"""Encode native GPU captures without inventing animation frames. Author: NingMeng123."""
from hashlib import sha256
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
FOLDER = ROOT / "build/verification/mob-render-audit"
FRAME_COUNT = 200
FRAME_DURATION_MS = 100
TICKS_PER_FRAME = 2


def main():
    # One palette keeps static surfaces stable while the real shader and wing clock advance.
    # Read the native frames twice rather than retaining 200 full-size RGB images in memory.
    samples = Image.new("RGB", (192 * 8, 108 * ((FRAME_COUNT + 7) // 8)))
    paths = [FOLDER / f"cosmic-animation/frame{index:03d}.png" for index in range(FRAME_COUNT)]
    for index, path in enumerate(paths):
        with Image.open(path) as source:
            assert source.size == (1536, 864), (path, source.size)
            samples.paste(source.convert("RGB").resize((192, 108), Image.Resampling.NEAREST),
                          (index % 8 * 192, index // 8 * 108))
    palette = samples.quantize(colors=256)
    encoded = []
    for path in paths:
        with Image.open(path) as source:
            encoded.append(source.convert("RGB").quantize(palette=palette, dither=Image.Dither.NONE))
    path = FOLDER / "cosmic-vibranium-flow.gif"
    encoded[0].save(path, save_all=True, append_images=encoded[1:], duration=FRAME_DURATION_MS,
                    loop=0, disposal=1, optimize=False)
    with Image.open(path) as preview:
        assert preview.n_frames == FRAME_COUNT
        duration = 0
        for index in range(preview.n_frames):
            preview.seek(index)
            duration += preview.info["duration"]
        assert duration == FRAME_COUNT * FRAME_DURATION_MS == 20000
    report = {"author": "NingMeng123", "path": str(path), "frames": FRAME_COUNT,
              "fps": 1000 / FRAME_DURATION_MS, "ticks_per_frame": TICKS_PER_FRAME,
              "capture_span_ticks": FRAME_COUNT * TICKS_PER_FRAME,
              "duration_ms": duration, "source": "native_gpu_frames", "size": encoded[0].size,
              "sha256": sha256(path.read_bytes()).hexdigest(), "bytes": path.stat().st_size}
    (ROOT / "build/verification/cosmic-preview.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
