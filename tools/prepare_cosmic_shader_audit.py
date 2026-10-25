"""Prepare isolated BSL, Photon and shader-off clients. Author: NingMeng123."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--instance", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "build/verification/cosmic-universal-20261025")
    args = parser.parse_args()
    destination = args.output.resolve()
    destination.relative_to((ROOT / "build/verification").resolve())
    if destination.exists():
        raise SystemExit(f"Refusing to overwrite an existing audit directory: {destination}")
    source_mods = args.instance / "mods"
    mods = []
    for pattern in ("iris-neoforge-*.jar", "*sodium-neoforge-*.jar"):
        matches = list(source_mods.glob(pattern))
        if len(matches) != 1:
            raise SystemExit(f"Expected exactly one {pattern}, found {matches}")
        mods.append(matches[0])
    packs = {"off": None, "bsl": "BSL_v10.1.1.zip", "photon": "photon_v1.3b.zip"}
    for filename in packs.values():
        if filename and not (args.instance / "shaderpacks" / filename).is_file():
            raise SystemExit(f"Missing shader pack: {filename}")
    manifest = {"author": "NingMeng123", "source_instance": str(args.instance), "copies": [], "cases": {}}
    for name, filename in packs.items():
        client = destination / name / "client"
        for folder in ("mods", "shaderpacks", "config"):
            (client / folder).mkdir(parents=True, exist_ok=True)
        copies = [(mod, client / "mods" / mod.name) for mod in mods]
        if filename:
            copies.append((args.instance / "shaderpacks" / filename, client / "shaderpacks" / filename))
        for source, target in copies:
            shutil.copyfile(source, target)
            manifest["copies"].append({"source": str(source), "target": str(target),
                                       "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
        (client / "config/iris.properties").write_text(
            f"enableShaders={str(bool(filename)).lower()}\nallowUnknownShaders=false\n"
            f"shaderPack={filename or ''}\nmaxShadowRenderDistance=32\n", encoding="ascii")
        (client / "config/fml.toml").write_text("earlyWindowControl = false\n", encoding="utf-8")
        (client / "options.txt").write_text(
            "overrideWidth:1600\noverrideHeight:900\nfullscreen:false\npauseOnLostFocus:false\n"
            "enableVsync:false\nmaxFps:120\n", encoding="utf-8")
        manifest["cases"][name] = str(client)
        print(client)
    (destination / "inputs.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
