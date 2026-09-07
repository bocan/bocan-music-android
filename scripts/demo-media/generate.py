#!/usr/bin/env python3
"""Generate the bundled demo library under app/src/main/assets/demo/.

Phase 14 (docs/design-spec/phase-14-demo-library.md). Run by hand on the Mac,
never at build time; commit the outputs together. Needs ffmpeg (with
libmp3lame), ffprobe, and ImageMagick 7 (`magick`) on PATH.

The output tree mirrors the phone's media root, so the seeder can copy it
across verbatim:

    demo/manifest.json                 a real Manifest document, serverId "demo"
    demo/library/Demo/<NN Title>.mp3   tracks, ID3v2.3 tagged, cover attached
    demo/artwork/<sha256>              covers, content addressed, no extension
    demo/lyrics/<trackId>.lrc          synced lyrics, one per track

Every id is DEMO_ID_BASE + n so phone-local rows keyed by track id can never
collide with a track from a real Mac.
"""

from __future__ import annotations

import hashlib
import json
import re
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / "app" / "src" / "main" / "assets" / "demo"
FONT = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"

DEMO_ID_BASE = 9_000_000_000
ARTIST = "Chris Funderburg"
ALBUM = "Bòcan Demo"
YEAR = 2026
GENRE = "Electronic"
PUBLISHER = "Cloud Cauldron"
COPYRIGHT = f"{YEAR} {ARTIST}. CC0 1.0."
COMMENT = (
    "Demo track bundled with Bòcan Music for Android. "
    "Replaced by your own library on the first sync."
)
DURATION_S = 60
BITRATE_KBPS = 128
SAMPLE_RATE = 44100

# Frequency f(t) = centre + depth * sin(2 pi t / period); the phase passed to
# sin() is its integral so the sweep has no discontinuities.
TONE_1 = (
    "0.25*sin(2*PI*550*t-2640*cos(2*PI*t/8))*(0.5+0.5*sin(2*PI*t/4))"
    "|0.25*sin(2*PI*550*t-2640*cos(2*PI*t/8))*(0.5-0.5*sin(2*PI*t/4))"
)
TONE_2 = (
    "st(0,2*PI*330*t-2640*cos(2*PI*t/16));"
    "(0.2*sin(ld(0))+0.1*sin(2*ld(0))+0.05*sin(3*ld(0)))*(0.8+0.2*sin(2*PI*4*t))"
)

LYRICS_1 = """[ti:Demo Audio 1]
[ar:Chris Funderburg]
[al:Bòcan Demo]
[00:00.50]This is the Bòcan demo album.
[00:05.00]The tone you hear rises and falls.
[00:10.00]These lyrics are synced to the music.
[00:15.00]Each line lights up on time.
[00:20.00]Drag the seek bar and watch it jump.
[00:25.00]Swipe left for the second demo track.
[00:30.00]Try the queue, the equalizer, and gestures.
[00:35.00]Rate a song, or mark it loved, on your Mac.
[00:40.00]Pair a Mac to sync your own library.
[00:45.00]This demo is replaced on your first sync.
[00:50.00]Nothing here leaves your phone.
[00:55.00]Thanks for listening.
"""

LYRICS_2 = """[ti:Demo Audio 2]
[ar:Chris Funderburg]
[al:Bòcan Demo]
[00:00.50]Second track, second cover, second set of lyrics.
[00:05.00]This tone is lower and moves more slowly.
[00:10.00]The tremolo you hear is four beats a second.
[00:15.00]Lyrics come from the Mac, one file per song.
[00:20.00]Timing offset lives under the lyrics toggle.
[00:25.00]Open song details for the format and bitrate.
[00:30.00]Add this song to the queue and shuffle it.
[00:35.00]The playlist tab has a manual and a smart list.
[00:40.00]Only the loved track is in the smart list.
[00:45.00]Your first sync removes both demo songs.
[00:50.00]The demo album is not scrobbled.
[00:55.00]That is the end of the demo.
"""


@dataclass(frozen=True)
class Track:
    number: int
    title: str
    tone: str
    tone_channels: str | None
    bpm: float
    rating: int
    loved: bool
    lyrics: str
    gradient: tuple[str, str]
    wave: str

    @property
    def id(self) -> int:
        return DEMO_ID_BASE + self.number

    @property
    def rel_path(self) -> str:
        return f"Demo/{self.number:02d} {self.title}.mp3"


TRACKS = [
    Track(
        number=1,
        title="Demo Audio 1",
        tone=TONE_1,
        tone_channels=None,
        bpm=120.0,
        rating=100,
        loved=True,
        lyrics=LYRICS_1,
        gradient=("#FF7A00", "#C2185B"),
        wave="polyline 60,780 200,700 340,760 480,560 620,600 760,380 940,300",
    ),
    Track(
        number=2,
        title="Demo Audio 2",
        tone=TONE_2,
        tone_channels="stereo",
        bpm=90.0,
        rating=60,
        loved=False,
        lyrics=LYRICS_2,
        gradient=("#1FA3A3", "#3F1FA3"),
        wave="polyline 60,300 200,380 340,320 480,520 620,480 760,700 940,780",
    ),
]

PLAYLISTS = [
    {
        "id": DEMO_ID_BASE + 1,
        "name": "Demo Playlist",
        "kind": "manual",
        "sortOrder": 1,
        "accentColor": "#FF7A00",
        "cover_of": 1,
        "trackIds": [t.id for t in TRACKS],
    },
    {
        "id": DEMO_ID_BASE + 2,
        "name": "Loved Demo Tracks",
        "kind": "smart",
        "sortOrder": 2,
        "accentColor": "#1FA3A3",
        "cover_of": 2,
        "trackIds": [t.id for t in TRACKS if t.loved],
    },
]


def run(*args: str, capture: bool = False) -> str:
    result = subprocess.run(args, check=True, text=True, capture_output=capture)
    return result.stdout if capture else ""


def sha256_of(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def render_cover(track: Track, target: Path) -> None:
    start, end = track.gradient
    run(
        "magick",
        "-size", "1000x1000",
        "-define", "gradient:angle=135",
        f"gradient:{start}-{end}",
        "-fill", "none", "-stroke", "white", "-strokewidth", "22",
        "-draw", track.wave,
        "-fill", "white", "-stroke", "none",
        "-font", FONT, "-pointsize", "520", "-gravity", "center",
        "-annotate", "+0-60", str(track.number),
        "-alpha", "off", "-depth", "8", "-strip",
        "-define", "png:compression-level=9",
        str(target),
    )


def encode_audio(track: Track, target: Path) -> None:
    source = f"aevalsrc=exprs='{track.tone}'"
    if track.tone_channels:
        source += f":c={track.tone_channels}"
    source += f":s={SAMPLE_RATE}:d={DURATION_S}"
    fade_out_start = DURATION_S - 0.5
    run(
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
        "-f", "lavfi", "-i", source,
        "-af", f"afade=t=in:d=0.5,afade=t=out:st={fade_out_start}:d=0.5",
        "-c:a", "libmp3lame", "-b:a", f"{BITRATE_KBPS}k",
        "-map_metadata", "-1",
        str(target),
    )


def measure_replaygain(audio: Path) -> tuple[float, float]:
    result = subprocess.run(
        ["ffmpeg", "-hide_banner", "-i", str(audio), "-af", "replaygain", "-f", "null", "-"],
        check=True, text=True, capture_output=True,
    )
    gain = re.search(r"track_gain = ([+-]?[0-9.]+) dB", result.stderr)
    peak = re.search(r"track_peak = ([0-9.]+)", result.stderr)
    if not gain or not peak:
        raise SystemExit(f"replaygain filter printed nothing useful for {audio}:\n{result.stderr}")
    return float(gain.group(1)), float(peak.group(1))


def probe_duration_ms(audio: Path) -> int:
    seconds = run(
        "ffprobe", "-hide_banner", "-loglevel", "error",
        "-show_entries", "format=duration", "-of", "csv=p=0", str(audio),
        capture=True,
    ).strip()
    return round(float(seconds) * 1000)


def tag_audio(track: Track, audio: Path, cover: Path, gain: dict[str, float], target: Path) -> None:
    def meta(key: str, value: str) -> list[str]:
        return ["-metadata", f"{key}={value}"]

    args = [
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
        "-i", str(audio), "-i", str(cover),
        "-map", "0:a", "-map", "1:v", "-c", "copy", "-disposition:v", "attached_pic",
        "-id3v2_version", "3", "-write_id3v1", "1",
    ]
    args += meta("title", track.title)
    args += meta("artist", ARTIST)
    args += meta("album_artist", ARTIST)
    args += meta("album", ALBUM)
    args += meta("track", f"{track.number}/{len(TRACKS)}")
    args += meta("disc", "1/1")
    args += meta("date", str(YEAR))
    args += meta("genre", GENRE)
    args += meta("composer", ARTIST)
    args += meta("TBPM", str(int(track.bpm)))
    args += meta("comment", COMMENT)
    args += meta("copyright", COPYRIGHT)
    args += meta("publisher", PUBLISHER)
    args += meta("TLAN", "eng")
    args += meta("lyrics", track.lyrics)
    args += meta("REPLAYGAIN_TRACK_GAIN", f"{gain['trackGain']:+.2f} dB")
    args += meta("REPLAYGAIN_TRACK_PEAK", f"{gain['trackPeak']:.6f}")
    args += meta("REPLAYGAIN_ALBUM_GAIN", f"{gain['albumGain']:+.2f} dB")
    args += meta("REPLAYGAIN_ALBUM_PEAK", f"{gain['albumPeak']:.6f}")
    args += ["-metadata:s:v", "title=Album cover", "-metadata:s:v", "comment=Cover (front)"]
    args.append(str(target))
    run(*args)


def check_no_dashes(text: str, where: str) -> None:
    if "–" in text or "—" in text:
        raise SystemExit(f"em or en dash in {where}; the standards forbid them")


def main() -> None:
    for tool in ("ffmpeg", "ffprobe", "magick"):
        if shutil.which(tool) is None:
            raise SystemExit(f"{tool} is not on PATH")
    for text, where in ((COMMENT, "comment"), (LYRICS_1, "lyrics 1"), (LYRICS_2, "lyrics 2")):
        check_no_dashes(text, where)

    if OUT.exists():
        shutil.rmtree(OUT)
    (OUT / "library" / "Demo").mkdir(parents=True)
    (OUT / "artwork").mkdir()
    (OUT / "lyrics").mkdir()

    with tempfile.TemporaryDirectory() as tmp_name:
        tmp = Path(tmp_name)
        covers: dict[int, tuple[Path, str]] = {}
        raw_audio: dict[int, Path] = {}
        measured: dict[int, tuple[float, float]] = {}

        for track in TRACKS:
            cover = tmp / f"cover-{track.number}.png"
            render_cover(track, cover)
            cover_hash = sha256_of(cover)
            shutil.copyfile(cover, OUT / "artwork" / cover_hash)
            covers[track.number] = (cover, cover_hash)

            raw = tmp / f"raw-{track.number}.mp3"
            encode_audio(track, raw)
            raw_audio[track.number] = raw
            measured[track.number] = measure_replaygain(raw)

        album_gain = round(sum(g for g, _ in measured.values()) / len(measured), 2)
        album_peak = max(p for _, p in measured.values())

        manifest_tracks = []
        for track in TRACKS:
            track_gain, track_peak = measured[track.number]
            gain = {
                "trackGain": track_gain,
                "trackPeak": track_peak,
                "albumGain": album_gain,
                "albumPeak": album_peak,
            }
            cover, cover_hash = covers[track.number]
            final = OUT / "library" / track.rel_path
            tag_audio(track, raw_audio[track.number], cover, gain, final)

            lyrics_path = OUT / "lyrics" / f"{track.id}.lrc"
            lyrics_path.write_text(track.lyrics, encoding="utf-8")

            manifest_tracks.append({
                "id": track.id,
                "relPath": track.rel_path,
                "size": final.stat().st_size,
                "sha256": sha256_of(final),
                "format": "mp3",
                "durationMs": probe_duration_ms(final),
                "title": track.title,
                "artist": ARTIST,
                "artistId": DEMO_ID_BASE + 1,
                "albumArtist": ARTIST,
                "albumArtistId": DEMO_ID_BASE + 1,
                "album": ALBUM,
                "albumId": DEMO_ID_BASE + 1,
                "trackNumber": track.number,
                "trackTotal": len(TRACKS),
                "discNumber": 1,
                "discTotal": 1,
                "year": YEAR,
                "genre": GENRE,
                "composer": ARTIST,
                "bpm": track.bpm,
                "rating": track.rating,
                "loved": track.loved,
                "sampleRate": SAMPLE_RATE,
                "bitrate": BITRATE_KBPS,
                "channelCount": 2,
                "isLossless": False,
                "replayGain": gain,
                "artworkHash": cover_hash,
                "lyricsHash": sha256_text(track.lyrics),
            })

    manifest_playlists = []
    for playlist in PLAYLISTS:
        manifest_playlists.append({
            "id": playlist["id"],
            "name": playlist["name"],
            "kind": playlist["kind"],
            "sortOrder": playlist["sortOrder"],
            "accentColor": playlist["accentColor"],
            "artworkHash": covers[playlist["cover_of"]][1],
            "trackIds": playlist["trackIds"],
        })

    manifest = {
        "protocolVersion": 1,
        "serverId": "demo",
        "serverName": "Demo library",
        "generation": 0,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "tracks": manifest_tracks,
        "playlists": manifest_playlists,
        "podcasts": [],
        "episodes": [],
    }
    (OUT / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    total = sum(p.stat().st_size for p in OUT.rglob("*") if p.is_file())
    print(f"wrote {OUT} ({total / 1024:.0f} KB)")
    for path in sorted(OUT.rglob("*")):
        if path.is_file():
            print(f"  {path.relative_to(OUT)}  {path.stat().st_size} bytes")


if __name__ == "__main__":
    sys.exit(main())
