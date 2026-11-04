#!/usr/bin/env bash
# 에뮬레이터: 앱 설치 → 계정 연결 → 모든 화면 열기 → 꺼졌는지 확인 (API 키는 기록에 남기지 않음)
set +e
PKG=net.skyblockhelper
ACT=$PKG/sbhelper.android.MainActivity
adb install -r app.apk
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am start -S -W -n $ACT --es sbhelper.selftest_user "$SB_USER" --es sbhelper.selftest_key "$SB_KEY" > /dev/null
for i in $(seq 1 30); do
  sleep 5
  if adb logcat -d -s SbhSelfTest:I | grep -q "visited=all"; then break; fi
  if adb logcat -d -b crash | grep -q "FATAL"; then break; fi
done
# 꺼진 기록 (스택만), 진행 기록
{
  echo "진행: $(adb logcat -d -s SbhSelfTest:I | grep -o 'linked=.*\|screen=.*\|visited=.*' | tr '\n' ' ')"
  adb logcat -d -b crash | grep -E "FATAL|Exception|Error|Caused by|	at sbhelper|	at androidx.compose" | head -60
} | sed "s/$SB_KEY/***/g" > crash.txt
cat crash.txt
