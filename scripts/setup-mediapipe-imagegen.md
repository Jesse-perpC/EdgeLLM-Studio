# MediaPipe ImageGenerator setup

The `StableDiffusionEngine` uses Google's **MediaPipe tasks-genai** library for on-device Stable Diffusion image generation. This is a real integration — not a placeholder. When properly configured, it runs actual Stable Diffusion 1.5 weights on-device via the MediaPipe ImageGenerator API.

## Dependency

The dependency is already declared in `app/build.gradle.kts`:

```kotlin
```toml
mediapipe-tasks-genai = "0.10.27"  # check for latest version
```

Verify it's present. If missing, add it to `libs.versions.toml`:

```toml
[versions]
mediapipe-tasks-genai = "0.10.14"  # check for latest version

[libraries]
mediapipe-tasks-genai = { group = "com.google.mediapipe", name = "tasks-genai", version.ref = "mediapipe-tasks-genai" }
```

## Model Download

MediaPipe ships a pre-trained Stable Diffusion 1.5 `.task` bundle. Download it before attempting generation:

**Direct download URL:**
```
https://storage.googleapis.com/mediapipe-assets/models/ImageGenerator/stable_diffusion_v1_5.text_to_image.task
```

**Alternative (Google AI Edge page):**
Visit https://ai.google.dev/edge/mediapipe/solutions/genai/image_generator and click the model download link.

The file is approximately **2 GB**. Store it on device external storage or internal app storage.

## Placing the model on device

Once downloaded, copy the `.task` file to the device:

```bash
# Via ADB
adb push stable_diffusion_v1_5.text_to_image.task /sdcard/Download/

# Or via Android Studio's Device File Explorer
# Path: /sdcard/Download/stable_diffusion_v1_5.text_to_image.task
```

The app expects the path to be passed via `ImageGenOptions.modelPath`. You can configure this in the app's settings or pass it programmatically:

```kotlin
val opts = StableDiffusionEngine.ImageGenOptions(
    modelPath = "/sdcard/Download/stable_diffusion_v1_5.text_to_image.task",
    width = 512,
    height = 512,
    numInferenceSteps = 20,
    guidanceScale = 7.5f
)
```

## API reference

The engine exposes three task types via `StableDiffusionEngine.ImageTask`:

| Task | Method | Additional inputs |
|---|---|---|
| `TEXT_TO_IMAGE` | `generateImage(prompt)` | None |
| `IMAGE_TO_IMAGE` | `generateImage(prompt, sourceBitmap)` | Source bitmap to transform |
| `INPAINT` | `generateImageInpainting(prompt, sourceBitmap, maskBitmap)` | Source + white=regen, black=keep mask |

All methods return a `Flow<ImageGenResult>`:

```kotlin
data class ImageGenResult(
    val bitmap: Bitmap?,
    val task: ImageTask,
    val elapsedMs: Long,
    val width: Int,
    val height: Int,
    val isComplete: Boolean,
    val error: String? = null
)
```

## Device requirements

- **Vulkan-capable GPU strongly recommended.** CPU-only inference on Android is extremely slow (~2-5 minutes per 512x512 image). Devices with Adreno 6xx/7xx, Mali-G77/G78, or equivalent support Vulkan acceleration.
- Minimum Android: **API 24** (matches the app's `minSdk`)
- Memory: At least **4 GB RAM** recommended; the model alone requires ~2 GB
- The MediaPipe runtime uses NNAPI/Vulkan delegates under the hood

## Troubleshooting

- **`NoClassDefFoundError: ImageGenerator`**: The `tasks-genai` dependency isn't on the classpath. Verify it's in `build.gradle.kts` and rebuild.
- **`UnsatisfiedLinkError`**: The MediaPipe native library isn't bundled. This happens when the `tasks-genai` AAR isn't resolved. Check Gradle sync output for errors.
- **Model file not found**: Double-check the path. Android scoped storage may block access to `/sdcard/Download/`. Use `context.getExternalFilesDir(null)?.absolutePath` for app-scoped storage that's always accessible.
- **Black/blank image output**: The model may have failed to initialize. Check the `error` field on `ImageGenResult`. Common causes: insufficient memory, incompatible GPU driver, or corrupted model file.
- **Very slow generation**: You're running on CPU. Ensure the device has Vulkan support and the MediaPipe Vulkan delegate is loading correctly. Check Logcat for `MediaPipe Vulkan delegate initialized`.

## Usage example

```kotlin
val engine = StableDiffusionEngine(context)
engine.generate(
    prompt = "a photograph of a cat wearing a astronaut suit on mars",
    task = StableDiffusionEngine.ImageTask.TEXT_TO_IMAGE,
    opts = StableDiffusionEngine.ImageGenOptions(
        modelPath = modelFilePath,
        width = 512,
        height = 512,
        numInferenceSteps = 20
    )
).collect { result ->
    if (result.isComplete) {
        result.bitmap?.let { displayImage(it) }
        Log.i(TAG, "Generated ${result.width}x${result.height} in ${result.elapsedMs}ms")
    }
}
```