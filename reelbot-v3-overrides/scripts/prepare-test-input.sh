#!/usr/bin/env bash
set -euo pipefail
mkdir -p test-input
curl --fail --location --retry 3 https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin -o test-input/ggml-base.bin
echo '60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe  test-input/ggml-base.bin' | sha256sum --check
ffmpeg -y -f lavfi -i testsrc2=size=640x360:rate=24 -stream_loop 2 -i app/src/main/cpp/whisper.cpp/samples/jfk.wav -t 30 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest test-input/real-speech-fixture.mp4
