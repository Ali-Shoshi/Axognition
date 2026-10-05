# Offline lecture and assistant speech

All three guided mathematics lectures and both AI chat layouts use bundled **Piper VITS neural voices** through **sherpa-onnx 1.13.8** on Android. Voice generation runs entirely on the device CPU. No speech account, API key, cloud request, or system voice installation is required.

| Use | English | Albanian |
| --- | --- | --- |
| Lecture | LJ Speech, female (`en_US-ljspeech-medium-int8`) | Arta (`sq_AL-arta-medium`) |
| AI chat, including streamed replies and saved answers | Norman, male (`en_US-norman-medium-int8`) | Edon, male (`sq_AL-edon-medium-int8`) |

The role selects the speaker explicitly. Device voice settings cannot change it. The English lecture speaker is the same one used before the chat integration.

## Build setup

Run once from the repository root on Windows:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/setup-offline-tts.ps1
```

Then build the app normally in Android Studio or with `gradlew.bat :app:assembleDebug`. The setup script downloads about 177 MB from sherpa-onnx releases and the Arta publisher and verifies every artifact against the pinned SHA-256 values in `scripts/offline-tts-artifacts.json`. Later builds can run offline. Model weights and the runtime live in the ignored `offline-tts/` directory; they are bundled into the APK rather than checked into Git. CI must run the same setup step before building.

The APK contains about 133 MiB of model and phonemizer assets, plus the native runtime. Android App Bundles deliver the native libraries appropriate for each device architecture. The general debug APK includes all four supported architectures and is therefore larger.

Arta is a standard Piper ONNX export. `scripts/convert-piper-model.ps1` adds the metadata required by sherpa-onnx and derives its token file from the publisher's configuration. It does not change the trained weights. This conversion uses PowerShell and .NET; no Python or additional packages are required.

## Playback

- `OfflineSpeechPlayer` handles all speech. Model preparation, inference and release run on a single background worker because the native phonemizer is shared. Each active player retains at most one language model; chat loads its model only when asked to speak.
- The phonemizer's shared data is copied once to private Android storage, excluded from backup. The actual neural weights are read from APK assets.
- Narration uses a slightly slower delivery and sentence pauses. Arithmetic notation is converted to spoken words without changing captions.
- Lecture replay reuses locally generated audio. The private lecture audio cache is limited to 64 MiB and is safe to delete. Chat audio uses temporary files that are removed after playback or cancellation.
- Playback speeds from 0.5× to 2× preserve voice pitch. Pausing, changing slides, muting, reloading, leaving, and changing language cancel stale playback and synthesis.
- Lectures wait for narration to finish before advancing an explanation automatically. Their audio watchdog uses the generated audio's actual duration.
- Chat queues streamed sentences in order and prepares the next sentence during playback. Interruption cancels current and queued speech. Chat's watchdog extends when the actual audio duration is known.
- Chat read-along uses approximate word ranges spread across the audio duration. Piper does not provide word timestamps. The ranges refer to the original displayed text, even when arithmetic is expanded for speech.
- Captions remain available if model loading or audio playback fails.

## Model sources and distribution notices

Model cards and license notices are bundled in `app/src/main/assets/tts-licenses/`.

- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8): Apache 2.0; [ONNX Runtime](https://github.com/microsoft/onnxruntime): MIT.
- [Piper voice repository](https://huggingface.co/rhasspy/piper-voices): published as MIT, with individual voice model cards documenting their training data.
- [English LJ Speech voice](https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_US/ljspeech/medium/MODEL_CARD): trained on the public domain LJ Speech dataset.
- [English Norman voice](https://huggingface.co/rhasspy/piper-voices/blob/c10ece1aade47bb51c153c893d14e5bf8e5b7117/en/en_US/norman/medium/MODEL_CARD): male speaker, trained from scratch on public domain LibriVox recordings.
- [Albanian Edon voice](https://huggingface.co/rhasspy/piper-voices/blob/main/sq/sq_AL/edon/medium/MODEL_CARD): Albanian training dataset is CC0; its card also records fine-tuning from the Lessac medium voice. Keep this provenance with redistributed models.
- [Albanian Arta voice](https://huggingface.co/edonseki/folsh.ai/tree/96a0ec37123333cb36fec91c5ce1b769081cfca6/arta): its model and configuration are **AGPL-3.0**, separately from Edon's CC0 data. The original license notice and full AGPL text are bundled. Preserve that license and provide the corresponding source required by it when distributing a release; the publisher sources and our metadata conversion script are linked here.
- [eSpeak NG](https://github.com/espeak-ng/espeak-ng): GPL 3.0 or later. It supplies text-to-phoneme processing; Piper's neural model generates the audible waveform. Its license and corresponding source obligations apply when distributing the bundled phonemizer/runtime. Preserve the notices and check these obligations before distributing a release.

No Meta MMS model or Azure/cloud speech service is used.
