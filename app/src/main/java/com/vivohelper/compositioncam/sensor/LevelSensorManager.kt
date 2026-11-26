package com.vivohelper.compositioncam.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 手机姿态传感器管理器
 * 用于计算竖屏拍照时的横滚角（左右歪斜）与俯仰角（仰拍/俯拍角度）
 */
class LevelSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // 使用低通滤波平滑角度数值，避免取景框数字频繁跳动
    private var smoothedRoll = 0f
    private var smoothedPitch = 90f
    private val alpha = 0.18f

    var onAngleChanged: ((rollDegrees: Float, pitchDegrees: Float) -> Unit)? = null

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        // 竖屏状态下：ax 为左右倾斜分量，ay 为竖直重力分量，az 为前后俯仰分量
        // 横滚角 roll：0° 表示手机左右完全水平
        val rawRoll = Math.toDegrees(atan2(ax.toDouble(), ay.toDouble())).toFloat()
        // 俯仰角 pitch：0° 表示手机屏幕朝上完全放平（俯拍桌面），90° 表示手机垂直地面直立拍照
        val horizontalMag = sqrt((ax * ax + ay * ay).toDouble())
        val rawPitch = Math.toDegrees(atan2(horizontalMag, az.toDouble())).toFloat()

        smoothedRoll = smoothedRoll + alpha * (rawRoll - smoothedRoll)
        smoothedPitch = smoothedPitch + alpha * (rawPitch - smoothedPitch)

        onAngleChanged?.invoke(smoothedRoll, smoothedPitch)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 无需处理精度变化
    }
}
