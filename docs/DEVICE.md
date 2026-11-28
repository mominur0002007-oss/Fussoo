# Device notes — TECNO Spark 10C

| | |
| --- | --- |
| SoC | Unisoc T606, Mali-G57 MP1 |
| RAM | 4 GB |
| Display | 720 × 1612, ~269 ppi |
| Android | 13 (HiOS 12.6) |
| Sensors | accelerometer, gyroscope, rotation vector present |

## What that means for this app

**Resolution.** A native 720p panel split into two eyes is 360 px wide per eye. Rendering
both eyes at 68% of that is already past the resolution a plastic lens can deliver. The
renderer defaults to 68% and lets the user pick 55% (battery) or 85% (cinematic).

**Decoding.** A 720p H.264 stream is decodeable by this class of chip without breaking a
sweat; 1080p VP9 is where it starts dropping frames. `Yt.choose()` prefers a muxed MP4
stream because it needs no audio/video merging and starts fastest, then falls back to
video-only MP4 plus the best m4a audio track. AV1 and VP9 are deprioritised.

**RAM.** 4 GB shared with HiOS. The app requests `largeHeap`, keeps thumbnails in a
48-entry LRU, and packs all HUD art into one 2048² texture (16 MB as ARGB). No video is
ever copied to app storage.

**Heat.** Rendering plus decoding for an hour will throttle the GPU. The renderer never
adds smoothing latency when frames are slow (above ~32 ms), which keeps motion honest
instead of turning into syrup.

## Box notes (Shinecon-style)

* Lens separation is usually 62–64 mm; the default is 63 mm.
* Barrel correction around 0.12 is a reasonable starting point; adjust with the stereo
  test pattern until grid lines look straight.
* Hold the phone centred in the box's clamp, or the two eyes will see different crops.

## First-run checklist

1. Open the app, search something, tap a result.
2. Sit the phone in the box, then look straight ahead and press **recentre** (`O`).
3. If the image is doubled, adjust **Lens separation** first, then **Field of view**.
4. If the edges look bent, raise **Barrel correction**.
5. If it stutters, set **Performance → Battery** and **Max quality → 720p**.
