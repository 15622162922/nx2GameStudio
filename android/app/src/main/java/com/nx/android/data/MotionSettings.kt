package com.nx.android.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

/**
 * 体感：设备转动带动游戏里的六轴传感器（宿主那边 dm_motion_set，王国之泪的弓就是靠它瞄）。
 *
 * 两个开关：
 * - 开不开：默认按设备有没有陀螺仪（没有就别开）
 * - 保留甩动方向：用实测的方向，不用宿主生成的波形（宿主那边 DM_MOTION_RAW）
 *
 * 纯值：env 行与 ctl 行都在这里组好，JVM 上可测。运行前 env 行交给宿主，运行中 ctl 行写进 DM_CTL
 * （宿主每 100 ms 读一次，所以改了立刻生效，不用重启正在跑的游戏）。
 */
data class MotionKnobs(val on: Boolean, val raw: Boolean) {

    /** 启动时交给宿主的 env 行 */
    fun envLines(): Array<String> = arrayOf(
        "DM_MOTION=${if (on) 1 else 0}",
        "DM_MOTION_RAW=${if (raw) 1 else 0}",
    )

    /** 运行中写进 DM_CTL 的行：宿主读到就 setenv，下一次读传感器时就是新的了 */
    fun ctlLines(): Array<String> = envLines().map { "env $it" }.toTypedArray()
}

/**
 * 体感开关：整机一份（不是每个游戏一份）。
 *
 * 默认值按设备有没有陀螺仪定；玩家改过之后以他改的为准。
 */
class MotionSettings(private val prefs: PrefsStore) {

    fun load(hasGyro: Boolean): MotionKnobs = MotionKnobs(
        on = prefs.bool(KEY_ON, hasGyro),
        raw = prefs.bool(KEY_RAW, false),
    )

    fun save(k: MotionKnobs) {
        prefs.put(KEY_ON, k.on)
        prefs.put(KEY_RAW, k.raw)
    }

    companion object {
        const val KEY_ON = "motion.on"
        const val KEY_RAW = "motion.raw"
    }
}

/** 这台设备有没有陀螺仪（体感瞄准要它） */
fun hasGyroscope(context: Context): Boolean =
    (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
        ?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
