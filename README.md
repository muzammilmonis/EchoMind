# Echo Mind

Echo Mind is a local-first Android voice-memory prototype. It records a conversation as 16 kHz mono WAV, performs offline speech recognition, groups utterances into Speaker A/B/C… using Vosk speaker embeddings, asks the user to rename detected speakers, then writes a timestamped transcript and a deterministic local summary into that recording's folder.

## V1 flow

1. Tap **Start recording**.
2. Audio is saved locally as `audio.wav`.
3. After **Stop & process**, the embedded Vosk models run fully on-device.
4. Speaker embeddings are clustered by cosine similarity into A/B/C… labels.
5. Echo Mind asks who Speaker A, B, C, etc. were.
6. The session folder receives:
   - `audio.wav`
   - `meeting.json`
   - `transcript.txt`
   - `summary.txt`
7. Sessions appear in the local history screen.

## Privacy / networking

The Android manifest contains no `INTERNET` permission. Recording, recognition, speaker clustering, transcript generation, and summary generation are local. Vosk is an embedded open-source runtime/model, not a cloud API.

## Offline models

A self-contained APK needs these model contents bundled under assets:

- `app/src/main/assets/model-en/` ← contents of `vosk-model-small-en-us-0.15`
- `app/src/main/assets/model-spk/` ← contents of `vosk-model-spk-0.4`

On a development machine with internet access, run:

```bash
./scripts/prepare-models.sh
```

The model download is a **build-time** step only. The resulting APK can work without internet.

## Build

Requires Android SDK 35+, Java 17, and Gradle 8.11.1 (or Android Studio configured for the project).

```bash
./scripts/prepare-models.sh
gradle :app:assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

A GitHub Actions workflow is included at `.github/workflows/build-apk.yml`.

## Current V1 limits

- ASR model is English-oriented. Urdu/Hinglish recognition quality will depend heavily on pronunciation and model choice.
- Speaker separation is utterance-level clustering using Vosk speaker vectors, not a heavyweight cloud diarization service; overlapping speech can reduce accuracy.
- The summary is rule-based (participants, speaking time, keywords), intentionally avoiding any cloud LLM.
- Session files live in the app's local external-files area. Public export/share can be added in a later version.
