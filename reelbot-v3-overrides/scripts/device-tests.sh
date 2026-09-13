#!/usr/bin/env bash
set -euo pipefail
mkdir -p test-results
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
adb shell am force-stop com.reelbot.mobile
adb shell am start -W -n com.reelbot.mobile/.MainActivity > test-results/cold-start.txt
adb shell am instrument -w -e class com.reelbot.mobile.StartupSmokeTest com.reelbot.mobile.test/androidx.test.runner.AndroidJUnitRunner | tee test-results/startup-navigation.txt
rg 'OK \(1 test\)' test-results/startup-navigation.txt
adb push test-input/ggml-base.bin /data/local/tmp/ggml-base.bin
adb push test-input/real-speech-fixture.mp4 /data/local/tmp/real-speech-fixture.mp4
adb shell run-as com.reelbot.mobile mkdir -p files/models
adb shell run-as com.reelbot.mobile cp /data/local/tmp/ggml-base.bin files/models/ggml-base.bin
adb shell run-as com.reelbot.mobile cp /data/local/tmp/real-speech-fixture.mp4 files/real-speech-fixture.mp4
adb shell am instrument -w -e class com.reelbot.mobile.LocalPipelineTest com.reelbot.mobile.test/androidx.test.runner.AndroidJUnitRunner | tee test-results/local-pipeline.txt
rg 'OK \(2 tests\)' test-results/local-pipeline.txt
adb shell am force-stop com.reelbot.mobile
adb shell am start -W -n com.reelbot.mobile/.MainActivity > test-results/restart.txt
adb shell run-as com.reelbot.mobile ls -l files/reels/verified-reel.mp4 > test-results/persisted-output.txt
adb exec-out run-as com.reelbot.mobile cat files/reels/verified-reel.mp4 > test-results/verified-reel.mp4
adb exec-out run-as com.reelbot.mobile cat files/verified-transcript.txt > test-results/transcript.txt
adb logcat -d > test-results/logcat.txt
ffprobe -v error -show_streams test-results/verified-reel.mp4 > test-results/output-streams.txt
ffmpeg -v error -i test-results/verified-reel.mp4 -f null - 2> test-results/decode-errors.txt
test ! -s test-results/decode-errors.txt
