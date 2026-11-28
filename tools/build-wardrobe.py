"""Build against the launcher's installed NeoForge 21.1.267 mapped libraries (JDK 21)."""
import os,subprocess,tempfile,sys,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
libs=Path(sys.argv[1])
neo=libs/"net/neoforged/neoforge/21.1.267"
cp=[neo/"neoforge-21.1.267-client.jar",neo/"neoforge-21.1.267-universal.jar",*libs.glob("net/minecraft/client/**/*-srg.jar"),*libs.rglob("*.jar")]
with tempfile.TemporaryDirectory() as tmp:
    out=Path(tmp)
    args=out/"args"
    args.write_text('-proc:none --release 21 -encoding UTF-8 -cp "'+os.pathsep.join(p.as_posix() for p in cp)+'" -d "'+out.as_posix()+'" '+' '.join('"'+p.as_posix()+'"' for p in (root/"wardrobe").rglob("*.java")),encoding="utf-8")
    subprocess.run(["javac","@"+str(args)],check=True)
    with zipfile.ZipFile(root/"launcher/woidwatch-wardrobe.jar","w",zipfile.ZIP_DEFLATED) as jar:
        for file in [*out.rglob("*.class")]:jar.write(file,file.relative_to(out))
        for file in (root/"wardrobe").rglob("*"):
            if file.suffix in (".toml",".json"):jar.write(file,file.relative_to(root/"wardrobe"))
