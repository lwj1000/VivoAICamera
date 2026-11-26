package com.vivohelper.compositioncam.model

import android.graphics.RectF

/**
 * 拍摄场景模式：针对不同常见拍摄主题应用专属构图规则
 */
enum class SceneMode(val label: String, val shortTip: String) {
    PORTRAIT("人像打卡", "自动追踪人脸，优化头顶留白与三分点站位"),
    LANDSCAPE("风景建筑", "高精度双轴水平仪与地平线三分法对齐"),
    FOOD("美食静物", "识别桌面主体，支持 90° 俯拍端平与 45° 视角提示"),
    DAILY("日常随手拍", "综合主体落点与水平度，实时给出构图评分")
}

/**
 * 构图参考网格类型
 */
enum class GridType(val label: String) {
    RULE_OF_THIRDS("三分法"),
    GOLDEN_SPIRAL("黄金比例"),
    SYMMETRY("中心对称"),
    NONE("无网格")
}

/**
 * 实时引导移动方向
 */
enum class GuideDirection {
    NONE,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    CLOSER,
    FURTHER,
    LEVEL_CW,
    LEVEL_CCW
}

/**
 * 端侧视觉模型识别到的主体（归一化坐标 0f..1f）
 */
data class DetectedSubject(
    val normalizedBox: RectF,
    val label: String,
    val isFace: Boolean = false
)

/**
 * 实时构图评估反馈结果
 */
data class CompositionFeedback(
    val score: Int = 75,
    val isOptimal: Boolean = false,
    val primaryTip: String = "正在识别取景画面...",
    val secondaryTip: String = "",
    val direction: GuideDirection = GuideDirection.NONE,
    val targetAnchorNormalized: Pair<Float, Float>? = null,
    val subjects: List<DetectedSubject> = emptyList(),
    val rollDegrees: Float = 0f,
    val pitchDegrees: Float = 90f
)

/**
 * 多模态大模型 API 配置（可选，用户自填）
 */
data class AiConfig(
    val baseUrl: String = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
    val apiKey: String = "",
    val modelName: String = "glm-4v-flash"
)
