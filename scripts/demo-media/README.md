# Demo media generator

`generate.py` builds the demo library that ships inside the APK under
`app/src/main/assets/demo/` (see `docs/design-spec/phase-14-demo-library.md`).
It is run by hand on a Mac and never at build time. Commit its outputs together:
the manifest carries the sha256 of every file, and `DemoAssetsIntegrityTests`
fails the build if one changes without the others.

## Needs

- ffmpeg with libmp3lame, and ffprobe (`brew install ffmpeg`)
- ImageMagick 7 (`brew install imagemagick`)
- Python 3.10 or newer, standard library only
- `/System/Library/Fonts/Supplemental/Arial Bold.ttf` (present on every Mac)

## Run

```
python3 scripts/demo-media/generate.py
```

It wipes `app/src/main/assets/demo/` and writes:

| Path | What |
|------|------|
| `manifest.json` | A real manifest document, `serverId` "demo", ids at or above 9000000001 |
| `library/Demo/NN Title.mp3` | 60 s tone tracks, 128 kbps stereo, ID3v2.3 with cover, lyrics, ReplayGain |
| `artwork/<sha256>` | The two covers, content addressed like `ArtworkStore` |
| `lyrics/<trackId>.lrc` | Synced lyrics, one per track |

## What lives where

- Track titles, tones, covers, ratings, BPM, and the LRC text are constants at
  the top of the script. Edit them there, rerun, commit.
- ReplayGain track values come from ffmpeg's `replaygain` filter on the encoded
  audio; album gain is the mean of the track gains and album peak the maximum.
- The USLT tag holds the same LRC text as the `.lrc` file. The phone reads
  lyrics from the manifest hash and the seeded cache, not from the tag.
- Verify tags with `ffprobe -show_entries format_tags "<file>"`.

No em dashes or en dashes anywhere in the text: the script refuses to run if
it finds one in the comment or lyrics.
