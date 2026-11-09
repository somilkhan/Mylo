#!/usr/bin/env bash
set +e
./gradlew --no-daemon --stacktrace :app:connectedDevDebugAndroidTest
test_status=$?
if [ "$test_status" -ne 0 ]; then
  echo '--- Emulator crash diagnostics (AndroidRuntime / instrumentation) ---'
  adb logcat -d -v threadtime -t 3000 | grep -E -A25 -B5 'FATAL EXCEPTION|AndroidRuntime|Process:|INSTRUMENTATION|am_crash|crash_dump' || true
  echo '--- App process exit history ---'
  adb shell dumpsys activity exit-info app.mylo.music.dev || true
fi
exit "$test_status"
