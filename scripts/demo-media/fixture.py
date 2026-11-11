"""The end-to-end fixture library (phase 17, slice 1).

Called by `generate.py --fixture`. Writes `app/src/debug/assets/fixture/`, a
library big enough to exercise every surface of the app with no Mac: letter
buckets (a leading article, an accented initial, a digit, a symbol), four
genres, a two-disc album, a CUE clip pair, ReplayGain on every track, covers
on all but two tracks, synced and unsynced lyrics, both playlist kinds plus a
folder with a child, and two shows with one chaptered episode and one already
in progress.

The tree mirrors the demo's so the seeder reuses the demo's code path:

    fixture/manifest.json                     serverId "fixture"
    fixture/library/Fixture/<artist>/<album>/<D-NN title>.mp3
    fixture/Podcasts/Fixture/<show>/<NN title>.mp3
    fixture/artwork/<sha256>
    fixture/lyrics/<trackId>.lrc              synced lyrics
    fixture/lyrics/<trackId>.txt              unsynced lyrics
    fixture/lyrics/kinds.json                 trackId -> "synced" or "unsynced" (the wire kind)
    fixture/chapters/<episodeId>.json

Output is deterministic: a fixed generatedAt, bit-exact ffmpeg output, and no
timestamps in the PNGs, so a rerun with the same tools changes nothing. Asset
paths are ASCII slugs; the display names (accents, symbols) live in the tags
and the manifest only.
"""

from __future__ import annotations

import json
import re
import shutil
import tempfile
import unicodedata
from dataclasses import dataclass
from pathlib import Path

from generate import REPO, FONT, check_no_dashes, measure_replaygain, probe_duration_ms, run, sha256_of, sha256_text

OUT = REPO / "app" / "src" / "debug" / "assets" / "fixture"

# DemoLibrary.ID_BASE plus one million, so the fixture and the demo can never collide.
ID_BASE = 9_001_000_000
GENERATED_AT = "2026-11-11T00:00:00Z"
SERVER_ID = "fixture"
SERVER_NAME = "Fixture library"

TRACK_SECONDS = 4
CUE_SOURCE_SECONDS = 8
# Long enough that 40 percent (48 s) sits well inside the app's resume window: past the
# 5 s floor and before the 15 s end margin, with room to play on without "completing".
EPISODE_SECONDS = 120
BITRATE_KBPS = 64
EPISODE_BITRATE_KBPS = 32
SAMPLE_RATE = 44100
COVER_PX = 256
# Near-silence: a 220 Hz sine at about -50 dBFS, quiet but measurable by the replaygain filter.
NEAR_SILENCE = "0.003*sin(2*PI*220*t)"


@dataclass(frozen=True)
class Album:
    artist: str
    title: str
    genre: str
    year: int
    discs: tuple[int, ...]
    cover: tuple[str, str] | None
    cue: bool = False


# 14 artists, 18 albums, 48 tracks. The artist names cover the letter buckets the car
# and the library index group by: A twice, a leading article, an accented initial,
# a digit, and a symbol. "!Lumen Signal" has no cover, which leaves two tracks bare.
ALBUMS = [
    Album("Aurora Lane", "First Light", "Ambient", 2019, (3,), ("#F2A65A", "#8C3B1E")),
    Album("Aurora Lane", "Low Sun", "Ambient", 2021, (2,), ("#E9C46A", "#264653")),
    Album("Arctic Owls", "Snowline", "Folk", 2018, (3,), ("#A8DADC", "#1D3557")),
    Album("The Bellwethers", "Pasture Songs", "Folk", 2015, (3,), ("#90BE6D", "#277DA1")),
    Album("The Bellwethers", "Long Field", "Folk", 2017, (3, 3), ("#43AA8B", "#577590")),
    Album("Édith Marceau", "Rue des Lilas", "Jazz", 2012, (3,), ("#B5838D", "#6D6875")),
    Album("2 Bridges", "Crossings", "Electronic", 2020, (2,), ("#00B4D8", "#03045E")),
    Album("!Lumen", "Signal", "Electronic", 2022, (2,), None),
    Album("Cassia Moor", "Heather", "Folk", 2016, (3,), ("#C77DFF", "#3C096C")),
    Album("Dunmore Tide", "Breakwater", "Ambient", 2014, (2,), ("#48CAE4", "#023E8A")),
    Album("Fennel & Finch", "Kitchen Garden", "Folk", 2023, (3,), ("#B7E4C7", "#1B4332")),
    Album("Grey Heron Trio", "Standing Still", "Jazz", 2010, (3,), ("#ADB5BD", "#212529")),
    Album("Grey Heron Trio", "Wading", "Jazz", 2013, (2,), ("#CED4DA", "#495057")),
    Album("Kestrel", "Hover", "Electronic", 2024, (2,), ("#FFB703", "#FB8500")),
    Album("Mossgiel", "Ploughman", "Folk", 2011, (2,), ("#DDA15E", "#606C38")),
    Album("Saltmarsh Choir", "Evensong", "Ambient", 2009, (2,), ("#E0AFA0", "#463F3A")),
    Album("Zephyr Lines", "Westerly", "Electronic", 2025, (2,), ("#FFADAD", "#9D0208")),
    # A CUE image: one source file and two virtual tracks clipped from it.
    Album("Zephyr Lines", "Harbour Session", "Jazz", 2025, (3,), ("#8ECAE6", "#219EBC"), cue=True),
]

# Lyrics on six tracks, keyed by (album title, track number): four synced, two unsynced.
SYNCED_LYRICS = {("First Light", 1), ("Snowline", 1), ("Rue des Lilas", 1), ("Westerly", 1)}
UNSYNCED_LYRICS = {("Heather", 1), ("Hover", 1)}
LOVED = {("First Light", 1), ("Snowline", 2), ("Crossings", 1), ("Westerly", 2)}

PODCASTS = [
    {"title": "Tidewater Talks", "author": "Saltmarsh Choir", "cover": ("#2A9D8F", "#264653"), "speed": 1.0},
    {"title": "The Night Shift Radio", "author": "Kestrel", "cover": ("#6D597A", "#355070"), "speed": 1.25},
]
# (podcast index, title, publishedAt, has chapters, play state, position fraction)
EPISODES = [
    (0, "Fixture Episode One", "2026-10-01T08:00:00Z", True, "unplayed", 0.0),
    (0, "Fixture Episode Two", "2026-10-08T08:00:00Z", False, "inProgress", 0.4),
    (1, "Night Shift Pilot", "2026-09-20T22:00:00Z", False, "unplayed", 0.0),
]
EPISODE_CHAPTERS = [(0.0, "Opening"), (40.0, "The middle"), (80.0, "Closing")]


@dataclass(frozen=True)
class FixtureTrack:
    id: int
    album: Album
    album_index: int
    disc: int
    number: int
    title: str
    rel_path: str
    clip: tuple[int, int, int] | None  # (source track id, start ms, end ms)

    @property
    def key(self) -> tuple[str, int]:
        return (self.album.title, self.number)


def slug(text: str) -> str:
    """ASCII, lower case, hyphenated: safe as an APK asset path on every platform."""
    ascii_text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode("ascii")
    cleaned = re.sub(r"[^A-Za-z0-9]+", "-", ascii_text).strip("-").lower()
    return cleaned or "x"


def artist_ids() -> dict[str, int]:
    names = list(dict.fromkeys(album.artist for album in ALBUMS))
    return {name: ID_BASE + index for index, name in enumerate(names, start=1)}


def build_tracks() -> list[FixtureTrack]:
    tracks: list[FixtureTrack] = []
    next_id = ID_BASE + 1
    for album_index, album in enumerate(ALBUMS, start=1):
        folder = f"Fixture/{slug(album.artist)}/{slug(album.title)}"
        if album.cue:
            source_id = next_id
            source_path = f"{folder}/1-01 {slug(album.title)}-full.mp3"
            tracks.append(FixtureTrack(source_id, album, album_index, 1, 1, f"{album.title} (full)", source_path, None))
            half = CUE_SOURCE_SECONDS * 1000 // 2
            for number, (start, end) in enumerate(((0, half), (half, CUE_SOURCE_SECONDS * 1000)), start=2):
                title = f"{album.title} Part {number - 1}"
                tracks.append(FixtureTrack(source_id + number - 1, album, album_index, 1, number, title, source_path, (source_id, start, end)))
            next_id += 3
            continue
        number_on_album = 0
        for disc, count in enumerate(album.discs, start=1):
            for number in range(1, count + 1):
                number_on_album += 1
                title = f"{album.title} {number_on_album}"
                rel_path = f"{folder}/{disc}-{number:02d} {slug(title)}.mp3"
                tracks.append(FixtureTrack(next_id, album, album_index, disc, number_on_album, title, rel_path, None))
                next_id += 1
    return tracks


def synced_lyrics(title: str, artist: str) -> str:
    lines = ["First line of the fixture lyrics.", "Second line, one second later.", "Third line.", "Last line."]
    timed = "".join(f"[00:0{index}.50]{line}\n" for index, line in enumerate(lines))
    return f"[ti:{title}]\n[ar:{artist}]\n{timed}"


def unsynced_lyrics(title: str) -> str:
    return f"{title}\nUnsynced fixture lyrics.\nNo timestamps on these lines.\n"


def render_cover(colors: tuple[str, str], label: str, target: Path) -> None:
    start, end = colors
    run(
        "magick",
        "-size", f"{COVER_PX}x{COVER_PX}",
        "-define", "gradient:angle=135",
        f"gradient:{start}-{end}",
        "-fill", "white", "-font", FONT, "-pointsize", "120", "-gravity", "center",
        "-annotate", "+0+0", label,
        "-alpha", "off", "-depth", "8", "-strip",
        "-define", "png:exclude-chunks=date,time",
        "-define", "png:compression-level=9",
        str(target),
    )


def encode_near_silence(seconds: int, target: Path, bitrate_kbps: int = BITRATE_KBPS) -> None:
    run(
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
        "-f", "lavfi", "-i", f"aevalsrc=exprs='{NEAR_SILENCE}':s={SAMPLE_RATE}:d={seconds}",
        "-ac", "1", "-c:a", "libmp3lame", "-b:a", f"{bitrate_kbps}k",
        "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
        str(target),
    )


def tag(source: Path, cover: Path | None, tags: list[tuple[str, str]], target: Path) -> None:
    """Copy the audio stream untouched, add ID3v2.3 tags and an optional front cover."""
    args = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(source)]
    if cover is not None:
        args += ["-i", str(cover), "-map", "0:a", "-map", "1:v", "-disposition:v", "attached_pic"]
    else:
        args += ["-map", "0:a"]
    args += ["-c", "copy", "-map_metadata", "-1", "-id3v2_version", "3", "-fflags", "+bitexact"]
    for key, value in tags:
        args += ["-metadata", f"{key}={value}"]
    if cover is not None:
        args += ["-metadata:s:v", "title=Album cover", "-metadata:s:v", "comment=Cover (front)"]
    target.parent.mkdir(parents=True, exist_ok=True)
    args.append(str(target))
    run(*args)


def replay_gain_tags(gain: dict[str, float]) -> list[tuple[str, str]]:
    return [
        ("REPLAYGAIN_TRACK_GAIN", f"{gain['trackGain']:+.2f} dB"),
        ("REPLAYGAIN_TRACK_PEAK", f"{gain['trackPeak']:.6f}"),
        ("REPLAYGAIN_ALBUM_GAIN", f"{gain['albumGain']:+.2f} dB"),
        ("REPLAYGAIN_ALBUM_PEAK", f"{gain['albumPeak']:.6f}"),
    ]


def generate_fixture() -> None:
    tracks = build_tracks()
    if len(tracks) != 48:
        raise SystemExit(f"the fixture table yields {len(tracks)} tracks; the phase 17 contract says 48")
    for album in ALBUMS:
        check_no_dashes(album.artist + album.title, f"album {album.title}")

    if OUT.exists():
        shutil.rmtree(OUT)
    for folder in ("library", "Podcasts", "artwork", "lyrics", "chapters"):
        (OUT / folder).mkdir(parents=True)

    artists = artist_ids()
    with tempfile.TemporaryDirectory() as tmp_name:
        tmp = Path(tmp_name)
        short_raw, cue_raw, episode_raw = tmp / "short.mp3", tmp / "cue.mp3", tmp / "episode.mp3"
        encode_near_silence(TRACK_SECONDS, short_raw)
        encode_near_silence(CUE_SOURCE_SECONDS, cue_raw)
        encode_near_silence(EPISODE_SECONDS, episode_raw, EPISODE_BITRATE_KBPS)
        track_gain, track_peak = measure_replaygain(short_raw)
        gain = {"trackGain": track_gain, "trackPeak": track_peak, "albumGain": track_gain, "albumPeak": track_peak}

        covers: dict[str, tuple[Path, str]] = {}
        for album in ALBUMS:
            if album.cover is None:
                continue
            path = tmp / f"cover-{slug(album.title)}.png"
            render_cover(album.cover, album.title[0], path)
            digest = sha256_of(path)
            shutil.copyfile(path, OUT / "artwork" / digest)
            covers[album.title] = (path, digest)

        manifest_tracks = []
        lyric_kinds: dict[str, str] = {}
        written: dict[str, Path] = {}
        for track in tracks:
            album = track.album
            cover = covers.get(album.title)
            final = OUT / "library" / track.rel_path
            if track.rel_path not in written:
                source = cue_raw if album.cue else short_raw
                title = f"{album.title} (full)" if album.cue else track.title
                tags = [
                    ("title", title), ("artist", album.artist), ("album_artist", album.artist),
                    ("album", album.title), ("track", f"{track.number}/{sum(album.discs)}"),
                    ("disc", f"{track.disc}/{len(album.discs)}"), ("date", str(album.year)), ("genre", album.genre),
                ] + replay_gain_tags(gain)
                tag(source, cover[0] if cover else None, tags, final)
                written[track.rel_path] = final

            lyrics_hash = None
            if track.key in SYNCED_LYRICS:
                text = synced_lyrics(track.title, album.artist)
                (OUT / "lyrics" / f"{track.id}.lrc").write_text(text, encoding="utf-8")
                lyrics_hash = sha256_text(text)
                lyric_kinds[str(track.id)] = "synced"
            elif track.key in UNSYNCED_LYRICS:
                text = unsynced_lyrics(track.title)
                (OUT / "lyrics" / f"{track.id}.txt").write_text(text, encoding="utf-8")
                lyrics_hash = sha256_text(text)
                lyric_kinds[str(track.id)] = "unsynced"

            entry = {
                "id": track.id,
                "relPath": track.rel_path,
                "size": final.stat().st_size,
                "sha256": sha256_of(final),
                "format": "mp3",
                "durationMs": (track.clip[2] - track.clip[1]) if track.clip else probe_duration_ms(final),
                "title": track.title,
                "artist": album.artist,
                "artistId": artists[album.artist],
                "albumArtist": album.artist,
                "albumArtistId": artists[album.artist],
                "album": album.title,
                "albumId": ID_BASE + track.album_index,
                "trackNumber": track.number,
                "trackTotal": sum(album.discs),
                "discNumber": track.disc,
                "discTotal": len(album.discs),
                "year": album.year,
                "genre": album.genre,
                "rating": 80 if track.key in LOVED else 40,
                "loved": track.key in LOVED,
                "sampleRate": SAMPLE_RATE,
                "bitrate": BITRATE_KBPS,
                "channelCount": 1,
                "isLossless": False,
                "replayGain": gain,
                "artworkHash": cover[1] if cover else None,
                "lyricsHash": lyrics_hash,
            }
            if track.clip:
                entry["clip"] = {"sourceTrackId": track.clip[0], "startMs": track.clip[1], "endMs": track.clip[2]}
            manifest_tracks.append({key: value for key, value in entry.items() if value is not None})

        # The Mac sends the lyrics kind on the wire; the fixture records it here instead.
        (OUT / "lyrics" / "kinds.json").write_text(json.dumps(lyric_kinds, indent=2) + "\n", encoding="utf-8")

        manifest_podcasts, podcast_covers = [], {}
        for index, podcast in enumerate(PODCASTS):
            path = tmp / f"podcast-{index}.png"
            render_cover(podcast["cover"], podcast["title"][0], path)
            digest = sha256_of(path)
            shutil.copyfile(path, OUT / "artwork" / digest)
            podcast_covers[index] = (path, digest)
            manifest_podcasts.append({
                "id": ID_BASE + index + 1,
                "title": podcast["title"],
                "author": podcast["author"],
                "descriptionHtml": f"<p>{podcast['title']} is a fixture show for the end-to-end suite.</p>",
                "artworkHash": digest,
                "playbackSpeed": podcast["speed"],
            })

        manifest_episodes = []
        for number, (podcast_index, title, published, chapters, state, fraction) in enumerate(EPISODES, start=1):
            episode_id = f"fixture-episode-{number}"
            show = PODCASTS[podcast_index]
            rel_path = f"Podcasts/Fixture/{slug(show['title'])}/{number:02d} {slug(title)}.mp3"
            final = OUT / rel_path
            tags = [
                ("title", title), ("artist", show["author"]), ("album_artist", show["author"]),
                ("album", show["title"]), ("track", f"{number}/{len(EPISODES)}"), ("genre", "Podcast"),
            ]
            tag(episode_raw, podcast_covers[podcast_index][0], tags, final)
            duration = probe_duration_ms(final)
            if chapters:
                document = {"version": "1.2.0", "chapters": [{"startTime": s, "title": t} for s, t in EPISODE_CHAPTERS]}
                (OUT / "chapters" / f"{episode_id}.json").write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
            manifest_episodes.append({
                "id": episode_id,
                "podcastId": ID_BASE + podcast_index + 1,
                "guid": f"bocan-fixture:{episode_id}",
                "title": title,
                "publishedAt": published,
                "durationMs": duration,
                "descriptionHtml": f"<p>{title}, a fixture episode.</p>",
                "relPath": rel_path,
                "size": final.stat().st_size,
                "sha256": sha256_of(final),
                "hasChapters": chapters,
                "playPositionMs": round(duration * fraction),
                "playState": state,
            })

    by_key = {track.key: track.id for track in tracks}
    first_cover = covers["First Light"][1]
    manifest_playlists = [
        {"id": ID_BASE + 1, "name": "Fixture Mix", "kind": "manual", "sortOrder": 1, "accentColor": "#F2A65A",
         "artworkHash": first_cover, "trackIds": [t.id for t in tracks if t.number == 1 and t.clip is None][:8]},
        {"id": ID_BASE + 2, "name": "Loved Fixture Tracks", "kind": "smart", "sortOrder": 2, "accentColor": "#E63946",
         "trackIds": [by_key[key] for key in sorted(LOVED)]},
        {"id": ID_BASE + 3, "name": "Fixture Folder", "kind": "folder", "sortOrder": 3, "trackIds": []},
        {"id": ID_BASE + 4, "name": "Folder Child", "kind": "manual", "parentId": ID_BASE + 3, "sortOrder": 1,
         "trackIds": [t.id for t in tracks if t.album.genre == "Jazz" and t.clip is None][:3]},
    ]

    manifest = {
        "protocolVersion": 1,
        "serverId": SERVER_ID,
        "serverName": SERVER_NAME,
        "generation": 1,
        "generatedAt": GENERATED_AT,
        "tracks": manifest_tracks,
        "playlists": manifest_playlists,
        "podcasts": manifest_podcasts,
        "episodes": manifest_episodes,
    }
    (OUT / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    files = [p for p in OUT.rglob("*") if p.is_file()]
    total = sum(p.stat().st_size for p in files)
    print(f"wrote {OUT}: {len(tracks)} tracks, {len(EPISODES)} episodes, {len(files)} files, {total / 1024:.0f} KB")
