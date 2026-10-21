#!/usr/bin/env python3
"""Build with Java 17 and Python 3. No Gradle or Android Studio required."""
import hashlib
import os
from pathlib import Path
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent
TOOLS = ROOT / "tools"
BUILD = ROOT / "build"
TOOLS.mkdir(exist_ok=True)
BUILD.mkdir(exist_ok=True)
DEPS = {
    "android.jar": "https://repo.maven.apache.org/maven2/com/google/android/android/4.1.1.4/android-4.1.1.4.jar",
    "ecj.jar": "https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.37.0/ecj-3.37.0.jar",
    "xposed.jar": "https://maven.aliyun.com/repository/public/de/robv/android/xposed/api/82/api-82.jar",
    "xmlpull.jar": "https://repo.maven.apache.org/maven2/xmlpull/xmlpull/1.1.3.1/xmlpull-1.1.3.1.jar",
    "sdk.zip": "https://dl.google.com/android/repository/build-tools_r35.0.1_linux.zip",
}

def run(*args):
    subprocess.run([str(a) for a in args], cwd=ROOT, check=True)

for name, url in DEPS.items():
    path = TOOLS / name
    if not path.exists():
        print("Downloading", name, flush=True)
        with urllib.request.urlopen(url, timeout=60) as response:
            path.write_bytes(response.read())

sdk = TOOLS / "sdk" / "android-15"
if not sdk.exists():
    with zipfile.ZipFile(TOOLS / "sdk.zip") as archive:
        archive.extractall(TOOLS / "sdk")
for name in ("aapt2", "apksigner", "zipalign"):
    (sdk / name).chmod(0o755)

classes = BUILD / "classes"
classes.mkdir(exist_ok=True)
sources = sorted((ROOT / "src").rglob("*.java"))
run("java", "-jar", TOOLS / "ecj.jar", "-8", "-proc:none", "-cp",
    os.pathsep.join(str(TOOLS / n) for n in ("android.jar", "xposed.jar", "xmlpull.jar")), "-d", classes, *sources)
jar = BUILD / "classes.jar"
with zipfile.ZipFile(jar, "w") as archive:
    for path in sorted(classes.rglob("*.class")):
        archive.write(path, path.relative_to(classes))
run("java", "-cp", sdk / "lib" / "d8.jar", "com.android.tools.r8.D8", "--min-api", "26",
    "--lib", TOOLS / "android.jar", "--classpath", TOOLS / "xposed.jar", "--output", BUILD, jar)
run(sdk / "aapt2", "compile", "--dir", ROOT / "res", "-o", BUILD / "resources.zip")
run(sdk / "aapt2", "link", "-I", TOOLS / "android.jar", "--manifest", ROOT / "AndroidManifest.xml",
    "-A", ROOT / "assets", "-o", BUILD / "unsigned.apk", BUILD / "resources.zip")
with zipfile.ZipFile(BUILD / "unsigned.apk", "a") as archive:
    archive.write(BUILD / "classes.dex", "classes.dex")
run(sdk / "zipalign", "-f", "-p", "4", BUILD / "unsigned.apk", BUILD / "aligned.apk")
key = ROOT / "development.keystore"
if not key.exists():
    run("keytool", "-genkeypair", "-keystore", key, "-storepass", "android", "-keypass", "android",
        "-alias", "ctsshare", "-dname", "CN=CTS Share Development", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000")
apk = ROOT / "CTS-Share-Restore-1.0.apk"
run(sdk / "apksigner", "sign", "--ks", key, "--ks-pass", "pass:android", "--key-pass", "pass:android",
    "--ks-key-alias", "ctsshare", "--out", apk, BUILD / "aligned.apk")
run(sdk / "apksigner", "verify", "--verbose", apk)
run(sdk / "zipalign", "-c", "4", apk)
print(apk)
print("SHA-256", hashlib.sha256(apk.read_bytes()).hexdigest())
