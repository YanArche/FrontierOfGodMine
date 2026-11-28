"""Prepare the pinned Amethyst source checkout for WoidWatch Android."""
import argparse
import shutil
import subprocess
import zipfile
from pathlib import Path

HERE=Path(__file__).resolve().parent
UPSTREAM="https://github.com/AngelAuraMC/Amethyst-Android.git"
REV="9423d6054115767b56de7ed0b83ed18fc1eba3c2"

def edit(path, marker, old, new):
    text=path.read_text(encoding="utf-8")
    if marker in text:
        return
    if text.count(old)!=1:
        raise RuntimeError(f"Pinned upstream layout changed: {path}")
    path.write_text(text.replace(old,new),encoding="utf-8")

def prepare(target, pack):
    if not target.exists():
        subprocess.run(["git","clone",UPSTREAM,str(target)],check=True)
        subprocess.run(["git","-C",str(target),"checkout","--detach",REV],check=True)
    rev=subprocess.check_output(["git","-C",str(target),"rev-parse","HEAD"],text=True).strip()
    if rev!=REV:
        raise RuntimeError("Use a checkout at the pinned Amethyst revision")
    app=target/"app_pojavlauncher"
    source=app/"src/main"
    package=source/"java/net/kdt/pojavlaunch"
    shutil.copyfile(HERE/"overlay/WoidWatchSetup.java",package/"WoidWatchSetup.java")
    assets=source/"assets/woidwatch"
    assets.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(HERE.parent/"launcher/art/frontier.jpg",assets/"frontier.jpg")
    if pack is not None:
        with zipfile.ZipFile(pack) as archive:
            if "frontier-mobile.json" not in archive.namelist():
                raise ValueError("Expected the package produced by build_pack.py")
        shutil.copyfile(pack,assets/"Frontier-Android.zip")
    layout=source/"res/layout/activity_pojav_launcher.xml"
    edit(layout,'@+id/woidwatch_library',
        '    <!-- Holding most of the dynamic content -->',
        """    <Button
        android:id="@+id/woidwatch_library"
        android:layout_width="0dp"
        android:layout_height="48dp"
        android:text="WoidWatch · Frontier of God"
        android:textAllCaps="false"
        android:textColor="#92F0D2"
        android:backgroundTint="#243D43"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toBottomOf="@id/account_spinner" />

    <!-- Holding most of the dynamic content -->""")
    edit(layout,'app:layout_constraintTop_toBottomOf="@id/woidwatch_library"',
        'app:layout_constraintTop_toBottomOf="@+id/account_spinner" />',
        'app:layout_constraintTop_toBottomOf="@id/woidwatch_library" />')
    gradle=app/"build.gradle"
    gradle.write_text(gradle.read_text(encoding="utf-8")
        .replace("org.angelauramc.amethyst","win.noboredgames.woidwatch")
        .replace("minSdkVersion 21","minSdkVersion 26")
        .replace('resValue "string", "app_name", "Amethyst (Debug)"','resValue "string", "app_name", "WoidWatch Launcher"')
        .replace('resValue "string", "app_short_name", "Amethyst (Debug)"','resValue "string", "app_short_name", "WoidWatch"'),encoding="utf-8")
    icons=source/"res/drawable-nodpi"
    icons.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(HERE.parent/"launcher/art/eye.png",icons/"woidwatch_icon.png")
    manifest=source/"AndroidManifest.xml"
    edit(manifest,'android:icon="@drawable/woidwatch_icon"',
        'android:icon="@mipmap/ic_launcher"','android:icon="@drawable/woidwatch_icon"')
    edit(manifest,'android:roundIcon="@drawable/woidwatch_icon"',
        'android:roundIcon="@mipmap/ic_launcher_round"','android:roundIcon="@drawable/woidwatch_icon"')
    edit(app/"build.gradle","abiFilters 'arm64-v8a'",
        '        minSdkVersion 26',"        minSdkVersion 26\n        ndk { abiFilters 'arm64-v8a' }")
    edit(app/"build.gradle","WoidWatch 4G only",
        "            useLegacyPackaging = true",
        "            useLegacyPackaging = true\n"
        "            // WoidWatch 4G only: AARs can still supply non-ARM64 libraries.\n"
        "            excludes += ['lib/armeabi/**', 'lib/armeabi-v7a/**', 'lib/x86/**', 'lib/x86_64/**', 'lib/mips/**', 'lib/mips64/**']")
    edit(source/"AndroidManifest.xml",'android:label="WoidWatch Launcher"',
        'android:label="@string/app_name"','android:label="WoidWatch Launcher"')
    edit(package/"LauncherActivity.java","WoidWatchSetup.mount(this)",
        "        setContentView(R.layout.activity_pojav_launcher);",
        "        setContentView(R.layout.activity_pojav_launcher);\n        WoidWatchSetup.mount(this);")
    edit(package/"utils/JREUtils.java",'WoidWatch: start the heap small',
        '        userArgs.add("-Xms" + LauncherPreferences.PREF_RAM_ALLOCATION + "M");',
        '        // WoidWatch: start the heap small on phones.\n        userArgs.add("-Xms512M");')
    bypass=target/"androidnsbypass/build.gradle.kts"
    if bypass.is_file():
        edit(bypass,'ndkVersion = "27.3.13750724"',
            "    compileSdk {\n        version = release(36) {\n            minorApiLevel = 1\n        }\n    }",
            '    compileSdk = 37\n    ndkVersion = "27.3.13750724"')
    print(f"WoidWatch overlay applied to {rev}")

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkout",type=Path)
    parser.add_argument("--pack",type=Path)
    args=parser.parse_args()
    prepare(args.checkout.resolve(),args.pack)
