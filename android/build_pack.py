"""Build the Android Frontier profile from a matching desktop export."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXPERIMENTAL_RENDERER_EXCLUSIONS = (
    "mods/distanthorizons-", "mods/iris-", "mods/customskinloader_", "mods/essential_",
)
MOBILE_OPTIONS = {
    "renderDistance": "5", "simulationDistance": "4", "maxFps": "30",
    "entityDistanceScaling": "0.75", "graphicsMode": "0", "particles": "1",
    "biomeBlendRadius": "0", "showSubtitles": "true", "lang": "ru_ru",
}

def build(source, output):
    index = json.loads((ROOT / "pack/index.json").read_text(encoding="utf8"))
    wardrobe = ROOT / "launcher/woidwatch-wardrobe.jar"
    if output.resolve() == source.resolve():
        raise ValueError("Source and output must differ")
    with zipfile.ZipFile(source) as desktop:
        expected = index["files"]
        if len(desktop.namelist()) != len(expected) or set(desktop.namelist()) != set(expected):
            raise ValueError("Desktop export does not match the tracked pack index")
        output.parent.mkdir(parents=True, exist_ok=True)
        staged = output.with_suffix(output.suffix + ".part")
        try:
            with zipfile.ZipFile(staged, "w") as mobile:
                for path in sorted(expected):
                    data = desktop.read(path)
                    if hashlib.sha256(data).hexdigest() != expected[path]["sha256"]:
                        raise ValueError(f"Desktop export integrity mismatch: {path}")
                    if path == "options.txt" or path.startswith("shaderpacks/") or path.lower().startswith(EXPERIMENTAL_RENDERER_EXCLUSIONS):
                        continue
                    mobile.writestr(path, data, compress_type=desktop.getinfo(path).compress_type)
                mobile.writestr("options.txt", "".join(f"{key}:{value}\n" for key,value in MOBILE_OPTIONS.items()))
                mobile.writestr("mods/woidwatch-wardrobe.jar",wardrobe.read_bytes())
                mobile.writestr("frontier-mobile.json",json.dumps({
                    "format": 1, "id": "frontier-of-god", "version": index["packVersion"],
                    "minecraft": index["minecraft"], "neoforge": index["neoforge"],
                    "graphicsExclusions": [p for p in sorted(expected) if p.lower().startswith(EXPERIMENTAL_RENDERER_EXCLUSIONS)]
                }))
            staged.replace(output)
        finally:
            staged.unlink(missing_ok=True)

if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT/"dist/client.zip")
    parser.add_argument("--output", type=Path, default=ROOT/"dist/Frontier-Android.zip")
    args=parser.parse_args()
    build(args.source,args.output)
