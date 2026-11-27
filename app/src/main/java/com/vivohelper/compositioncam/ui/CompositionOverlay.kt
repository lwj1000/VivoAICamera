package com.vivohelper.compositioncam.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.vivohelper.compositioncam.model.CompositionFeedback
import com.vivohelper.compositioncam.model.GridType
import com.vivohelper.compositioncam.model.GuideDirection
import com.vivohelper.compositioncam.model.SceneMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 相机取景框叠加层：绘制构图网格、实时主体框、主体->目标引导连线、动态方向箭头与双轴角度标尺
 */
@Composable
fun CompositionOverlay(
    feedback: CompositionFeedback,
    gridType: GridType,
    sceneMode: SceneMode,
    modifier: Modifier = Modifier
) {
    // 呼吸脉冲动画，用于强化方向引导箭头与推荐锚点
    val infiniteTransition = rememberInfiniteTransition(label = "guide_pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_progress"
    )
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. 绘制构图辅助网格
        drawGrid(gridType, w, h)

        // 2. 绘制端侧检测到的主体框
        val boxColor = if (feedback.isOptimal) Color(0xFF4ADE80) else Color(0xFF38BDF8)
        feedback.subjects.forEachIndexed { index, subject ->
            val rect = subject.normalizedBox
            val left = rect.left * w
            val top = rect.top * h
            val boxW = rect.width() * w
            val boxH = rect.height() * h
            val strokeW = if (index == 0) 4f else 2f
            val alpha = if (index == 0) 0.9f else 0.45f

            drawCornerBox(
                left = left,
                top = top,
                width = boxW,
                height = boxH,
                color = boxColor.copy(alpha = alpha),
                strokeWidth = strokeW
            )
        }

        // 3. 绘制推荐目标锚点圆环，以及从主主体到目标点的空间引导箭线
        val primarySubject = feedback.subjects.firstOrNull()
        feedback.targetAnchorNormalized?.let { (ax, ay) ->
            val targetX = ax * w
            val targetY = ay * h
            val ringColor = if (feedback.isOptimal) Color(0xFF4ADE80) else Color(0xFFFACC15)

            drawCircle(
                color = ringColor.copy(alpha = 0.78f),
                radius = 22f + pulseProgress * 4f,
                center = Offset(targetX, targetY),
                style = Stroke(
                    width = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )
            )
            drawCircle(
                color = ringColor,
                radius = 5.5f,
                center = Offset(targetX, targetY)
            )

            // 当存在主主体且尚未对齐目标点时，绘制主体中心指向目标锚点的引导连线与箭头
            if (primarySubject != null && !feedback.isOptimal) {
                val sx = primarySubject.normalizedBox.centerX() * w
                val sy = primarySubject.normalizedBox.centerY() * h
                val dist = hypot(targetX - sx, targetY - sy)
                if (dist > 48f) {
                    drawArrowLine(
                        start = Offset(sx, sy),
                        end = Offset(targetX, targetY),
                        color = Color(0xFFFACC15).copy(alpha = 0.85f)
                    )
                }
            }
        }

        // 4. 绘制屏幕中心双轴水平仪
        drawHorizonLevel(
            rollDegrees = feedback.rollDegrees,
            pitchDegrees = feedback.pitchDegrees,
            sceneMode = sceneMode,
            w = w,
            h = h,
            textMeasurer = textMeasurer
        )

        // 5. 绘制取景框内醒目的方向/角度引导指示器
        if (feedback.direction != GuideDirection.NONE) {
            drawDirectionGuide(
                direction = feedback.direction,
                rollDegrees = feedback.rollDegrees,
                pulse = pulseProgress,
                w = w,
                h = h,
                textMeasurer = textMeasurer
            )
        }

        // 6. 绘制右侧边缘实时俯仰角度标尺（引导拍摄角度）
        drawPitchAngleGauge(
            pitchDegrees = feedback.pitchDegrees,
            sceneMode = sceneMode,
            w = w,
            h = h,
            textMeasurer = textMeasurer
        )
    }
}

private fun DrawScope.drawGrid(gridType: GridType, w: Float, h: Float) {
    val lineColor = Color.White.copy(alpha = 0.32f)
    val stroke = 1.5f

    when (gridType) {
        GridType.RULE_OF_THIRDS -> {
            val x1 = w / 3f
            val x2 = w * 2f / 3f
            val y1 = h / 3f
            val y2 = h * 2f / 3f
            drawLine(lineColor, Offset(x1, 0f), Offset(x1, h), stroke)
            drawLine(lineColor, Offset(x2, 0f), Offset(x2, h), stroke)
            drawLine(lineColor, Offset(0f, y1), Offset(w, y1), stroke)
            drawLine(lineColor, Offset(0f, y2), Offset(w, y2), stroke)

            // 标记四个三分法黄金交点
            val dotColor = Color.White.copy(alpha = 0.55f)
            listOf(x1 to y1, x2 to y1, x1 to y2, x2 to y2).forEach { (dx, dy) ->
                drawCircle(dotColor, radius = 4f, center = Offset(dx, dy))
            }
        }

        GridType.GOLDEN_SPIRAL -> {
            // 黄金分割比例线（0.382 / 0.618）
            val x1 = w * 0.382f
            val x2 = w * 0.618f
            val y1 = h * 0.382f
            val y2 = h * 0.618f
            drawLine(lineColor, Offset(x1, 0f), Offset(x1, h), stroke)
            drawLine(lineColor, Offset(x2, 0f), Offset(x2, h), stroke)
            drawLine(lineColor, Offset(0f, y1), Offset(w, y1), stroke)
            drawLine(lineColor, Offset(0f, y2), Offset(w, y2), stroke)
        }

        GridType.SYMMETRY -> {
            val cx = w / 2f
            val cy = h / 2f
            val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
            drawLine(lineColor, Offset(cx, 0f), Offset(cx, h), stroke, pathEffect = dash)
            drawLine(lineColor, Offset(0f, cy), Offset(w, cy), stroke, pathEffect = dash)
            drawLine(lineColor.copy(alpha = 0.2f), Offset(0f, 0f), Offset(w, h), 1f, pathEffect = dash)
            drawLine(lineColor.copy(alpha = 0.2f), Offset(w, 0f), Offset(0f, h), 1f, pathEffect = dash)
        }

        GridType.NONE -> Unit
    }
}

private fun DrawScope.drawCornerBox(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
    strokeWidth: Float
) {
    val cornerLen = minOf(width, height) * 0.22f
    val right = left + width
    val bottom = top + height

    // 柔和半透明边框
    drawRect(
        color = color.copy(alpha = 0.18f),
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = Stroke(width = 1.2f)
    )

    // 四角 L 型强化标线
    drawLine(color, Offset(left, top), Offset(left + cornerLen, top), strokeWidth)
    drawLine(color, Offset(left, top), Offset(left, top + cornerLen), strokeWidth)

    drawLine(color, Offset(right - cornerLen, top), Offset(right, top), strokeWidth)
    drawLine(color, Offset(right, top), Offset(right, top + cornerLen), strokeWidth)

    drawLine(color, Offset(left, bottom - cornerLen), Offset(left, bottom), strokeWidth)
    drawLine(color, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeWidth)

    drawLine(color, Offset(right - cornerLen, bottom), Offset(right, bottom), strokeWidth)
    drawLine(color, Offset(right, bottom - cornerLen), Offset(right, bottom), strokeWidth)
}

/**
 * 绘制从主体中心指向推荐落点的虚线引导箭头
 */
private fun DrawScope.drawArrowLine(
    start: Offset,
    end: Offset,
    color: Color
) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = 3f,
        pathEffect = dash
    )
    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val headLen = 20f
    val spread = Math.toRadians(28.0)
    val p1 = Offset(
        (end.x - headLen * cos(angle - spread)).toFloat(),
        (end.y - headLen * sin(angle - spread)).toFloat()
    )
    val p2 = Offset(
        (end.x - headLen * cos(angle + spread)).toFloat(),
        (end.y - headLen * sin(angle + spread)).toFloat()
    )
    val path = Path().apply {
        moveTo(p1.x, p1.y)
        lineTo(end.x, end.y)
        lineTo(p2.x, p2.y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawHorizonLevel(
    rollDegrees: Float,
    pitchDegrees: Float,
    sceneMode: SceneMode,
    w: Float,
    h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = w / 2f
    val cy = h / 2f

    // 美食模式下如果手机接近平放俯拍，绘制同心圆水平校准器
    if (sceneMode == SceneMode.FOOD && pitchDegrees < 20f) {
        val isFlat = pitchDegrees < 6f
        val color = if (isFlat) Color(0xFF4ADE80) else Color(0xFFFACC15)
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = 30f,
            center = Offset(cx, cy),
            style = Stroke(width = 2.5f)
        )
        val offsetX = (rollDegrees * 1.5f).coerceIn(-24f, 24f)
        val offsetY = (pitchDegrees * 1.2f).coerceIn(-24f, 24f)
        drawCircle(
            color = color.copy(alpha = 0.85f),
            radius = 10f,
            center = Offset(cx + offsetX, cy + offsetY)
        )
        return
    }

    val isLevel = abs(rollDegrees) <= 1.5f
    val levelColor = if (isLevel) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.82f)

    // 固定水平基准刻度
    drawLine(Color.White.copy(alpha = 0.35f), Offset(cx - 78f, cy), Offset(cx - 44f, cy), 2f)
    drawLine(Color.White.copy(alpha = 0.35f), Offset(cx + 44f, cy), Offset(cx + 78f, cy), 2f)

    // 随手机旋转的动态水平线
    rotate(degrees = -rollDegrees, pivot = Offset(cx, cy)) {
        drawLine(levelColor, Offset(cx - 42f, cy), Offset(cx + 42f, cy), if (isLevel) 4.5f else 2.5f)
    }

    // 在中心水平仪下方显示倾斜角度数字
    val angleText = if (isLevel) "水平 0°" else "${String.format("%.1f", abs(rollDegrees))}°"
    val layout = textMeasurer.measure(
        text = angleText,
        style = TextStyle(
            color = levelColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(cx - layout.size.width / 2f, cy + 14f)
    )
}

/**
 * 在取景框对应方位绘制大号呼吸箭头，直观指示相机移动/旋转方向
 */
private fun DrawScope.drawDirectionGuide(
    direction: GuideDirection,
    rollDegrees: Float,
    pulse: Float,
    w: Float,
    h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = w / 2f
    val cy = h / 2f
    val guideColor = Color(0xFFFACC15)
    val shift = pulse * 14f

    when (direction) {
        GuideDirection.LEFT -> {
            drawDoubleChevron(
                center = Offset(w * 0.14f - shift, cy),
                angleDeg = 180f,
                color = guideColor
            )
            drawGuideTag("向左移", Offset(w * 0.14f, cy + 38f), guideColor, textMeasurer)
        }

        GuideDirection.RIGHT -> {
            drawDoubleChevron(
                center = Offset(w * 0.86f + shift, cy),
                angleDeg = 0f,
                color = guideColor
            )
            drawGuideTag("向右移", Offset(w * 0.86f, cy + 38f), guideColor, textMeasurer)
        }

        GuideDirection.UP -> {
            drawDoubleChevron(
                center = Offset(cx, h * 0.24f - shift),
                angleDeg = -90f,
                color = guideColor
            )
            drawGuideTag("抬高镜头", Offset(cx, h * 0.24f + 34f), guideColor, textMeasurer)
        }

        GuideDirection.DOWN -> {
            drawDoubleChevron(
                center = Offset(cx, h * 0.68f + shift),
                angleDeg = 90f,
                color = guideColor
            )
            drawGuideTag("压低镜头", Offset(cx, h * 0.68f - 46f), guideColor, textMeasurer)
        }

        GuideDirection.CLOSER -> {
            // 向内聚拢双箭头（提示走近或拉长焦）
            drawDoubleChevron(Offset(cx - 90f + shift, cy), 0f, guideColor)
            drawDoubleChevron(Offset(cx + 90f - shift, cy), 180f, guideColor)
            drawGuideTag("靠近/放大", Offset(cx, cy + 44f), guideColor, textMeasurer)
        }

        GuideDirection.FURTHER -> {
            // 向外扩散双箭头（提示后退或切超广角）
            drawDoubleChevron(Offset(cx - 70f - shift, cy), 180f, guideColor)
            drawDoubleChevron(Offset(cx + 70f + shift, cy), 0f, guideColor)
            drawGuideTag("后退半步", Offset(cx, cy + 44f), guideColor, textMeasurer)
        }

        GuideDirection.LEVEL_CW -> {
            // 顺时针旋转弧形引导
            drawRotationArcGuide(cx, cy, clockwise = true, rollDegrees = rollDegrees, color = guideColor, textMeasurer = textMeasurer)
        }

        GuideDirection.LEVEL_CCW -> {
            // 逆时针旋转弧形引导
            drawRotationArcGuide(cx, cy, clockwise = false, rollDegrees = rollDegrees, color = guideColor, textMeasurer = textMeasurer)
        }

        GuideDirection.NONE -> Unit
    }
}

/**
 * 绘制双层指向箭头（基准方向为向右 0°，通过旋转适配上下左右）
 */
private fun DrawScope.drawDoubleChevron(
    center: Offset,
    angleDeg: Float,
    color: Color
) {
    rotate(degrees = angleDeg, pivot = center) {
        val offsets = listOf(-10f, 8f)
        offsets.forEachIndexed { idx, dx ->
            val alpha = if (idx == 0) 0.55f else 0.95f
            val path = Path().apply {
                moveTo(center.x + dx - 12f, center.y - 18f)
                lineTo(center.x + dx + 8f, center.y)
                lineTo(center.x + dx - 12f, center.y + 18f)
            }
            drawPath(
                path = path,
                color = color.copy(alpha = alpha),
                style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

private fun DrawScope.drawRotationArcGuide(
    cx: Float,
    cy: Float,
    clockwise: Boolean,
    rollDegrees: Float,
    color: Color,
    textMeasurer: TextMeasurer
) {
    val radius = 105f
    val startAngle = if (clockwise) -35f else -145f
    val sweepAngle = if (clockwise) 70f else -70f
    drawArc(
        color = color.copy(alpha = 0.85f),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(cx - radius, cy - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = 4.5f, cap = StrokeCap.Round)
    )
    val tipText = if (clockwise) "顺时针调平 ${String.format("%.1f", abs(rollDegrees))}°"
    else "逆时针调平 ${String.format("%.1f", abs(rollDegrees))}°"
    drawGuideTag(tipText, Offset(cx, cy - radius - 22f), color, textMeasurer)
}

private fun DrawScope.drawGuideTag(
    text: String,
    center: Offset,
    color: Color,
    textMeasurer: TextMeasurer
) {
    val layout = textMeasurer.measure(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    )
    val padX = 12f
    val padY = 6f
    val bgW = layout.size.width + padX * 2
    val bgH = layout.size.height + padY * 2
    val topLeft = Offset(center.x - bgW / 2f, center.y - bgH / 2f)

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.65f),
        topLeft = topLeft,
        size = Size(bgW, bgH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(topLeft.x + padX, topLeft.y + padY)
    )
}

/**
 * 右侧边栏实时俯仰角度标尺：显示当前仰拍/平视/俯拍角度，并高亮推荐角度区间
 */
private fun DrawScope.drawPitchAngleGauge(
    pitchDegrees: Float,
    sceneMode: SceneMode,
    w: Float,
    h: Float,
    textMeasurer: TextMeasurer
) {
    val barX = w - 26f
    val topY = h * 0.30f
    val bottomY = h * 0.62f
    val barH = bottomY - topY

    // 标尺底轨（0° 俯拍 到 120° 略仰拍）
    drawLine(
        color = Color.White.copy(alpha = 0.28f),
        start = Offset(barX, topY),
        end = Offset(barX, bottomY),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )

    // 根据当前场景高亮推荐角度区间
    val targetRange = when (sceneMode) {
        SceneMode.LANDSCAPE -> 82f..98f // 垂直端平防透视畸变
        SceneMode.FOOD -> 0f..12f       // 90° 俯拍区
        SceneMode.PORTRAIT -> 78f..98f  // 平视或微仰拍
        SceneMode.DAILY -> 70f..100f
    }

    fun pitchToY(p: Float): Float {
        val norm = (p.coerceIn(0f, 120f)) / 120f
        // 0° 俯拍在下方，90°~120° 在上方
        return bottomY - norm * barH
    }

    drawLine(
        color = Color(0xFF4ADE80).copy(alpha = 0.7f),
        start = Offset(barX, pitchToY(targetRange.endInclusive)),
        end = Offset(barX, pitchToY(targetRange.start)),
        strokeWidth = 6f,
        cap = StrokeCap.Round
    )

    // 美食模式额外标出 45° 经典斜拍区
    if (sceneMode == SceneMode.FOOD) {
        drawLine(
            color = Color(0xFF38BDF8).copy(alpha = 0.7f),
            start = Offset(barX, pitchToY(52f)),
            end = Offset(barX, pitchToY(38f)),
            strokeWidth = 6f,
            cap = StrokeCap.Round
        )
    }

    val curY = pitchToY(pitchDegrees)
    val inTarget = pitchDegrees in targetRange || (sceneMode == SceneMode.FOOD && pitchDegrees in 38f..52f)
    val dotColor = if (inTarget) Color(0xFF4ADE80) else Color(0xFFFACC15)

    drawCircle(
        color = dotColor,
        radius = 7f,
        center = Offset(barX, curY)
    )

    val label = when {
        pitchDegrees < 12f -> "俯拍"
        pitchDegrees in 35f..55f -> "45°"
        pitchDegrees in 82f..98f -> "平视"
        pitchDegrees < 82f -> "俯${(90 - pitchDegrees).toInt()}°"
        else -> "仰${(pitchDegrees - 90).toInt()}°"
    }
    val layout = textMeasurer.measure(
        text = label,
        style = TextStyle(
            color = dotColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(barX - layout.size.width - 10f, curY - layout.size.height / 2f)
    )
}
