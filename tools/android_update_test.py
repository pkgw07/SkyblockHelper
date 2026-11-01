"""
휴대폰 앱 안 업데이트를 에뮬레이터에서 끝까지 확인한다 (CI 전용).

  1단계 (처음 업데이트): 현재 버전(a.apk) 설치 → 앱 안 업데이트 시작 → '이 출처 허용' 켜고 돌아오기 →
         Android 업데이트 확인 창에서 '업데이트' → 99.0.0 이 됐는지
  2단계 (그다음 업데이트): 99.0.0(b) → 99.0.1(c) 이 확인 창 없이 설치되는지

화면마다 스크린샷과 기기 로그(SbhUpdate)를 out 폴더에 남긴다.
사용: python3 tools/android_update_test.py <apk 폴더(a,b,c.apk)> <out 폴더>
"""
import hashlib
import http.server
import os
import re
import socketserver
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET

PKG = "net.skyblockhelper"
ACT = PKG + "/sbhelper.android.MainActivity"
PORT = 8765
CONFIRM = ["Update", "UPDATE", "Install", "INSTALL", "업데이트", "설치"]
ALLOW_PAGE = ["Allow from this source", "이 출처 허용", "Allow from this source "]


def adb(*args, timeout=60):
    return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)


class Run:
    def __init__(self, out):
        self.out = out
        self.n = 0
        self.seen = set()
        self.notes = []

    def note(self, s):
        print(s, flush=True)
        self.notes.append(s)

    def shot(self, name):
        self.n += 1
        data = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=30).stdout
        path = os.path.join(self.out, f"{self.n:02d}-{name}.png")
        open(path, "wb").write(data)

    def ui(self):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        return adb("shell", "cat", "/sdcard/ui.xml").stdout

    @staticmethod
    def nodes(xml):
        try:
            root = ET.fromstring(xml)
        except ET.ParseError:
            return []
        out = []
        for n in root.iter("node"):
            out.append(n.attrib)
        return out

    @staticmethod
    def center(bounds):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
        return (x1 + x2) // 2, (y1 + y2) // 2

    def tap(self, node, why):
        x, y = self.center(node["bounds"])
        self.note(f"  탭: {why} ({node.get('text') or node.get('class')})")
        adb("shell", "input", "tap", str(x), str(y))

    def version(self):
        m = re.search(r"versionName=(\S+)", adb("shell", "dumpsys", "package", PKG).stdout)
        return m.group(1) if m else None

    def installer(self):
        m = re.search(r"installerPackageName=(\S+)", adb("shell", "dumpsys", "package", PKG).stdout)
        return m.group(1) if m else None

    def drive(self, expect, label, max_wait=180):
        """화면을 보며 필요한 버튼을 누르고, 버전이 expect 가 되면 True"""
        start = time.time()
        dialogs = 0
        while time.time() - start < max_wait:
            time.sleep(2)
            v = self.version()
            if v == expect:
                self.note(f"  ✔ 버전 {v} 설치됨 ({int(time.time() - start)}초, 누른 확인 창 {dialogs}개)")
                # 설치 뒤 앱이 다시 열렸는지, 완료 알림이 떴는지
                time.sleep(6)
                self.shot(f"{label}-done")
                top = adb("shell", "dumpsys", "activity", "activities").stdout
                resumed = re.search(r"(topResumedActivity|mResumedActivity|ResumedActivity)[^\n]*", top)
                reopened = bool(resumed and PKG in resumed.group(0))
                notif = PKG in adb("shell", "dumpsys", "notification", "--noredact").stdout
                self.note(f"  {'✔' if reopened else '✘'} 설치 뒤 앱이 다시 열림  ·  {'✔' if notif else '✘'} 완료 알림")
                return True, dialogs
            xml = self.ui()
            nodes = self.nodes(xml)
            texts = [n.get("text", "") for n in nodes]
            # 시계·날짜처럼 계속 바뀌는 글자는 빼고 비교 (같은 화면을 여러 번 찍지 않게)
            stable = [t for t in texts if not re.search(r"\d{1,2}:\d{2}|YEAR|\d+(st|nd|rd|th)$", t)]
            key = hashlib.md5("|".join(stable).encode()).hexdigest()
            if key not in self.seen:
                self.seen.add(key)
                self.shot(label)
                self.note(f"  화면: {' / '.join(t for t in texts if t)[:200]}")
            # 1) '알 수 없는 앱 설치' 설정 화면: 스위치를 켜고 뒤로
            if any(any(a.strip() in t for a in ALLOW_PAGE) for t in texts):
                sw = [n for n in nodes if n.get("checkable") == "true"]
                if sw and sw[0].get("checked") == "false":
                    self.tap(sw[0], "이 출처 허용 스위치")
                    time.sleep(1.5)
                    self.shot(f"{label}-allowed")
                    dialogs += 1
                self.note("  뒤로 (앱으로 돌아가기)")
                adb("shell", "input", "keyevent", "BACK")
                continue
            # 2) Android 설치 확인 창: '업데이트' / '설치'
            btn = [n for n in nodes if n.get("clickable") == "true" and n.get("text", "").strip() in CONFIRM]
            if btn and any("com.android.packageinstaller" in n.get("package", "") or "permissioncontroller" in n.get("package", "")
                           or "packageinstaller" in n.get("package", "") for n in nodes):
                self.tap(btn[0], "설치 확인 창의 버튼")
                dialogs += 1
                continue
            # 3) 그 밖의 '허용' (알림 권한 등)
            allow = [n for n in nodes if n.get("clickable") == "true" and n.get("text", "").strip() in ("Allow", "허용")]
            if allow:
                self.tap(allow[0], "허용")
        self.shot(f"{label}-timeout")
        self.note(f"  ✘ {max_wait}초 안에 {expect} 로 바뀌지 않음 (지금 {self.version()})")
        return False, dialogs


def serve(folder):
    handler = lambda *a, **k: http.server.SimpleHTTPRequestHandler(*a, directory=folder, **k)
    httpd = socketserver.TCPServer(("127.0.0.1", PORT), handler)
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    return httpd


def main():
    apks, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    r = Run(out)
    serve(apks)
    adb("reverse", f"tcp:{PORT}", f"tcp:{PORT}")
    adb("logcat", "-c")
    ok_all = True

    r.note("1단계: 처음 업데이트 (a → 99.0.0)")
    res = adb("install", "-r", os.path.join(apks, "a.apk"), timeout=180)
    r.note(f"  설치: {res.stdout.strip() or res.stderr.strip()}  버전 {r.version()}  설치 주체 {r.installer()}")
    adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
    adb("shell", "am", "start", "-n", ACT)
    time.sleep(6)
    r.shot("1-app")
    # -S: 앱을 새로 띄우면서 신호를 넘긴다 (이미 떠 있으면 Android 가 신호를 전달하지 않음)
    adb("shell", "am", "start", "-S", "-W", "-n", ACT, "--es", "sbhelper.selftest_update", f"http://127.0.0.1:{PORT}/b.apk",
        "--es", "sbhelper.selftest_version", "99.0.0")
    ok1, d1 = r.drive("99.0.0", "1")
    ok_all &= ok1
    r.note(f"  설치 주체 (업데이트 후): {r.installer()}")

    if ok1:
        r.note("2단계: 그다음 업데이트 (99.0.0 → 99.0.1) — 확인 창 없이 되어야 함")
        time.sleep(4)
        adb("shell", "am", "start", "-n", ACT)
        time.sleep(4)
        adb("shell", "am", "start", "-S", "-W", "-n", ACT, "--es", "sbhelper.selftest_update", f"http://127.0.0.1:{PORT}/c.apk",
            "--es", "sbhelper.selftest_version", "99.0.1")
        ok2, d2 = r.drive("99.0.1", "2")
        ok_all &= ok2
        if ok2 and d2 > 0:
            r.note(f"  참고: 두 번째 업데이트에서도 확인 창 {d2}개를 눌렀어요")

    log = adb("logcat", "-d", "-s", "SbhUpdate:I", "SbhSelfTest:I", "PackageInstaller:*", "InstallStart:*", "AndroidRuntime:E", "ActivityTaskManager:W").stdout
    open(os.path.join(out, "logcat.txt"), "w").write(log)
    open(os.path.join(out, "result.txt"), "w").write(("성공" if ok_all else "실패") + "\n" + "\n".join(r.notes))
    print("\n".join(l for l in log.splitlines() if "SbhUpdate" in l or "SbhSelfTest" in l or "FATAL" in l or "Background activity" in l or "BAL" in l)[-4000:])
    sys.exit(0 if ok_all else 1)


if __name__ == "__main__":
    main()
