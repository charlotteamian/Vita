package com.vita.healthtracker.data.sensor

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.vita.healthtracker.data.local.dao.DailyHealthDao
import com.vita.healthtracker.data.local.entity.DailyHealthSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * 把「手机自己」做成一个独立的数据源, 通过手机内置的步数传感器 (TYPE_STEP_COUNTER) 记步。
 *
 * 设计要点:
 *  - TYPE_STEP_COUNTER 返回的是「开机以来的累计步数」。这里用持久化基线 (SharedPreferences) 换算出
 *    「今天」的步数, 即使 app 重启 / 手机重启也不会把当天步数清零。
 *  - 手机重启会让计步器归零, 通过「raw 比上次小」来识别并继续累加。
 *  - 仅当某一天还没有任何其它来源 (手表 / Health Connect / 佳明 / Apple) 写入数据时, 才由手机来记这天,
 *    避免覆盖更准确的手表数据。已经是手机记的日子则持续更新。
 *  - 同时按步数粗略估算距离和活跃卡路里, 没连手表时今日/统计页也能看到合理数字。
 */
class StepSensorManager(
    context: Context,
    private val dailyDao: DailyHealthDao,
) {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 传感器是否可用 */
    val isAvailable: Boolean get() = stepSensor != null

    @Volatile private var listening = false

    /** 当前是否已注册手机步数监听。 */
    val isListening: Boolean get() = listening

    // 限流: 基线每次事件都持久化, 但 DB 只在步数有明显增长时才写, 避免走路时高频写库。
    @Volatile private var lastWrittenSteps = -1L
    @Volatile private var lastWrittenDate = ""

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            val raw = event?.values?.firstOrNull() ?: return
            val today = LocalDate.now().format(fmt)

            // 用持久化基线把「累计步数」换算成「今天步数」(同步执行, 跑在传感器回调线程)
            val storedDate = prefs.getString(KEY_DATE, null)
            var accumulated = prefs.getLong(KEY_ACCUMULATED, 0L)
            val lastRaw = prefs.getFloat(KEY_LAST_RAW, -1f)

            if (lastRaw < 0f) {
                // 首次启用时没有午夜基线。把当前计步器读数作为今日估计值先落库,
                // 否则用户点了「开启手机同步」后会一直看到 0, 直到下一次走动才有数据。
                accumulated = raw.toLong().coerceAtLeast(0L)
            } else if (storedDate != today) {
                // 新的一天: 以当天第一次读数为基线, 后续用增量累计。
                accumulated = 0L
            } else {
                // 同一天: 累加增量。raw 比上次小 → 手机重启过 (计步器归零), 把 raw 本身当作增量。
                val delta = if (raw >= lastRaw) (raw - lastRaw) else raw
                accumulated += delta.toLong()
            }

            prefs.edit()
                .putString(KEY_DATE, today)
                .putLong(KEY_ACCUMULATED, accumulated)
                .putFloat(KEY_LAST_RAW, raw)
                .apply()

            val stepsToday = accumulated.coerceAtLeast(0L)

            // 限流: 同一天内步数增长不足阈值就先不写库 (基线已持久化, 不会丢数据)
            if (today == lastWrittenDate && stepsToday - lastWrittenSteps < STEP_WRITE_THRESHOLD) return
            lastWrittenDate = today
            lastWrittenSteps = stepsToday
            scope.launch { writeStepsToday(today, stepsToday) }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private suspend fun writeStepsToday(date: String, steps: Long) = writeMutex.withLock {
        if (steps <= 0L) return@withLock // 还没走动, 不创建空行
        val distance = steps * STRIDE_METERS
        val activeCal = steps * KCAL_PER_STEP
        val existing = dailyDao.forDate(date)
        when {
            // 这天还没有任何来源的数据 → 由手机来记
            existing == null -> dailyDao.upsert(
                DailyHealthSnapshot(
                    date = date,
                    steps = steps,
                    distanceMeters = distance,
                    activeCalories = activeCal,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    source = SOURCE_PHONE,
                )
            )
            // 这天本来就是手机记的 → 更新手机自己的数字 (步数只会随当天增长)
            existing.source == SOURCE_PHONE -> dailyDao.upsert(
                existing.copy(
                    steps = max(existing.steps, steps),
                    distanceMeters = distance,
                    activeCalories = activeCal,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
            )
            // 否则: 已有手表 / Health Connect / 佳明等更准确来源, 手机不覆盖
            else -> Unit
        }
    }

    fun startListening(): Boolean {
        val sensor = stepSensor ?: return false
        return runCatching {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            listening = true
            true
        }.getOrElse {
            listening = false
            false
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(listener)
        listening = false
    }

    companion object {
        const val SOURCE_PHONE = "phone"

        private const val PREFS_NAME = "vita_step_sensor"
        private const val KEY_DATE = "baseline_date"
        private const val KEY_ACCUMULATED = "accumulated_steps"
        private const val KEY_LAST_RAW = "last_raw_steps"

        /** 估算用平均步幅 (米/步) */
        private const val STRIDE_METERS = 0.72
        /** 估算用每步活跃卡路里 (kcal/步) */
        private const val KCAL_PER_STEP = 0.04
        /** DB 写入限流阈值: 步数每增长这么多才落库一次 */
        private const val STEP_WRITE_THRESHOLD = 10L
    }
}
