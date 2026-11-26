package com.vivohelper.compositioncam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.vivohelper.compositioncam.model.CompositionFeedback
import com.vivohelper.compositioncam.model.GridType
import com.vivohelper.compositioncam.model.SceneMode
import kotlin.math.abs

/**
 * 相机取景框叠加层：绘制构图网格、实时主体框、推荐落点光环与双轴水平仪
 */
@Composable
fun CompositionOverlay(
    feedback: CompositionFeedback,
    gridType: GridType,
    sceneMode: SceneMode,
    modifier: Modifier = Modifier
) {
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

        // 3. 绘制推荐目标锚点圆环
        feedback.targetAnchorNormalized?.let { (ax, ay) ->
            val cx = ax * w
            val cy = ay * h
            val ringColor = if (feedback.isOptimal) Color(0xFF4ADE80) else Color(0xFFFACC15)
            drawCircle(
                color = ringColor.copy(alpha = 0.75f),
                radius = 22f,
                center = Offset(cx, cy),
                style = Stroke(
                    width = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )
            )
            drawCircle(
                color = ringColor,
                radius = 5f,
                center = Offset(cx, cy)
            )
        }

        // 4. 绘制屏幕中心水平仪（风景建筑、人像、日常或美食非俯拍时显示）
        drawHorizonLevel(
            rollDegrees = feedback.rollDegrees,
            pitchDegrees = feedback.pitchDegrees,
            sceneMode = sceneMode,
            w = w,
            h = h
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

private fun DrawScope.drawHorizonLevel(
    rollDegrees: Float,
    pitchDegrees: Float,
    sceneMode: SceneMode,
    w: Float,
    h: Float
) {
    val cx = w / 2f
    val cy = h / 2f

    // 美食模式下如果手机接近平放俯拍，绘制同心圆水平校准器
    if (sceneMode == SceneMode.FOOD && pitchDegrees < 20f) {
        val isFlat = pitchDegrees < 6f
        val color = if (isFlat) Color(0xFF4ADE80) else Color(0xFFFACC15)
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = 28f,
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
    val levelColor = if (isLevel) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.78f)

    // 固定水平基准刻度
    drawLine(Color.White.copy(alpha = 0.35f), Offset(cx - 75f, cy), Offset(cx - 42f, cy), 2f)
    drawLine(Color.White.copy(alpha = 0.35f), Offset(cx + 42f, cy), Offset(cx + 75f, cy), 2f)

    // 随手机旋转的动态水平线
    rotate(degrees = -rollDegrees, pivot = Offset(cx, cy)) {
        drawLine(levelColor, Offset(cx - 40f, cy), Offset(cx + 40f, cy), if (isLevel) 4f else 2.5f)
    }
}
