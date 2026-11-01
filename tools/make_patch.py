"""
데스크톱 앱 안 업데이트 파일을 만든다 (CI 의 Windows 빌드에서 실행).

  update-manifest.json                 버전, 자바 버전, 네이티브 파일 지문, jar 목록(이름·sha256·크기·묶음)
  SkyblockHelper-<v>-patch-app.zip     이 앱의 jar (매 버전 바뀜, 작음)
  SkyblockHelper-<v>-patch-libs.zip    라이브러리 jar (라이브러리를 올릴 때만 바뀜)

앱은 manifest 와 자기 파일을 비교해서 바뀐 jar 가 든 묶음만 받는다 (DesktopUpdater.kt 와 같은 규칙).

사용: python tools/make_patch.py <배포 폴더 SkyblockHelper> <버전> <출력 폴더>
"""
import hashlib
import json
import os
import re
import sys
import zipfile


def sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def is_app_jar(path: str) -> bool:
    """이 앱의 코드(sbhelper 패키지)가 든 jar 인지"""
    with zipfile.ZipFile(path) as z:
        return any(n.startswith("sbhelper/") for n in z.namelist())


def main() -> None:
    dist, version, out = sys.argv[1], sys.argv[2], sys.argv[3]
    app = os.path.join(dist, "app")
    os.makedirs(out, exist_ok=True)

    jars = sorted(n for n in os.listdir(app) if n.endswith(".jar"))
    # 클래스패스 순서는 실행 파일 설정(.cfg)과 같게
    order = []
    for cfg in (n for n in os.listdir(app) if n.endswith(".cfg")):
        with open(os.path.join(app, cfg), encoding="utf-8", errors="replace") as f:
            for line in f:
                m = re.match(r"app\.classpath=.*[\\/]([^\\/]+\.jar)\s*$", line.strip())
                if m and m.group(1) in jars and m.group(1) not in order:
                    order.append(m.group(1))
    order += [j for j in jars if j not in order]

    # 네이티브 파일 지문: jar·cfg 가 아닌 파일 (DesktopUpdater.nativeHash 와 같은 방식)
    files = []
    for root, _, names in os.walk(app):
        for n in names:
            if n.endswith(".jar") or n.endswith(".cfg") or n.startswith("."):  # .jpackage.xml 은 버전이 적혀 있어 매번 바뀜
                continue
            p = os.path.join(root, n)
            files.append((os.path.relpath(p, app).replace(os.sep, "/"), p))
    native = hashlib.sha256()
    for rel, p in sorted(files):
        native.update(f"{rel}:{sha256(p)}\n".encode())

    java_major = None
    release_file = os.path.join(dist, "runtime", "release")
    if os.path.exists(release_file):
        with open(release_file, encoding="utf-8") as f:
            m = re.search(r'JAVA_VERSION="(\d+)', f.read())
            if m:
                java_major = int(m.group(1))

    entries = []
    packs = {"app": [], "libs": []}
    for j in order:
        p = os.path.join(app, j)
        pack = "app" if is_app_jar(p) else "libs"
        packs[pack].append(p)
        entries.append({"name": j, "sha256": sha256(p), "size": os.path.getsize(p), "pack": pack})

    for pack, paths in packs.items():
        with zipfile.ZipFile(os.path.join(out, f"SkyblockHelper-{version}-patch-{pack}.zip"), "w", zipfile.ZIP_DEFLATED) as z:
            for p in paths:
                z.write(p, os.path.basename(p))

    # 네이티브 파일 묶음: 그래픽 라이브러리(skiko)가 바뀐 버전도 앱 안에서 업데이트할 수 있게
    native_list = []
    with zipfile.ZipFile(os.path.join(out, f"SkyblockHelper-{version}-patch-native.zip"), "w", zipfile.ZIP_DEFLATED) as z:
        for rel, p in sorted(files):
            z.write(p, rel)
            native_list.append({"path": rel, "sha256": sha256(p), "size": os.path.getsize(p)})

    manifest = {"version": version, "javaMajor": java_major, "nativeHash": native.hexdigest(), "jars": entries,
                "native": native_list}
    with open(os.path.join(out, "update-manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=1)
    print(f"jars: {len(entries)} (app {len(packs['app'])}, libs {len(packs['libs'])}), java {java_major}, native files {len(files)}")
    for rel, _ in sorted(files):
        print("  native:", rel)


if __name__ == "__main__":
    main()
