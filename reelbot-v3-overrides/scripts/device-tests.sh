#!/usr/bin/env bash
set -euo pipefail
mkdir -p test-results
reelbot_log_pid=""
finish_tests() {
  reelbot_test_exit=$?
  trap - EXIT
  if [ -n "$reelbot_log_pid" ]; then kill "$reelbot_log_pid" 2>/dev/null || true; fi
  timeout -k 5s 15s adb logcat -d > test-results/logcat.txt || true
  timeout -k 5s 15s adb exec-out screencap -p > test-results/final-screen.png || true
  rg 'ReelBot|AndroidRuntime|TestRunner|whisper_|FATAL|Fatal' test-results/logcat.txt | tail -150 || true
  exit "$reelbot_test_exit"
}
trap finish_tests EXIT
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell mkdir -p /sdcard/Download
adb push test-input/long-video.mp4 /sdcard/Download/real-speech-fixture.mp4
adb logcat -c
adb logcat -v threadtime > test-results/logcat-live.txt 2>&1 &
reelbot_log_pid=$!
adb shell am force-stop com.reelbot.mobile
adb shell am start -W -n com.reelbot.mobile/.MainActivity > test-results/cold-start.txt
adb exec-out screencap -p > test-results/home-screen.png
timeout -k 10s 180s adb shell am instrument -w -e class com.reelbot.mobile.StartupSmokeTest com.reelbot.mobile.test/androidx.test.runner.AndroidJUnitRunner | tee test-results/startup-navigation.txt
rg -F 'OK (1 test)' test-results/startup-navigation.txt
adb exec-out run-as com.reelbot.mobile cat files/selected-video.png > test-results/selected-video.png
adb push test-input/ggml-base.bin /data/local/tmp/ggml-base.bin
adb push test-input/real-speech-fixture.mp4 /data/local/tmp/real-speech-fixture.mp4
adb shell run-as com.reelbot.mobile mkdir -p files/models
adb shell run-as com.reelbot.mobile cp /data/local/tmp/ggml-base.bin files/models/ggml-base.bin
adb shell run-as com.reelbot.mobile cp /data/local/tmp/real-speech-fixture.mp4 files/real-speech-fixture.mp4
timeout -k 10s 480s adb shell am instrument -w -e class com.reelbot.mobile.LocalPipelineTest com.reelbot.mobile.test/androidx.test.runner.AndroidJUnitRunner | tee test-results/local-pipeline.txt
rg -F 'OK (3 tests)' test-results/local-pipeline.txt
adb shell am force-stop com.reelbot.mobile
adb shell am start -W -n com.reelbot.mobile/.MainActivity > test-results/restart.txt
timeout -k 10s 120s adb shell am instrument -w -e class com.reelbot.mobile.PersistenceTest com.reelbot.mobile.test/androidx.test.runner.AndroidJUnitRunner | tee test-results/persistence.txt
rg -F 'OK (1 test)' test-results/persistence.txt
adb shell run-as com.reelbot.mobile ls -l files/reels/verified-reel.mp4 > test-results/persisted-output.txt
adb exec-out run-as com.reelbot.mobile cat files/reels/verified-reel.mp4 > test-results/verified-reel.mp4
adb exec-out run-as com.reelbot.mobile cat files/verified-transcript.txt > test-results/transcript.txt
adb logcat -d > test-results/logcat.txt
ffprobe -v error -show_streams test-results/verified-reel.mp4 > test-results/output-streams.txt
ffmpeg -v error -i test-results/verified-reel.mp4 -f null - 2> test-results/decode-errors.txt
test ! -s test-results/decode-errors.txt

timeout -k 10s 180s adb shell am instrument -w -e class com.reelbot.mobile.ModelDownloadTest com.reelbot.mobile.test/androidx.test.runner.AndroidJUnitRunner | tee test-results/android-model-download.txt
rg -F 'OK (1 test)' test-results/android-model-download.txt

timeout -k 10s 90s adb shell am instrument -w -e class com.reelbot.mobile.PlaybackTest com.reelbot.mobile.test/androidx.test.runner.AndroidJUnitRunner | tee test-results/android-playback.txt
rg -F 'OK (1 test)' test-results/android-playback.txt
adb push test-input/face-fixture.mp4 /data/local/tmp/face-fixture.mp4
adb shell run-as com.reelbot.mobile cp /data/local/tmp/face-fixture.mp4 files/face-fixture.mp4
timeout -k 10s 120s adb shell am instrument -w -e class com.reelbot.mobile.FaceCropTest com.reelbot.mobile.test/androidx.test.runner.AndroidJUnitRunner | tee test-results/face-crop.txt
rg -F 'OK (1 test)' test-results/face-crop.txt
adb exec-out run-as com.reelbot.mobile cat files/reels/face-verified.mp4 > test-results/face-verified.mp4
