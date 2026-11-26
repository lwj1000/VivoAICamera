package com.vivohelper.compositioncam.analyzer

import com.vivohelper.compositioncam.model.CompositionFeedback
import com.vivohelper.compositioncam.model.DetectedSubject
import com.vivohelper.compositioncam.model.GuideDirection
import com.vivohelper.compositioncam.model.SceneMode
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 端侧实时构图评估引擎
 * 结合主体坐标与陀螺仪姿态，针对四大场景给出即时构图评分与方向引导
 */
object CompositionEngine {

    // 三分法四个黄金交点与水平中心上交点
    private val thirdPoints = listOf(
        0.333f to 0.333f,
        0.667f to 0.333f,
        0.333f to 0.667f,
        0.667f to 0.667f
    )

    fun evaluate(
        sceneMode: SceneMode,
        subjects: List<DetectedSubject>,
        rollDegrees: Float,
        pitchDegrees: Float
    ): CompositionFeedback {
        return when (sceneMode) {
            SceneMode.PORTRAIT -> evaluatePortrait(subjects, rollDegrees, pitchDegrees)
            SceneMode.LANDSCAPE -> evaluateLandscape(subjects, rollDegrees, pitchDegrees)
            SceneMode.FOOD -> evaluateFood(subjects, rollDegrees, pitchDegrees)
            SceneMode.DAILY -> evaluateDaily(subjects, rollDegrees, pitchDegrees)
        }
    }

    /**
     * 人像打卡构图评估：关注头顶留白、人脸占比、三分线对齐与水平度
     */
    private fun evaluatePortrait(
        subjects: List<DetectedSubject>,
        roll: Float,
        pitch: Float
    ): CompositionFeedback {
        val primary = subjects.firstOrNull { it.isFace } ?: subjects.firstOrNull()
        val rollPenalty = (abs(roll) * 2.5f).coerceAtMost(25f)

        if (primary == null) {
            val levelTip = if (abs(roll) > 2.5f) "画面略斜，请微调水平" else "请将人物放入取景框内"
            return CompositionFeedback(
                score = (70 - rollPenalty).roundToInt().coerceIn(30, 95),
                isOptimal = false,
                primaryTip = levelTip,
                secondaryTip = "建议人物双眼靠近上三分线，头顶留出约 15% 空间",
                direction = rollDirection(roll),
                targetAnchorNormalized = 0.5f to 0.333f,
                subjects = subjects,
                rollDegrees = roll,
                pitchDegrees = pitch
            )
        }

        val box = primary.normalizedBox
        val cx = box.centerX()
        val cy = box.centerY()
        val faceHeight = box.height()
        val headSpace = box.top

        // 寻找最适合当前人物站位的目标点（左三分、居中上三分、右三分）
        val candidateAnchors = listOf(0.333f to 0.333f, 0.5f to 0.333f, 0.667f to 0.333f)
        val bestAnchor = candidateAnchors.minByOrNull { (ax, ay) -> hypot(cx - ax, cy - ay) }!!

        var score = 100f - rollPenalty
        var tip = "人像构图很棒，可以按下快门"
        var subTip = "头顶留白与人物比例协调"
        var dir = GuideDirection.NONE

        when {
            abs(roll) > 3.0f -> {
                tip = if (roll > 0) "手机向右倾，请逆时针调平" else "手机向左倾，请顺时针调平"
                subTip = "保持地平线或背景线条水平"
                dir = rollDirection(roll)
            }
            headSpace < 0.06f -> {
                score -= 22f
                tip = "头顶留白太少，镜头略微抬高"
                subTip = "避免切到头顶，留出 10%~20% 呼吸感"
                dir = GuideDirection.UP
            }
            headSpace > 0.32f && cy > 0.45f -> {
                score -= 18f
                tip = "头顶留白过多，镜头稍微压低"
                subTip = "让人物眼睛靠近上三分线"
                dir = GuideDirection.DOWN
            }
            faceHeight < 0.09f -> {
                score -= 15f
                tip = "人物偏小，可走近几步或切 2x/3.7x 长焦"
                subTip = "长焦人像背景虚化更好、构图更聚焦"
                dir = GuideDirection.CLOSER
            }
            faceHeight > 0.45f -> {
                score -= 15f
                tip = "画面过满，建议后退半步"
                subTip = "适当留出环境背景更有故事感"
                dir = GuideDirection.FURTHER
            }
            cx - bestAnchor.first > 0.08f -> {
                score -= 14f
                tip = "人物偏右，镜头向右微移将人物放在${anchorName(bestAnchor.first)}"
                subTip = "对齐绿色目标点可提升视觉平衡"
                dir = GuideDirection.RIGHT
            }
            bestAnchor.first - cx > 0.08f -> {
                score -= 14f
                tip = "人物偏左，镜头向左微移将人物放在${anchorName(bestAnchor.first)}"
                subTip = "对齐绿色目标点可提升视觉平衡"
                dir = GuideDirection.LEFT
            }
        }

        val finalScore = score.roundToInt().coerceIn(25, 99)
        return CompositionFeedback(
            score = finalScore,
            isOptimal = finalScore >= 85 && dir == GuideDirection.NONE,
            primaryTip = tip,
            secondaryTip = subTip,
            direction = dir,
            targetAnchorNormalized = bestAnchor,
            subjects = subjects,
            rollDegrees = roll,
            pitchDegrees = pitch
        )
    }

    /**
     * 风景建筑构图评估：核心关注双轴水平与垂直透视防畸变
     */
    private fun evaluateLandscape(
        subjects: List<DetectedSubject>,
        roll: Float,
        pitch: Float
    ): CompositionFeedback {
        val absRoll = abs(roll)
        val rollPenalty = (absRoll * 4f).coerceAtMost(35f)
        // 建筑拍摄时，pitch 接近 90° 说明手机竖直无仰俯畸变
        val pitchDiffFromVertical = abs(pitch - 90f)
        val pitchPenalty = if (pitchDiffFromVertical > 12f) (pitchDiffFromVertical - 12f) * 0.8f else 0f

        var score = (100f - rollPenalty - pitchPenalty).roundToInt().coerceIn(30, 99)
        var dir = GuideDirection.NONE
        val tip: String
        val subTip: String

        when {
            absRoll > 2.0f -> {
                tip = if (roll > 0) "地平线偏斜，请逆时针调平 (${String.format("%.1f", absRoll)}°)"
                else "地平线偏斜，请顺时针调平 (${String.format("%.1f", absRoll)}°)"
                subTip = "建议将地平线放在下三分线或上三分线"
                dir = rollDirection(roll)
            }
            pitchDiffFromVertical > 18f -> {
                score = (score - 5).coerceAtLeast(40)
                tip = "建筑仰/俯角较大，如需横平竖直请端直手机"
                subTip = if (pitch > 90f) "当前为俯拍视角" else "当前为仰拍视角，注意边缘透视"
            }
            else -> {
                tip = "水平已对齐，构图稳健"
                subTip = "可将视觉焦点置于三分线交点增强层次感"
            }
        }

        val primary = subjects.firstOrNull()
        val anchor = if (primary != null) {
            thirdPoints.minByOrNull { (ax, ay) -> hypot(primary.normalizedBox.centerX() - ax, primary.normalizedBox.centerY() - ay) }
        } else {
            0.5f to 0.667f
        }

        return CompositionFeedback(
            score = score,
            isOptimal = absRoll <= 1.8f && score >= 85,
            primaryTip = tip,
            secondaryTip = subTip,
            direction = dir,
            targetAnchorNormalized = anchor,
            subjects = subjects,
            rollDegrees = roll,
            pitchDegrees = pitch
        )
    }

    /**
     * 美食静物构图评估：检测俯拍端平与 45° 黄金视角 + 主体落点引导
     */
    private fun evaluateFood(
        subjects: List<DetectedSubject>,
        roll: Float,
        pitch: Float
    ): CompositionFeedback {
        val primary = subjects.firstOrNull()
        val absRoll = abs(roll)
        val isFlatLay = pitch < 12f // 俯拍（手机接近平行桌面）
        val isFortyFive = pitch in 35f..55f // 45° 经典美食视角

        var score = 92f - (absRoll * 2.5f).coerceAtMost(25f)
        var dir = GuideDirection.NONE
        var tip = "主体位置不错，可以拍摄"
        var subTip = when {
            isFlatLay -> "当前为 90° 俯拍视角，手机已端平"
            isFortyFive -> "当前为 45° 经典美食视角，立体感最佳"
            else -> "可尝试 90° 垂直俯拍或 45° 侧拍"
        }

        if (absRoll > 2.8f && !isFlatLay) {
            tip = "画面轻微倾斜，请微调水平"
            dir = rollDirection(roll)
        } else if (primary != null) {
            val cx = primary.normalizedBox.centerX()
            val cy = primary.normalizedBox.centerY()
            // 美食常用中心构图或三分交点
            val anchors = listOf(0.5f to 0.5f) + thirdPoints
            val best = anchors.minByOrNull { (ax, ay) -> hypot(cx - ax, cy - ay) }!!
            val dist = hypot(cx - best.first, cy - best.second)

            if (dist > 0.12f) {
                score -= (dist * 45f).coerceAtMost(22f)
                dir = when {
                    cx - best.first > 0.08f -> GuideDirection.RIGHT
                    best.first - cx > 0.08f -> GuideDirection.LEFT
                    cy - best.second > 0.08f -> GuideDirection.DOWN
                    else -> GuideDirection.UP
                }
                tip = "将美食主体微调至中心或三分交点"
            }
            return CompositionFeedback(
                score = score.roundToInt().coerceIn(35, 99),
                isOptimal = dist <= 0.12f && (isFlatLay || absRoll <= 2.5f),
                primaryTip = tip,
                secondaryTip = subTip,
                direction = dir,
                targetAnchorNormalized = best,
                subjects = subjects,
                rollDegrees = roll,
                pitchDegrees = pitch
            )
        } else {
            tip = if (isFlatLay) "俯拍视角已就绪，请对准美食主体" else "请将美食或静物放入取景框"
            score -= 15f
        }

        return CompositionFeedback(
            score = score.roundToInt().coerceIn(35, 99),
            isOptimal = false,
            primaryTip = tip,
            secondaryTip = subTip,
            direction = dir,
            targetAnchorNormalized = 0.5f to 0.5f,
            subjects = subjects,
            rollDegrees = roll,
            pitchDegrees = pitch
        )
    }

    /**
     * 日常随手拍构图评估：综合主体三分点距离与水平度
     */
    private fun evaluateDaily(
        subjects: List<DetectedSubject>,
        roll: Float,
        pitch: Float
    ): CompositionFeedback {
        val absRoll = abs(roll)
        val rollPenalty = (absRoll * 3f).coerceAtMost(30f)
        val primary = subjects.firstOrNull()

        if (primary == null) {
            val score = (78 - rollPenalty).roundToInt().coerceIn(35, 95)
            return CompositionFeedback(
                score = score,
                isOptimal = absRoll <= 1.8f,
                primaryTip = if (absRoll > 2.5f) "画面略歪，请保持水平" else "自由构图，注意留白与光线",
                secondaryTip = "可点击右下角「AI 点评」获取深度审美建议",
                direction = rollDirection(roll),
                targetAnchorNormalized = 0.5f to 0.5f,
                subjects = subjects,
                rollDegrees = roll,
                pitchDegrees = pitch
            )
        }

        val cx = primary.normalizedBox.centerX()
        val cy = primary.normalizedBox.centerY()
        val anchors = thirdPoints + listOf(0.5f to 0.5f)
        val best = anchors.minByOrNull { (ax, ay) -> hypot(cx - ax, cy - ay) }!!
        val dist = hypot(cx - best.first, cy - best.second)
        val posPenalty = (dist * 50f).coerceAtMost(28f)

        val score = (100f - rollPenalty - posPenalty).roundToInt().coerceIn(30, 99)
        var dir = GuideDirection.NONE
        val tip = when {
            absRoll > 2.8f -> {
                dir = rollDirection(roll)
                "画面偏斜，请调平手机"
            }
            dist > 0.14f -> {
                dir = when {
                    cx - best.first > 0.08f -> GuideDirection.RIGHT
                    best.first - cx > 0.08f -> GuideDirection.LEFT
                    cy - best.second > 0.08f -> GuideDirection.DOWN
                    else -> GuideDirection.UP
                }
                "主体可向推荐绿点方向微调"
            }
            else -> "构图均衡，推荐拍摄"
        }

        return CompositionFeedback(
            score = score,
            isOptimal = score >= 84 && dir == GuideDirection.NONE,
            primaryTip = tip,
            secondaryTip = "主体占比与水平线良好",
            direction = dir,
            targetAnchorNormalized = best,
            subjects = subjects,
            rollDegrees = roll,
            pitchDegrees = pitch
        )
    }

    private fun rollDirection(roll: Float): GuideDirection {
        return when {
            roll > 2.5f -> GuideDirection.LEVEL_CCW
            roll < -2.5f -> GuideDirection.LEVEL_CW
            else -> GuideDirection.NONE
        }
    }

    private fun anchorName(x: Float): String {
        return when {
            x < 0.42f -> "左三分线"
            x > 0.58f -> "右三分线"
            else -> "中心"
        }
    }
}
