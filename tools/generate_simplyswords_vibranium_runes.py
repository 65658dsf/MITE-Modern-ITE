"""Add small, shape-changing native runes to the 15 MITE Simply Swords vibranium sprites.

Author: NingMeng123.
Source silhouettes/material redraws: the adjacent MITE Simply Swords compatibility
generator; Simply Swords artwork is credited there to Sweenus, Hootea and
Konsyliarz42. Rune frames are sampled from the manifest-locked MITE-ITE 2.0.25
vibranium sword by XiaoYu233/Xy_Lose, rather than inventing a new alphabet.

Only this addon's assets/mite texture overrides are written. The neighboring
repository and upstream archives are read-only. No model or UV changes are needed.
"""

from __future__ import annotations

import argparse
from io import BytesIO
import hashlib
import json
import math
from pathlib import Path
import zipfile

from PIL import Image, ImageDraw, PngImagePlugin


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BASE = ROOT.parent / "miteto1.21.1neoforge"
FAMILIES = (
    "longsword", "twinblade", "rapier", "katana", "sai", "spear", "glaive", "warglaive",
    "cutlass", "claymore", "greataxe", "greathammer", "chakram", "scythe", "halberd",
)
SCALE = 4
GLYPH_SIZE = 4
FRAME_COUNT = 8
FRAME_TICKS = 3
# This six-pixel cell changes glyph in every frame of the original 128-square sword.
NATIVE_CELL = (89, 33, 95, 39)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def glyph_frames(resources: Path) -> list[Image.Image]:
    manifest = json.loads((resources / "mite/vibranium-native-assets.json").read_text(encoding="utf-8"))
    relative = "assets/mite/textures/item/vibranium_sword.png"
    entry = next(asset for asset in manifest["assets"]
                 if asset["destination"].endswith("/" + relative))
    data = (resources / relative).read_bytes()
    if sha256(data) != entry["sha256"]:
        raise ValueError("Native sword differs from its source manifest; refusing to sample unverified rune frames")
    sword = Image.open(BytesIO(data)).convert("RGBA")
    if sword.size != (128, 128 * FRAME_COUNT):
        raise ValueError(f"Unexpected native sword animation: {sword.size}")
    glyphs = []
    for frame in range(FRAME_COUNT):
        x0, y0, x1, y1 = NATIVE_CELL
        cell = sword.crop((x0, y0 + frame * 128, x1, y1 + frame * 128))
        # Keep the original neutral-cyan rune pixels; the source's blue blade is not a rune.
        pixels = [(r, g, b, 255 if alpha and min(r, g, b) >= 160 else 0)
                  for r, g, b, alpha in cell.get_flattened_data()]
        cell.putdata(pixels)
        glyphs.append(cell.resize((GLYPH_SIZE, GLYPH_SIZE), Image.Resampling.NEAREST))
    if len({glyph.getchannel("A").tobytes() for glyph in glyphs}) != FRAME_COUNT:
        raise ValueError("Every source frame must produce a different rune shape")
    return glyphs


def metal_pixels(iron: Image.Image, gold: Image.Image) -> set[tuple[int, int]]:
    if iron.size != gold.size or iron.getchannel("A").tobytes() != gold.getchannel("A").tobytes():
        raise ValueError("Simply Swords material references have different silhouettes")
    return {(x, y) for y in range(iron.height) for x in range(iron.width)
            if iron.getpixel((x, y))[3] == 255
            and iron.getpixel((x, y))[:3] != gold.getpixel((x, y))[:3]}


def rune_positions(source: Image.Image, metal: set[tuple[int, int]]) -> list[tuple[int, int]]:
    """Choose sparse metal-interior cells, leaving black outlines, cyan edges and grips intact."""
    remaining = set(metal)
    components = []
    while remaining:
        pending = [remaining.pop()]
        component = set(pending)
        while pending:
            x, y = pending.pop()
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    point = (x + dx, y + dy)
                    if point in remaining:
                        remaining.remove(point)
                        component.add(point)
                        pending.append(point)
        components.append(component)
    # Isolated pommels and end caps are metal too, but the animated lettering belongs on blades.
    largest = max(map(len, components))
    blade_metal = set().union(*(component for component in components
                               if len(component) >= max(12, largest * .2)))
    candidates = []
    for x, y in sorted(blade_metal):
        r, g, b, _ = source.getpixel((x, y))
        neighbors = sum((x + dx, y + dy) in metal for dy in (-1, 0, 1)
                        for dx in (-1, 0, 1) if dx or dy)
        # Match the existing compatibility palette's middle metal tones, never its brightest edge.
        broad_face_interior = len(metal) < 350 or sum(
            (x + dx, y + dy) in metal for dx in range(-2, 3) for dy in range(-2, 3)) >= 23
        if 52 <= (r + g + b) / 3 <= 165 and g < 180 and neighbors >= 2 and broad_face_interior:
            candidates.append((x, y))
    if not candidates:
        raise ValueError("No safe metal interior is available for a rune")
    # Furthest-point placement spreads the small fixed cells over the metal rather than flashing
    # its whole surface. Each cell stays inside one opaque source texel after nearest-neighbor scale.
    count = min(7, max(2, round(math.sqrt(len(metal)) / 2.8)))
    first = max(candidates, key=lambda point: (point[0] - point[1], -point[1]))
    chosen = [first]
    remaining = [point for point in candidates if point != first]
    while remaining and len(chosen) < count:
        point = max(remaining, key=lambda candidate: min(
            (candidate[0] - old[0]) ** 2 + (candidate[1] - old[1]) ** 2 for old in chosen))
        separation = min((point[0] - old[0]) ** 2 + (point[1] - old[1]) ** 2 for old in chosen)
        if separation < 9:
            break
        chosen.append(point)
        remaining.remove(point)
    return sorted(chosen, key=lambda point: point[0] - point[1])


def animate(source: Image.Image, metal: set[tuple[int, int]], glyphs: list[Image.Image]):
    positions = rune_positions(source, metal)
    base = source.resize((source.width * SCALE, source.height * SCALE), Image.Resampling.NEAREST)
    frames = []
    allowed = {(x * SCALE + dx, y * SCALE + dy) for x, y in positions
               for dy in range(GLYPH_SIZE) for dx in range(GLYPH_SIZE)}
    for index in range(FRAME_COUNT):
        frame = base.copy()
        for rune, (x, y) in enumerate(positions):
            glyph = glyphs[(index + rune * 3) % FRAME_COUNT]
            # Paste only glyph pixels, preserving both the original alpha and the metal around them.
            frame.paste(glyph, (x * SCALE, y * SCALE), glyph.getchannel("A"))
        if frame.getchannel("A").tobytes() != base.getchannel("A").tobytes():
            raise AssertionError("A rune changed the weapon silhouette")
        changed = {(x, y) for y in range(base.height) for x in range(base.width)
                   if frame.getpixel((x, y)) != base.getpixel((x, y))}
        if not changed or not changed <= allowed:
            raise AssertionError("Only the fixed small rune cells may change")
        frames.append(frame)
    if len({frame.tobytes() for frame in frames}) != FRAME_COUNT:
        raise AssertionError("Rune animation must change its glyph shape in all eight frames")
    strip = Image.new("RGBA", (base.width, base.height * FRAME_COUNT))
    for index, frame in enumerate(frames):
        strip.paste(frame, (0, index * base.height))
    return strip, frames, positions


def png_bytes(image: Image.Image, source_hash: str) -> bytes:
    metadata = PngImagePlugin.PngInfo()
    metadata.add_text("Author", "NingMeng123")
    metadata.add_text("Source sprite SHA-256", source_hash)
    metadata.add_text("Rune source", "MITE-ITE 2.0.25 vibranium_sword.png; original eight glyph frames")
    metadata.add_text("Art credits", "Simply Swords: Sweenus, Hootea, Konsyliarz42; MITE-ITE: XiaoYu233, Xy_Lose")
    output = BytesIO()
    image.save(output, format="PNG", pnginfo=metadata, optimize=False)
    return output.getvalue()


def preview(path: Path, rows: list[tuple[str, list[Image.Image]]]) -> None:
    cell = 144
    header = 28
    image = Image.new("RGB", (110 + cell * 4, header + cell * len(rows)), (26, 31, 39))
    draw = ImageDraw.Draw(image)
    for column, frame in enumerate((0, 2, 4, 6)):
        draw.text((116 + column * cell, 8), f"frame {frame}", fill=(223, 235, 245))
    for row, (family, frames) in enumerate(rows):
        draw.text((5, header + row * cell + 60), family, fill=(223, 235, 245))
        for column, frame in enumerate((0, 2, 4, 6)):
            original = frames[frame]
            factor = min(128 / original.width, 128 / original.height)
            shown = original.resize((round(original.width * factor), round(original.height * factor)),
                                    Image.Resampling.NEAREST)
            x = 110 + column * cell + (cell - shown.width) // 2
            y = header + row * cell + (cell - shown.height) // 2
            image.paste(shown, (x, y), shown.getchannel("A"))
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-repo", type=Path, default=DEFAULT_BASE)
    parser.add_argument("--ss-jar", type=Path)
    parser.add_argument("--check", action="store_true", help="Verify generated addon resources without writing")
    parser.add_argument("--preview", type=Path, help="Optional review-only contact sheet")
    args = parser.parse_args()
    resources = args.base_repo.resolve() / "src/main/resources"
    manifest = json.loads((resources / "mite/simplyswords-compat-assets.json").read_text(encoding="utf-8"))
    archive = args.ss_jar or args.base_repo / manifest["upstream"]["source_archive"]
    if sha256(archive.read_bytes()) != manifest["upstream"]["sha256"]:
        raise ValueError("Simply Swords archive differs from the base compatibility source manifest")
    glyphs = glyph_frames(resources)
    generated = {}
    rows = []
    facts = []
    with zipfile.ZipFile(archive) as upstream:
        for family in FAMILIES:
            name = f"simplyswords_vibranium_{family}"
            relative = f"assets/mite/textures/item/{name}.png"
            source_bytes = (resources / relative).read_bytes()
            source = Image.open(BytesIO(source_bytes)).convert("RGBA")
            iron = Image.open(BytesIO(upstream.read(f"assets/simplyswords/textures/item/iron_{family}.png"))).convert("RGBA")
            gold = Image.open(BytesIO(upstream.read(f"assets/simplyswords/textures/item/gold_{family}.png"))).convert("RGBA")
            if source.size != iron.size or source.getchannel("A").tobytes() != iron.getchannel("A").tobytes():
                raise ValueError(f"Unexpected source compatibility sprite: {name}")
            metal = metal_pixels(iron, gold)
            strip, frames, positions = animate(source, metal, glyphs)
            generated[relative] = png_bytes(strip, sha256(source_bytes))
            generated[relative + ".mcmeta"] = (json.dumps({"animation": {
                "frametime": FRAME_TICKS, "width": frames[0].width, "height": frames[0].height,
                "interpolate": False,
            }}, indent=2) + "\n").encode("utf-8")
            rows.append((family, frames))
            facts.append(f"{family}: {source.width} -> {frames[0].width}px, {len(positions)} small runes, 8 distinct frames")
    destination = ROOT / "src/main/resources"
    differences = [relative for relative, data in generated.items()
                   if not (destination / relative).exists() or (destination / relative).read_bytes() != data]
    if args.check and differences:
        raise SystemExit("Generated rune resources differ:\n" + "\n".join(differences))
    if not args.check:
        for relative, data in generated.items():
            target = destination / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        if args.preview:
            preview(args.preview, rows)
    print("\n".join(facts))
    print(f"{'Verified' if args.check else 'Generated'} {len(generated)} addon overrides; base files unchanged; "
          f"{FRAME_COUNT * FRAME_TICKS} tick / 1.2 second native glyph loop")


if __name__ == "__main__":
    main()
