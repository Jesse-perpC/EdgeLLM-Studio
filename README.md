<div align="center">

```
   ███████╗██████╗  ██████╗ ███████╗██╗     ██╗     ███╗   ███╗
   ██╔════╝██╔══██╗██╔════╝ ██╔════╝██║     ██║     ████╗ ████║
   █████╗  ██║  ██║██║  ███╗█████╗  ██║     ██║     ██╔████╔██║
   ██╔══╝  ██║  ██║██║   ██║██╔══╝  ██║     ██║     ██║╚██╔╝██║
   ███████╗██████╔╝╚██████╔╝███████╗███████╗███████╗██║ ╚═╝ ██║
   ╚══════╝╚═════╝  ╚═════╝ ╚══════╝╚══════╝╚══════╝╚═╝     ╚═╝
             S  T  U  D  I  O  //  O N - D E V I C E
```

### **NEXT-GENERATION NEURAL SILICON RUNTIME FOR ANDROID**
*100% Air-Gapped • Hardware-Accelerated (NPU/GPU) • Zero-Cloud Telemetry • Cryptographic Keystore*

---

[![Build Status](https://img.shields.io/badge/CI%2FCD-GitHub_Actions_Passing-06B6D4?style=for-the-badge&logo=githubactions&logoColor=white)](https://github.com)
[![Platform](https://img.shields.io/badge/Platform-Android_14%2B_%7C_API_34%2B-A855F7?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Compute](https://img.shields.io/badge/Compute-Vulkan_1.3_%7C_NNAPI_HTP-10B981?style=for-the-badge&logo=khronosgroup&logoColor=white)](https://www.khronos.org/vulkan/)
[![Security](https://img.shields.io/badge/Vault-AES--256--GCM_Hardware-F59E0B?style=for-the-badge&logo=securityscorecard&logoColor=white)](https://developer.android.com/training/articles/keystore)
[![Privacy](https://img.shields.io/badge/Network-100%25_Offline_Air--Gapped-EF4444?style=for-the-badge&logo=shield&logoColor=white)](/)

```
================================== SYSTEM TELEMETRY ==================================
[STATUS: READY]   [SILICON: QUALCOMM / MEDIATEK / TENSOR]   [NETWORK: AIR-GAPPED]
[VRAM BUFFER: OPTIMAL]   [KV-CACHE: DYNAMIC FP16]   [CRYPTO VAULT: HARDWARE KEYSTORE]
======================================================================================
```

</div>

---

## ⚡ Executive Overview

**EdgeLLM Studio** is a sovereign, local-first artificial intelligence runtime designed from the bare metal up for modern ARM mobile silicon. By unifying **Vulkan 1.3 GPU compute shaders**, **Neural Processing Unit (NPU) accelerators**, and **dynamic FP16 Key-Value Attention caching**, EdgeLLM Studio executes state-of-the-art Large Language Models entirely on-device without sending a single byte over the network.

---

## 🛰️ Interactive System Capabilities

<details open>
<summary><b>📊 1. Real-Time Silicon Utilization HUD & Memory Monitor</b> (Click to collapse/expand)</summary>
<br>

Visualizes low-level hardware utilization through live Bézier telemetry and dynamic memory segmentation:

- **Hardware Acceleration Trends**: Real-time 25-second rolling timeline capturing **GPU (Cyan)**, **NPU (Violet)**, and **CPU NEON (Amber)** compute workloads.
- **Interactive Scrubber**: Touch or scrub anywhere across the timeline to inspect exact silicon loads at individual second timestamps.
- **Memory Segmentation Donut & Stacked Visualizer**:
  $$\text{RAM}_{\text{Total}} = \text{Model Weights (VRAM)} + \text{KV Attention Cache} + \text{OS Buffer} + \text{Headroom}$$
- **Zero-OOM Protection**: Dynamic calculation prevents Android Low Memory Killer (LMK) eviction by enforcing safe memory thresholds.
- **Live Diagnostics**: Continuously polls Time-To-First-Token (**TTFT** in ms), generation throughput (**tokens/second**), thermal envelope (**°C**), and instantaneous power draw (**Watts**).

</details>

<details open>
<summary><b>🧠 2. Unified Multi-Format On-Device Model Zoo</b> (Click to collapse/expand)</summary>
<br>

EdgeLLM Studio seamlessly runs state-of-the-art quantized weights across industry-standard formats:

| Model Designation | Architecture | Precision | Parameters | VRAM Footprint | Compute Target |
|:------------------|:-------------|:----------|:-----------|:---------------|:---------------|
| **SmolLM-135M**   | GGUF         | `Q4_K_M`  | 135 Million| ~120 MB        | Universal CPU / Low-tier NPU |
| **Qwen2.5-0.5B**  | GGUF / ONNX  | `Q4_K_M`  | 490 Million| ~380 MB        | GPU Vulkan / Adreno |
| **TinyLlama-1.1B**| GGUF         | `Q4_K_M`  | 1.1 Billion| ~669 MB        | NPU Hexagon / Tensor TPU |
| **Gemma-2-2B**    | GGUF / TFLite| `Q4_K_M`  | 2.6 Billion| ~1.6 GB        | Flagship NPU / Dimensity APU |
| **Phi-3-Mini-3.8B**| GGUF / ONNX | `Q4_K_M`  | 3.8 Billion| ~2.3 GB        | Snapdragon 8 Gen 3+ (12GB+ RAM) |

> 💡 *Switch active models on-the-fly in 1-tap from the **Dashboard Carousel**; memory allocations and headroom are recomputed in real time.*

</details>

<details>
<summary><b>🎛️ 3. Dynamic KV-Cache Attention Sizer</b> (Click to expand)</summary>
<br>

Long-context inference creates substantial memory demands via attention Key-Value buffers. EdgeLLM Studio features an interactive context sizer to dial in prompt context lengths:

```
+-----------------------------------------------------------------------------+
| Context Window : [========--------------------------------] 512 / 2048 tok  |
| Memory Scaling : ~256 KB per Token (FP16 Attention Heads, 32 Layers)        |
| Allocation     : 128 MB KV-Buffer @ 512 tok  --->  512 MB KV-Buffer @ 2048 tok|
+-----------------------------------------------------------------------------+
```

Adjust the slider to simulate device headroom under extended multi-turn conversations.

</details>

<details>
<summary><b>🛡️ 4. Air-Gapped Cryptographic Vault (AES-256-GCM)</b> (Click to expand)</summary>
<br>

All user prompts, model generations, and local context caches are encrypted at rest using **hardware-backed keys** stored in the **Android Keystore (StrongBox / TEE)**.

```
       [Raw User Prompt]
               │
               ▼
   ┌───────────────────────┐
   │ Android Keystore TEE  │ ──> Generates 256-bit AES-GCM Key
   └───────────────────────┘
               │
               ▼
   [Sealed Ciphertext + 128-bit Auth Tag] ──> Local Room SQLite (Zero Leakage)
```

- **Zero Network Egress**: Absolutely no network permissions required for inference execution.
- **Biometric / PIN Unlock**: Hardware authorization before decrypting chat archives.
- **Cryptographic Export**: Export sealed conversation backups for cold storage.

</details>

<details>
<summary><b>🔌 5. Modular Local Plugins & Autonomous Tool-Calling</b> (Click to expand)</summary>
<br>

Equip local models with air-gapped agency through deterministic edge tools:
- **🧮 Math & Logic Evaluator**: High-precision offline arithmetic and algebraic solver.
- **⚡ Local Vector Store**: On-device semantic embeddings for retrieval-augmented generation (RAG).
- **📝 Vault Notes Integration**: Search, summarize, and append to encrypted personal notes.
- **💻 Sandboxed Python / Micro-Terminal**: Safe offline script execution for deterministic tasks.

</details>

---

## 🏗️ Neural Compute Pipeline

```
           ┌──────────────────────────────────────────────┐
           │             USER PROMPT INGESTION            │
           └──────────────────────┬───────────────────────┘
                                  │
                                  ▼
           ┌──────────────────────────────────────────────┐
           │       TOKENIZER & ATTENTION KV COMPRESSION   │
           └──────────────────────┬───────────────────────┘
                                  │
                 ┌────────────────┴────────────────┐
                 ▼                                 ▼
    ┌─────────────────────────┐       ┌─────────────────────────┐
    │  QUALCOMM HEXAGON NPU   │       │   VULKAN 1.3 COMPUTE    │
    │   (NNAPI / HTP Shaders) │       │   (Adreno / Immortalis) │
    └────────────┬────────────┘       └────────────┬────────────┘
                 │                                 │
                 └────────────────┬────────────────┘
                                  │
                                  ▼
           ┌──────────────────────────────────────────────┐
           │       STREAMING DECODING ENGINE (TOK/S)      │
           │       • Prefill TTFT Latency Reduction       │
           │       • Speculative Sampling Acceleration    │
           └──────────────────────┬───────────────────────┘
                                  │
                                  ▼
           ┌──────────────────────────────────────────────┐
           │      MATERIAL 3 REACTIVE UI (JETPACK COMPOSE)│
           └──────────────────────────────────────────────┘
```

---

## 🚀 Quick Start: Deploying the APK

### Method A: Automated GitHub Actions Build (Zero Setup Required)

Every commit pushed to this repository triggers an automated CI/CD pipeline that compiles, signs, and packages an installable Android APK.

1. Navigate to the [**Actions**](../../actions) tab at the top of this repository.
2. Click on the latest workflow run: **`Build Android APK`**.
3. Scroll down to the **Artifacts** section at the bottom of the summary page.
4. Download **`EdgeLLM-Studio-Debug-APK.zip`**.
5. Extract the ZIP and transfer `app-debug.apk` to your Android device, or install via ADB:
   ```bash
   adb install -r app-debug.apk
   ```

---

### Method B: Local Source Compilation

#### Prerequisites
- **JDK 17+** (`openjdk-17-jdk` or Temurin)
- **Android SDK Platform 34+**
- **Gradle 8.7+**

```bash
# 1. Clone the repository
git clone https://github.com/your-username/edgellm-studio.git
cd edgellm-studio

# 2. Prepare environment configuration
cp .env.example .env

# 3. Compile the debug APK
./gradlew assembleDebug --stacktrace

# 4. Push directly to connected device over USB/WiFi
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📱 Hardware Support Matrix

| Mobile Chipset | NPU Core | GPU Compute | Target Models | Status |
|:---|:---|:---|:---|:---:|
| **Snapdragon 8 Gen 3 / 4** | Hexagon HTP (45+ TOPS) | Adreno 750/830 (Vulkan 1.3) | SmolLM, Qwen, TinyLlama, Gemma 2, Phi-3 | 🟢 High Performance |
| **Snapdragon 8 Gen 2** | Hexagon DSP (33 TOPS) | Adreno 740 (Vulkan 1.3) | SmolLM, Qwen, TinyLlama, Gemma 2 | 🟢 Verified |
| **Google Tensor G3 / G4** | Eden TPU (Edge AI) | Immortalis-G715 / Mali | SmolLM, TinyLlama, Gemma 2 | 🟢 Verified |
| **MediaTek Dimensity 9300**| APU 790 (Generative AI) | Immortalis-G720 (Vulkan 1.3)| SmolLM, Qwen, TinyLlama, Gemma 2 | 🟢 Verified |
| **Universal ARM64 Devices**| ARM NEON (CPU Fallback)| OpenCL / Standard Vulkan | SmolLM-135M, Qwen2.5-0.5B | 🟡 Functional (CPU) |

---

## ⚙️ Interactive Feature Roadmap

- [x] **Real-time Silicon Utilization Visualizer** (GPU, NPU, CPU with interactive timeline scrubber)
- [x] **Live LLM Memory Allocation Donut & Stacked Bar** (VRAM weights, KV-cache, system headroom)
- [x] **Quick Model Selector Carousel** (1-tap switching with dynamic memory re-allocation)
- [x] **Zero-Knowledge Encrypted Vault** (AES-256-GCM hardware keystore)
- [x] **Multi-format Engine Loader** (GGUF, TFLite, ONNX Runtime)
- [x] **Dynamic KV-Cache Sizer** (Interactive context window token scaling)
- [x] **Continuous Integration Pipeline** (Automated GitHub Actions APK builds on every push)
- [ ] **Speculative Decoding Accelerator** (Draft model pairing for 2x+ tokens/sec)
- [ ] **Direct LoRA Adapter Hot-Swapping** (Runtime micro-fine-tune switching)
- [ ] **INT2/FP8 Experimental Quantization Engine**

---

## 📜 License & Sovereign AI Ethics

EdgeLLM Studio is released under the **Apache 2.0 License**.

> *EdgeLLM Studio is built on the philosophy of sovereign computing: Your prompts never touch external infrastructure, your model weights live in your device storage, and your privacy is governed by cryptographic silicon guarantees.*

<div align="center">

```
======================================================================================
     ENGINEERED FOR THE DISCONNECTED FUTURE • BUILT WITH JETPACK COMPOSE & KOTLIN
======================================================================================
```

</div>
