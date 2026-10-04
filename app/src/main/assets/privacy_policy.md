# EdgeLLM Studio — Privacy Policy

**Effective:** October 2026. This policy describes what the app does with
your data. Short version: inference runs on your device; anything that
leaves the device only does so through features you explicitly enable.

## 1. On-device inference (default)

GGUF (llama.cpp), ONNX Runtime, LiteRT/MediaPipe, and the built-in
knowledge base execute entirely on your device. Prompts, model weights,
and generated text processed by these engines are never transmitted to
any remote server by the app. No account, no analytics SDK, no ads, no
third-party trackers are bundled for this path.

## 2. Optional cloud features (off by default)

- **Cloud Assist (Gemini).** If you supply a Gemini API key and enable
  Cloud Assist, your prompts and the model responses are sent to Google's
  generative-AI endpoints under Google's terms and your API quota. The
  key is stored only in the app's private files on this device.
- **Cloud Hub providers.** If you configure a third-party provider key,
  prompts are sent to that provider under its terms.
- Disable these features at any time in Settings to return to fully
  local operation.

## 3. Local API server (opt-in)

The built-in Ollama-compatible server (port 11434) is **off / loopback-only
by default**. If you enable LAN binding, devices on your local network can
reach it; all API routes except the health probe require the bearer token
shown in the app's API server settings. The token is generated randomly
on this device and never embedded in served pages.

## 4. On-device storage

Models you download, chat history, settings, and the API bearer token are
stored in the app's private files on this device. Android backup may copy
private files to your linked Google account per your system backup
settings; API keys entered in-app are held in private preferences.

## 5. Permissions

- Camera / microphone: used only for on-device multimodal and voice
  features you invoke; audio and images processed locally are not
  uploaded unless an enabled cloud feature requires it.
- Notifications / foreground service: keeps local inference and the
  optional API server alive while in use.

## 6. Changes & contact

Material changes will update this file and the app version notes. For
questions, open an issue at
https://github.com/Jesse-perpC/EdgeLLM-Studio.

Note for Play Data Safety: declare on-device inference plus the optional
identifiers above (network transmission only via user-enabled cloud
features; no data shared with the developer).
