package com.vivohelper.compositioncam.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vivohelper.compositioncam.analyzer.CameraFrameAnalyzer
import com.vivohelper.compositioncam.analyzer.CompositionEngine
import com.vivohelper.compositioncam.model.AiConfig
import com.vivohelper.compositioncam.model.CompositionFeedback
import com.vivohelper.compositioncam.model.DetectedSubject
import com.vivohelper.compositioncam.model.GridType
import com.vivohelper.compositioncam.model.GuideDirection
import com.vivohelper.compositioncam.model.SceneMode
import com.vivohelper.compositioncam.network.VisionAiAdvisor
import com.vivohelper.compositioncam.sensor.LevelSensorManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 主相机取景与 AI 构图辅助界面
 */
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        PermissionScreen(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        return
    }

    // 相机与构图核心状态
    var sceneMode by remember { mutableStateOf(SceneMode.PORTRAIT) }
    var gridType by remember { mutableStateOf(GridType.RULE_OF_THIRDS) }
    var useFrontCamera by remember { mutableStateOf(false) }

    var detectedSubjects by remember { mutableStateOf<List<DetectedSubject>>(emptyList()) }
    var rollDegrees by remember { mutableFloatStateOf(0f) }
    var pitchDegrees by remember { mutableFloatStateOf(90f) }

    var currentZoom by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(10f) }

    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var lastSavedUri by remember { mutableStateOf<Uri?>(null) }

    // AI 大模型点评相关状态
    var aiConfig by remember { mutableStateOf(VisionAiAdvisor.loadConfig(context)) }
    var showAiSettings by remember { mutableStateOf(false) }
    var isAiAnalyzing by remember { mutableStateOf(false) }
    var aiAdviceText by remember { mutableStateOf<String?>(null) }

    // 传感器监听
    val levelManager = remember { LevelSensorManager(context) }
    DisposableEffect(Unit) {
        levelManager.onAngleChanged = { r, p ->
            rollDegrees = r
            pitchDegrees = p
        }
        levelManager.start()
        onDispose {
            levelManager.stop()
        }
    }

    // 实时计算构图评估反馈
    val feedback: CompositionFeedback = remember(sceneMode, detectedSubjects, rollDegrees, pitchDegrees) {
        CompositionEngine.evaluate(sceneMode, detectedSubjects, rollDegrees, pitchDegrees)
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val frameAnalyzer = remember {
        CameraFrameAnalyzer(
            getSceneMode = { sceneMode },
            isFrontCamera = { useFrontCamera },
            onSubjectsDetected = { list -> detectedSubjects = list }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            frameAnalyzer.close()
            analysisExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        // 1. CameraX 实时取景预览
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewViewRef = this
                }
            },
            update = { view ->
                previewViewRef = view
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = view.surfaceProvider
                    }
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(analysisExecutor, frameAnalyzer)
                        }

                    val selector = if (useFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            imageCapture,
                            imageAnalysis
                        )
                        cameraRef = camera
                        imageCaptureRef = imageCapture

                        // 读取设备硬件支持的变焦范围（vivo X200 Ultra 支持超广角 ~ 长焦）
                        val zoomState = camera.cameraInfo.zoomState.value
                        if (zoomState != null) {
                            minZoom = zoomState.minZoomRatio
                            maxZoom = zoomState.maxZoomRatio
                            currentZoom = zoomState.zoomRatio
                        }

                        // 点击屏幕自动对焦与测光
                        view.setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_UP) {
                                val factory = view.meteringPointFactory
                                val point = factory.createPoint(event.x, event.y)
                                val action = FocusMeteringAction.Builder(point).build()
                                camera.cameraControl.startFocusAndMetering(action)
                            }
                            true
                        }
                    } catch (_: Exception) {
                        // 忽略相机绑定异常
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )

        // 2. 构图辅助网格、主体框、水平仪叠加层
        CompositionOverlay(
            feedback = feedback,
            gridType = gridType,
            sceneMode = sceneMode,
            modifier = Modifier.fillMaxSize()
        )

        // 3. 顶部工具栏：评分胶囊 + 网格切换 + AI 设置
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 构图评分徽章
                ScoreBadge(score = feedback.score, isOptimal = feedback.isOptimal)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 网格切换按钮
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.clickable {
                            val all = GridType.entries
                            gridType = all[(all.indexOf(gridType) + 1) % all.size]
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = "切换网格",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = gridType.label, color = Color.White, fontSize = 12.sp)
                        }
                    }

                    // AI 设置按钮
                    IconButton(
                        onClick = { showAiSettings = true },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "AI 设置",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 实时方向与构图引导提示条
            GuideBanner(feedback = feedback)
        }

        // 4. 底部控制面板：AI 点评结果卡 + 焦段切换 + 场景模式 + 快门栏
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(top = 10.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // AI 老师深度点评弹窗卡片
            AnimatedVisibility(visible = aiAdviceText != null || isAiAnalyzing) {
                Surface(
                    color = Color(0xFF1E293B).copy(alpha = 0.94f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFFFACC15),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI 摄影老师建议",
                                    color = Color(0xFFFACC15),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { aiAdviceText = null },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (isAiAnalyzing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = Color(0xFFFACC15),
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "正在分析取景画面...",
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Text(
                                text = aiAdviceText.orEmpty(),
                                color = Color.White,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }

            // 变焦倍率快捷栏（仅在后置多摄时显示可用倍率）
            if (!useFrontCamera) {
                ZoomSelectorRow(
                    minZoom = minZoom,
                    maxZoom = maxZoom,
                    currentZoom = currentZoom,
                    onSelectZoom = { target ->
                        cameraRef?.cameraControl?.setZoomRatio(target)
                        currentZoom = target
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 四大场景模式切换条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                SceneMode.entries.forEach { mode ->
                    val selected = mode == sceneMode
                    Surface(
                        color = if (selected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.clickable { sceneMode = mode }
                    ) {
                        Text(
                            text = mode.label,
                            color = if (selected) Color.Black else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 底部快门与功能栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左下：打开最近拍摄的照片
                IconButton(
                    onClick = {
                        val intent = if (lastSavedUri != null) {
                            Intent(Intent.ACTION_VIEW, lastSavedUri).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        } else {
                            Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "相册",
                        tint = Color.White
                    )
                }

                // 中间：快门按钮（构图达标时外圈变绿）
                val shutterRing = if (feedback.isOptimal) Color(0xFF4ADE80) else Color.White
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(4.dp, shutterRing, CircleShape)
                        .clickable {
                            takePhotoAndSave(context, imageCaptureRef) { uri ->
                                lastSavedUri = uri
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(shutterRing)
                    )
                }

                // 右下：AI 深度点评 + 切换前后摄
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFFFACC15),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.clickable {
                            val previewBitmap = previewViewRef?.bitmap
                            if (previewBitmap == null) {
                                Toast.makeText(context, "画面尚未就绪", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            isAiAnalyzing = true
                            aiAdviceText = null
                            scope.launch {
                                val result = VisionAiAdvisor.analyzeFrame(previewBitmap, sceneMode, aiConfig)
                                isAiAnalyzing = false
                                result.onSuccess { text ->
                                    aiAdviceText = text
                                }.onFailure { err ->
                                    aiAdviceText = err.message ?: "请求异常，请检查网络或 API 配置"
                                }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI点评",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "AI点评",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = { useFrontCamera = !useFrontCamera },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "切换摄像头",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    if (showAiSettings) {
        AiConfigDialog(
            initial = aiConfig,
            onDismiss = { showAiSettings = false },
            onSave = { updated ->
                VisionAiAdvisor.saveConfig(context, updated)
                aiConfig = updated
                showAiSettings = false
                Toast.makeText(context, "AI 配置已保存", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun ScoreBadge(score: Int, isOptimal: Boolean) {
    val bgColor = when {
        isOptimal -> Color(0xFF16A34A).copy(alpha = 0.85f)
        score >= 75 -> Color(0xFF0284C7).copy(alpha = 0.82f)
        else -> Color(0xFFD97706).copy(alpha = 0.85f)
    }
    Surface(color = bgColor, shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOptimal) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = "构图 $score 分",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GuideBanner(feedback: CompositionFeedback) {
    val icon = when (feedback.direction) {
        GuideDirection.LEFT -> Icons.AutoMirrored.Filled.ArrowBack
        GuideDirection.RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
        GuideDirection.UP -> Icons.Default.ArrowUpward
        GuideDirection.DOWN -> Icons.Default.ArrowDownward
        GuideDirection.CLOSER -> Icons.Default.ZoomIn
        GuideDirection.FURTHER -> Icons.Default.ZoomOut
        GuideDirection.LEVEL_CW -> Icons.Default.RotateRight
        GuideDirection.LEVEL_CCW -> Icons.Default.RotateLeft
        GuideDirection.NONE -> if (feedback.isOptimal) Icons.Default.CheckCircle else null
    }

    Surface(
        color = Color.Black.copy(alpha = 0.52f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (feedback.isOptimal) Color(0xFF4ADE80) else Color(0xFFFACC15),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column {
                Text(
                    text = feedback.primaryTip,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                if (feedback.secondaryTip.isNotBlank()) {
                    Text(
                        text = feedback.secondaryTip,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomSelectorRow(
    minZoom: Float,
    maxZoom: Float,
    currentZoom: Float,
    onSelectZoom: (Float) -> Unit
) {
    val presets = buildList {
        if (minZoom <= 0.65f) add(0.6f)
        add(1.0f)
        if (maxZoom >= 2.0f) add(2.0f)
        if (maxZoom >= 3.7f) add(3.7f)
        if (maxZoom >= 5.0f) add(5.0f)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { ratio ->
            val active = kotlin.math.abs(currentZoom - ratio) < 0.15f
            Surface(
                color = if (active) Color(0xFFFACC15) else Color.White.copy(alpha = 0.14f),
                shape = CircleShape,
                modifier = Modifier.clickable { onSelectZoom(ratio) }
            ) {
                val label = if (ratio == 1.0f || ratio == 2.0f || ratio == 5.0f) {
                    "${ratio.toInt()}x"
                } else {
                    "${ratio}x"
                }
                Text(
                    text = label,
                    color = if (active) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun AiConfigDialog(
    initial: AiConfig,
    onDismiss: () -> Unit,
    onSave: (AiConfig) -> Unit
) {
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var modelName by remember { mutableStateOf(initial.modelName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "多模态 AI 点评设置（可选）", fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "不填 API Key 也能正常使用全部端侧实时构图功能。如需「AI 点评」，可填智谱免费视觉模型 glm-4v-flash 或通义千问 VL 接口。",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API 地址 (Chat Completions)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型名称") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(AiConfig(baseUrl, apiKey, modelName)) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "需要相机权限以提供实时 AI 构图引导",
                color = Color.White,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
            ) {
                Text("授予相机权限", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun takePhotoAndSave(
    context: Context,
    imageCapture: ImageCapture?,
    onSaved: (Uri) -> Unit
) {
    if (imageCapture == null) return

    val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_$name")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/构图助手")
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.let(onSaved)
                Toast.makeText(context, "已保存到相册「Pictures/构图助手」", Toast.LENGTH_SHORT).show()
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(context, "拍照失败：${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
