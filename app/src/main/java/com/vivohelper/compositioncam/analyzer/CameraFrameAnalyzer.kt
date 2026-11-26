package com.vivohelper.compositioncam.analyzer

import android.graphics.Rect
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.vivohelper.compositioncam.model.DetectedSubject
import com.vivohelper.compositioncam.model.SceneMode

/**
 * CameraX 实时图像帧分析器
 * 使用 Google ML Kit 进行端侧离线人脸与物体检测，并做节流控制以降低功耗
 */
class CameraFrameAnalyzer(
    private val getSceneMode: () -> SceneMode,
    private val isFrontCamera: () -> Boolean,
    private val onSubjectsDetected: (List<DetectedSubject>) -> Unit
) : ImageAnalysis.Analyzer {

    // 人像检测器（快速模式保证取景流畅）
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    // 通用物体检测器（单图多目标流式模式）
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
    )

    private var lastAnalyzeTimestamp = 0L
    private val analyzeIntervalMs = 180L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalyzeTimestamp < analyzeIntervalMs) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        lastAnalyzeTimestamp = now
        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        // 计算旋转后的实际逻辑宽高，用于将像素框转换为 0f..1f 归一化坐标
        val rotatedWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val rotatedHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
        val mirrorX = isFrontCamera()

        val mode = getSceneMode()
        if (mode == SceneMode.PORTRAIT) {
            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    val list = faces.sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }
                        .map { face ->
                            DetectedSubject(
                                normalizedBox = toNormalizedRect(face.boundingBox, rotatedWidth, rotatedHeight, mirrorX),
                                label = "人像",
                                isFace = true
                            )
                        }
                    onSubjectsDetected(list)
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            objectDetector.process(inputImage)
                .addOnSuccessListener { objects ->
                    val list = objects.sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }
                        .map { obj ->
                            val tag = obj.labels.firstOrNull()?.text ?: "主体"
                            DetectedSubject(
                                normalizedBox = toNormalizedRect(obj.boundingBox, rotatedWidth, rotatedHeight, mirrorX),
                                label = tag,
                                isFace = false
                            )
                        }
                    onSubjectsDetected(list)
                }
                .addOnCompleteListener { imageProxy.close() }
        }
    }

    private fun toNormalizedRect(
        rect: Rect,
        width: Int,
        height: Int,
        mirrorX: Boolean
    ): RectF {
        if (width <= 0 || height <= 0) return RectF()
        val left = (rect.left.toFloat() / width).coerceIn(0f, 1f)
        val top = (rect.top.toFloat() / height).coerceIn(0f, 1f)
        val right = (rect.right.toFloat() / width).coerceIn(0f, 1f)
        val bottom = (rect.bottom.toFloat() / height).coerceIn(0f, 1f)
        return if (mirrorX) {
            RectF(1f - right, top, 1f - left, bottom)
        } else {
            RectF(left, top, right, bottom)
        }
    }

    fun close() {
        faceDetector.close()
        objectDetector.close()
    }
}
