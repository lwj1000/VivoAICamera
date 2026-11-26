package com.vivohelper.compositioncam.network

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.vivohelper.compositioncam.model.AiConfig
import com.vivohelper.compositioncam.model.SceneMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 多模态大模型 AI 摄影点评客户端
 * 支持兼容 OpenAI Chat Completions Vision 格式的接口（如智谱 GLM-4V、通义千问 VL、Gemini 代理等）
 */
object VisionAiAdvisor {

    private const val PREF_NAME = "ai_advisor_prefs"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model_name"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    fun loadConfig(context: Context): AiConfig {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val defaults = AiConfig()
        return AiConfig(
            baseUrl = prefs.getString(KEY_BASE_URL, defaults.baseUrl) ?: defaults.baseUrl,
            apiKey = prefs.getString(KEY_API_KEY, defaults.apiKey) ?: defaults.apiKey,
            modelName = prefs.getString(KEY_MODEL, defaults.modelName) ?: defaults.modelName
        )
    }

    fun saveConfig(context: Context, config: AiConfig) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_MODEL, config.modelName.trim())
            .apply()
    }

    /**
     * 请求多模态大模型分析当前取景画面并给出构图建议
     */
    suspend fun analyzeFrame(
        bitmap: Bitmap,
        sceneMode: SceneMode,
        config: AiConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("未配置 API Key，请先点击右上角「AI 设置」填写"))
        }

        try {
            val base64Image = compressToBase64(bitmap)
            val promptText = buildPrompt(sceneMode)

            val contentArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", promptText)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$base64Image")
                    })
                })
            }

            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", contentArray)
                })
            }

            val payload = JSONObject().apply {
                put("model", config.modelName.ifBlank { "glm-4v-flash" })
                put("messages", messages)
                put("max_tokens", 300)
                put("temperature", 0.5)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = payload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(config.baseUrl)
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(RuntimeException("请求失败 (${response.code})：$rawBody"))
                }
                val json = JSONObject(rawBody)
                val choices = json.optJSONArray("choices")
                val reply = choices?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()

                if (reply.isNullOrBlank()) {
                    Result.failure(RuntimeException("模型未返回有效文本内容"))
                } else {
                    Result.success(reply)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildPrompt(sceneMode: SceneMode): String {
        val focus = when (sceneMode) {
            SceneMode.PORTRAIT -> "重点关注人像位置、头顶留白、人物姿态与视线引导、背景杂物避让、焦段建议。"
            SceneMode.LANDSCAPE -> "重点关注地平线位置、建筑线条透视、前景中景层次感、对称或三分法构图。"
            SceneMode.FOOD -> "重点关注拍摄角度（俯拍/45度）、桌面杂物清理、主体摆放重心与光影质感。"
            SceneMode.DAILY -> "重点关注视觉焦点是否突出、画面留白是否舒适、色彩与光线氛围。"
        }
        return "你是一位专业手机摄影构图指导老师。用户正在用 vivo X200 Ultra 准备拍摄「${sceneMode.label}」画面，请直接根据当前取景框画面给出 3 条简洁、可立刻执行的调整建议（不超过 120 字）。$focus"
    }

    private fun compressToBase64(bitmap: Bitmap): String {
        val maxSide = 768
        val ratio = minOf(maxSide.toFloat() / bitmap.width, maxSide.toFloat() / bitmap.height, 1f)
        val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        val scaled = if (ratio < 1f) Bitmap.createScaledBitmap(bitmap, targetW, targetH, true) else bitmap

        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 78, stream)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
