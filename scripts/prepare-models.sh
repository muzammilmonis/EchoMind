#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$ROOT/.model-tmp"
ASSETS="$ROOT/app/src/main/assets"
rm -rf "$TMP" "$ASSETS/model-en" "$ASSETS/model-spk"
mkdir -p "$TMP" "$ASSETS/model-en" "$ASSETS/model-spk"

curl -fL "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip" -o "$TMP/asr.zip"
curl -fL "https://alphacephei.com/vosk/models/vosk-model-spk-0.4.zip" -o "$TMP/spk.zip"
unzip -q "$TMP/asr.zip" -d "$TMP/asr"
unzip -q "$TMP/spk.zip" -d "$TMP/spk"
cp -a "$TMP/asr/vosk-model-small-en-us-0.15/." "$ASSETS/model-en/"
cp -a "$TMP/spk/vosk-model-spk-0.4/." "$ASSETS/model-spk/"
rm -rf "$TMP"
echo "Offline Vosk ASR + speaker models copied into Android assets."
