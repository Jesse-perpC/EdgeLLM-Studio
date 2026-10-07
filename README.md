# EdgeLLM Studio (Web & React)

> **The #1 Ranked All-in-One Local On-Device AI & Neural Runtime**
> *Universal GGUF + MediaPipe + ONNX + MNN • Stable Diffusion Studio • Hugging Face Hub Explorer • RAG Chunk Debugger • OpenAI-Compatible Server (Port 8080) • LMSYS Model Arena • 100% Air-Gapped*

---

## ⚡ Overview

EdgeLLM Studio has been rewritten as a high-performance **React SPA (Vite + TypeScript + Tailwind CSS)** application, preserving all core neural execution capabilities, business logic, and UI architecture.

### 🌟 Key Ported Features & Studios

1. **Dashboard & Hardware Telemetry (`DashboardScreen`)**
   - Real-time silicon monitoring: Snapdragon 8 Gen 3 / Tensor / ARM64, NPU 45+ TOPS, GPU compute shaders, battery thermals, and RAM pressure pool.
   - Active loaded model status and 1-tap quick launchers into all 11 neural studios.

2. **Decisive Straight-to-Point Chat Studio (`InferenceScreen`)**
   - Streaming token synthesis with real-time speedometer (`tok/s`), Time-To-First-Token (`TTFT`), total tokens, and hardware backend indicator.
   - Zero-filler factual answers across electronics, Java/OOP, networking, physics, algorithms, and multi-lingual translations.
   - Configurable Chain-of-Thought (CoT) thinking mode with collapsible reasoning trace.
   - Web Speech API integration for real-time voice input and neural Text-to-Speech (TTS) readout.
   - Thumbs up/down rating and 1-click export to the Zero-Knowledge Vault.

3. **On-Device Model Vault (`DeviceAndModelsScreen`)**
   - Local model management for GGUF, LiteRT, ONNX, and Alibaba MNN checkpoints.
   - 1-click load and activate, download caching simulation, and custom `.gguf` / `.onnx` file registration.

4. **Hugging Face Hub Explorer (`HfExplorerScreen`)**
   - Live mobile hub browser with over 100,000+ open-source model checkpoints.
   - Precision filters by format (`GGUF`, `MediaPipe`, `MNN`) and quantization (`Q4_K_M`, `INT4`).
   - 1-tap download into local model vault.

5. **LMSYS Neural Model Arena & Tensor Clash (`ModelArenaScreen`)**
   - Side-by-side dual A/B battle colosseum with live stream velocity clash.
   - Blind Holographic Shield mode to eliminate human confirmation bias.
   - Curated challenge decks (Quantum Grandfather Paradox, Three Gods riddle, Zero-Allocation Ring Buffer, Speed Blitz).
   - Jury verdict with dynamic Elo rating changes and the Classified Hall of Elo Leaderboard.

6. **On-Device Stable Diffusion Studio (`ImageTaskScreen`)**
   - Latent Diffusion Text-to-Image and Image-to-Image synthesis.
   - Interactive Touch/Mouse Inpainting Canvas Mask Brush (`MaskPainterDialog`).
   - 4× ESRGAN neural super-resolution upscale.

7. **RAG Semantic Knowledge Base & Chunk Debugger (`RagDebugScreen`)**
   - Grounding documents with 128-dimensional on-device cosine vector embeddings.
   - Content-addressed SHA-256 chunk inspector with token counts and latency tracking.
   - Live cosine similarity threshold slider ($0.10 \dots 0.95$).

8. **OpenAI & Ollama-Compatible Local HTTP Server (`ApiServerScreen`)**
   - Embedded local daemon on port `8080` (or Ollama port `11434`).
   - Bearer API token generation, rotation, copy-to-clipboard, and live curl test snippet.
   - Real-time request audit log with latency and status codes.

9. **Asynchronous Background Queue (`BackgroundTasksScreen`)**
   - Non-blocking batch document summaries, code audits, and embedding indexers.

10. **Sandboxed Plugins & MCP Ecosystem (`PluginPipelineScreen`)**
    - PII Privacy Sanitizer, Log Anomaly Threat Scanner, Meeting Summarizer, and Code Security Reviewer.
    - Model Context Protocol (MCP) server integration.

11. **Zero-Knowledge Encrypted Vault (`EncryptedVaultScreen`)**
    - AES-256-GCM encryption with PBKDF2 (100,000 iterations) using the native Web Crypto API.

12. **Silicon Governor & Visual Aesthetic Studio (`SiliconGovernorSheet` & `ThemeStudioSheet`)**
    - Max Performance, Dynamic Balanced, and Eco Battery Saver modes.
    - 6 cyberpunk and clean theme palettes: Quantum Cyber, Neural Violet, Emerald Matrix, Solar Plasma, Crimson Valkyrie, Obsidian Stealth.

---

## 🛠️ Development & Running

### Requirements
- Node.js 22+
- npm

### Installation & Dev Server
```bash
npm install
npm run dev
```

The application runs on `http://0.0.0.0:3000`.

### Production Build
```bash
npm run build
```
