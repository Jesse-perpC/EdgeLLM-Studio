package com.perpcorp.edgellm.engine

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * On-device image generation slot for MediaPipe ImageGenerator / SD weights.
 *
 * The published `tasks-genai` AAR used by this project does not currently
 * expose ImageGenerator / imagegenerator packages at compile time, so this
 * engine reports Unavailable with a concrete reason instead of referencing
 * missing types or returning placeholder bitmaps.
 *
 * See scripts/setup-mediapipe-imagegen.md for bundling when the API is available.
 */
class StableDiffusionEngine(private val context: Context?) {

    companion object {
        private const val TAG = "StableDiffusionEngine"
    }

    class ImageGenUnavailableException(message: String, cause: Throwable? = null) :
        RuntimeException(message, cause)

    enum class ImageTask { TEXT_TO_IMAGE, IMAGE_TO_IMAGE, INPAINT }

    data class ImageGenOptions(
        val modelPath: String,
        val width: Int = 512,
        val height: Int = 512,
        val seed: Long = -1L,
        val numInferenceSteps: Int = 20,
        val guidanceScale: Float = 7.5f
    )

    data class ImageGenResult(
        val bitmap: Bitmap?,
        val task: ImageTask,
        val elapsedMs: Long,
        val width: Int,
        val height: Int,
        val isComplete: Boolean,
        val error: String? = null
    )

    /**
     * Make the return type explicit so Kotlin can infer generic types at call site.
     */
    fun generate(
        prompt: String,
        task: ImageTask,
        opts: ImageGenOptions,
        sourceBitmap: Bitmap? = null,
        maskBitmap: Bitmap? = null
    ): Flow<ImageGenResult> = flow<ImageGenResult> {
        if (opts.modelPath.isBlank()) {
            throw ImageGenUnavailableException(
                "No image model path configured. Download a MediaPipe ImageGenerator " +
                    ".task bundle and set its local path."
            )
        }
        val modelFile = File(opts.modelPath)
        if (!modelFile.isFile) {
            throw ImageGenUnavailableException(
                "Image model file not found: ${opts.modelPath}."
            )
        }
        if (task == ImageTask.IMAGE_TO_IMAGE && sourceBitmap == null) {
            throw ImageGenUnavailableException("IMAGE_TO_IMAGE requires a source bitmap.")
        }
        if (task == ImageTask.INPAINT && (sourceBitmap == null || maskBitmap == null)) {
            throw ImageGenUnavailableException(
                "INPAINT requires both a source bitmap and a mask bitmap."
            )
        }
        // Compile-safe honesty: ImageGenerator types are not on the classpath.
        throw ImageGenUnavailableException(
            "MediaPipe ImageGenerator is not available in the published tasks-genai " +
                "dependency used by this build. See scripts/setup-mediapipe-imagegen.md."
        )
    }.flowOn(Dispatchers.Default)
