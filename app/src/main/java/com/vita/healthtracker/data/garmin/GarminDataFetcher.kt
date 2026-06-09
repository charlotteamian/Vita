package com.vita.healthtracker.data.garmin

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class GarminDataFetcher(private val authClient: GarminAuthClient) {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val garminDateTimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val client = authClient.getClient()

    suspend fun fetchDailySummary(date: LocalDate): GarminDailySummary? = withContext(Dispatchers.IO) {
        val dateStr = date.format(fmt)
        val displayName = authClient.requireDisplayName()
        val url = authClient.connectApiUrl("usersummary-service/usersummary/daily")
            .toHttpUrl()
            .newBuilder()
            .addPathSegment(displayName)
            .addQueryParameter("calendarDate", dateStr)
            .build()
        val req = Request.Builder().url(url).build()
        val json = client.newCall(req).executeNullableJson() ?: return@withContext null
        if (json.optBoolean("privacyProtected", false)) {
            throw GarminApiException(401, "Garmin 返回 privacyProtected, 请重新登录")
        }
        val hrFallback = parseDailyHeartRate(date, displayName)
        val averageHr = json.optPositiveInt("averageHeartRate")
            ?: json.optPositiveInt("averageHR")
            ?: json.optPositiveInt("avgHeartRate")
            ?: hrFallback?.averageHeartRate
        val restingHr = json.optPositiveInt("restingHeartRate")
            ?: hrFallback?.restingHeartRate
            ?: 0
        GarminDailySummary(
            totalSteps = json.optInt("totalSteps", 0),
            totalDistanceMeters = json.optDouble("totalDistanceMeters", 0.0),
            activeKilocalories = json.optInt("activeKilocalories", 0),
            totalKilocalories = json.optInt("totalKilocalories", 0),
            activeMinutes = json.optLong("moderateIntensityMinutes", 0L) + json.optLong("vigorousIntensityMinutes", 0L),
            averageHeartRate = averageHr,
            restingHeartRate = restingHr,
            floorsAscended = json.optDouble("floorsAscendedInMeters", 0.0) / 3.0, // approx 3m per floor
            // ─── 进阶指标 (字段名各版本略有差异, 做多候选名兜底; 拿不到就留空, 不影响其它数据) ───
            avgStress = json.optInt("averageStressLevel", -1).takeIf { it > 0 },
            maxStress = json.optInt("maxStressLevel", -1).takeIf { it > 0 },
            bodyBatteryHigh = json.firstPositiveInt("bodyBatteryHighestValue", "bodyBatteryMostRecentValue"),
            bodyBatteryLow = json.firstPositiveInt("bodyBatteryLowestValue"),
            avgSpo2 = json.firstPositiveInt("averageSpo2", "averageSpo2Value", "avgSpo2", "averageMonitoringEnvironmentSpo2"),
            avgRespiration = json.firstPositiveDouble("avgWakingRespirationValue", "averageRespirationValue", "avgRespirationValue"),
        ).takeIf { it.hasData }
    }

    /** 心率变异性 (HRV), 单独接口, 拿不到不报错。 */
    suspend fun fetchHrv(date: LocalDate): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val dateStr = date.format(fmt)
            val url = authClient.connectApiUrl("hrv-service/hrv/$dateStr")
            val json = client.newCall(Request.Builder().url(url).build()).executeNullableJson()
                ?: return@runCatching null
            json.optJSONObject("hrvSummary")?.firstPositiveInt("lastNightAvg", "weeklyAvg")
        }.getOrNull()
    }

    suspend fun fetchSleep(date: LocalDate): GarminSleep? = withContext(Dispatchers.IO) {
        val dateStr = date.format(fmt)
        val url = authClient.connectApiUrl("wellness-service/wellness/dailySleepData/$dateStr")
        val req = Request.Builder().url(url).build()
        val json = client.newCall(req).executeNullableJson() ?: return@withContext null
        val dto = json.optJSONObject("dailySleepDTO") ?: return@withContext null

        val start = dto.optLong("sleepStartTimestampGMT", 0L)
        val end = dto.optLong("sleepEndTimestampGMT", 0L)
        val deep = dto.optLong("deepSleepSeconds", 0L) / 60L
        val light = dto.optLong("lightSleepSeconds", 0L) / 60L
        val rem = dto.optLong("remSleepSeconds", 0L) / 60L
        val awake = dto.optLong("awakeSleepSeconds", 0L) / 60L
        val totalSeconds = dto.optLong("sleepTimeSeconds", 0L)
        val total = when {
            totalSeconds > 0L -> totalSeconds / 60L
            end > start && start > 0L -> (end - start) / 60_000L
            else -> deep + light + rem
        }
        val score = dto.optJSONObject("sleepScores")?.optJSONObject("overall")?.optInt("value", 0)?.takeIf { it > 0 }
            ?: dto.optInt("sleepScore", 0).takeIf { it > 0 }

        GarminSleep(
            startEpochMs = start,
            endEpochMs = end,
            totalMinutes = total,
            deepMinutes = deep,
            lightMinutes = light,
            remMinutes = rem,
            awakeMinutes = awake,
            sleepScore = score,
        ).takeIf { it.totalMinutes > 0L && it.startEpochMs > 0L }
    }

    suspend fun fetchActivities(date: LocalDate): List<GarminActivity> = withContext(Dispatchers.IO) {
        val dateStr = date.format(fmt)
        val filtered = requestActivities(
            startDate = dateStr,
            endDate = dateStr,
            limit = 100,
        ).filter { it.occursOn(date) }

        // 有些刚上传的活动在 date 参数索引里会短暂查不到, 但最近活动列表已经能看到。
        // 对近两周日期加一个无日期条件的兜底查询, 防止用户刚运动完同步时显示「无新增数据」。
        if (filtered.isNotEmpty() || date.isBefore(LocalDate.now().minusDays(14))) {
            filtered
        } else {
            requestActivities(startDate = null, endDate = null, limit = 50)
                .filter { it.occursOn(date) }
        }
    }

    suspend fun fetchBodyBattery(date: LocalDate): List<GarminBodyBatteryPoint> = withContext(Dispatchers.IO) {
        val dateStr = date.format(fmt)
        val displayName = authClient.requireDisplayName()
        val urls = listOf(
            authClient.connectApiUrl("wellness-service/wellness/bodyBattery/reports/daily/$dateStr"),
            authClient.connectApiUrl("wellness-service/wellness/bodyBattery/reports/daily")
                .toHttpUrl()
                .newBuilder()
                .addPathSegment(displayName)
                .addQueryParameter("date", dateStr)
                .build()
                .toString(),
        )
        urls.firstNotNullOfOrNull { url ->
            runCatching {
                val json = client.newCall(Request.Builder().url(url).build()).executeNullableJson()
                    ?: return@runCatching emptyList()
                json.readBodyBatteryPoints()
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }.orEmpty()
    }

    suspend fun fetchDailyRawPayloads(
        date: LocalDate,
        skipCategoryKeys: Set<String> = emptySet(),
    ): List<GarminRawPayload> = withContext(Dispatchers.IO) {
        val dateStr = date.format(fmt)
        val displayName = authClient.requireDisplayName()
        fetchRawPayloads(rawDailyEndpoints(dateStr, displayName).filterNot { it.key in skipCategoryKeys })
    }

    suspend fun fetchActivityRawPayloads(
        activity: GarminActivity,
        skipCategoryKeys: Set<String> = emptySet(),
    ): List<GarminRawPayload> = withContext(Dispatchers.IO) {
        fetchRawPayloads(rawActivityEndpoints(activity.id, activity.name).filterNot { it.key in skipCategoryKeys })
    }

    private suspend fun fetchRawPayloads(endpoints: List<RawEndpoint>): List<GarminRawPayload> = coroutineScope {
        val gate = Semaphore(4)
        endpoints.map { endpoint ->
            async {
                gate.withPermit { fetchRawPayload(endpoint) }
            }
        }.awaitAll().filterNotNull()
    }

    private fun fetchRawPayload(endpoint: RawEndpoint): GarminRawPayload? =
        runCatching {
            val payload = requestRawJson(endpoint.path, endpoint.params) ?: return@runCatching null
            GarminRawPayload(
                categoryKey = endpoint.key,
                categoryLabel = endpoint.label,
                endpointPath = endpoint.path,
                payloadJson = payload,
            )
        }.getOrElse { e ->
            val code = (e as? GarminApiException)?.code
            if (code == 401 || code == 403 || code == 429) throw e
            null
        }

    suspend fun fetchMenstrualData(date: LocalDate): GarminCycle? = withContext(Dispatchers.IO) {
        val dateStr = date.format(fmt)
        val url = authClient.connectApiUrl("periodichealth-service/menstrualcycle/calendar/$dateStr")
        val req = Request.Builder().url(url).build()
        val json = client.newCall(req).executeNullableJson() ?: return@withContext null

        val isPeriodDay = json.optBoolean("isPeriodDay", false)
        val flow = json.optInt("flowAmount", 0)
        
        if (isPeriodDay) GarminCycle(isPeriodDay, flow) else null
    }

    private fun okhttp3.Call.executeNullableJson(): JSONObject? {
        execute().use { response ->
            if (response.code == 404 || response.code == 204) return null
            if (!response.isSuccessful) throw GarminApiException(response.code, response.errorMessage())
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) return null
            return JSONObject(text)
        }
    }

    private fun okhttp3.Call.executeNullableJsonArray(): JSONArray? {
        execute().use { response ->
            if (response.code == 404 || response.code == 204) return null
            if (!response.isSuccessful) throw GarminApiException(response.code, response.errorMessage())
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) return null
            return JSONArray(text)
        }
    }

    private fun okhttp3.Call.executeNullableJsonText(): String? {
        execute().use { response ->
            if (response.code == 404 || response.code == 204 || response.code == 400) return null
            if (!response.isSuccessful) throw GarminApiException(response.code, response.errorMessage())
            val text = response.body?.string()?.trim().orEmpty()
            if (text.isBlank()) return null
            if (!text.startsWith("{") && !text.startsWith("[")) return null
            if (!text.isMeaningfulJsonPayload()) return null
            return text
        }
    }

    private fun requestRawJson(path: String, params: Map<String, String> = emptyMap()): String? {
        val builder = authClient.connectApiUrl(path)
            .toHttpUrl()
            .newBuilder()
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return client.newCall(Request.Builder().url(builder.build()).build()).executeNullableJsonText()
    }

    private fun String.isMeaningfulJsonPayload(): Boolean = runCatching {
        when {
            startsWith("{") -> JSONObject(this).length() > 0
            startsWith("[") -> JSONArray(this).length() > 0
            else -> false
        }
    }.getOrDefault(false)

    private fun rawDailyEndpoints(dateStr: String, displayName: String): List<RawEndpoint> = listOf(
        RawEndpoint(
            key = "daily_summary",
            label = "日汇总",
            path = "usersummary-service/usersummary/daily/$displayName",
            params = mapOf("calendarDate" to dateStr),
        ),
        RawEndpoint(
            key = "summary_chart",
            label = "步数/活动曲线",
            path = "wellness-service/wellness/dailySummaryChart/$displayName",
            params = mapOf("date" to dateStr),
        ),
        RawEndpoint(
            key = "heart_rate",
            label = "全天心率",
            path = "wellness-service/wellness/dailyHeartRate/$displayName",
            params = mapOf("date" to dateStr),
        ),
        RawEndpoint("stress", "全天压力", "wellness-service/wellness/dailyStress/$dateStr"),
        RawEndpoint(
            key = "body_battery",
            label = "身体电量",
            path = "wellness-service/wellness/bodyBattery/reports/daily",
            params = mapOf("startDate" to dateStr, "endDate" to dateStr),
        ),
        RawEndpoint("body_battery_events", "身体电量事件", "wellness-service/wellness/bodyBattery/events/$dateStr"),
        RawEndpoint(
            key = "sleep",
            label = "睡眠",
            path = "wellness-service/wellness/dailySleepData/$displayName",
            params = mapOf("date" to dateStr, "nonSleepBufferMinutes" to "60"),
        ),
        RawEndpoint("hrv", "心率变异性 HRV", "hrv-service/hrv/$dateStr"),
        RawEndpoint("spo2", "血氧", "wellness-service/wellness/daily/spo2/$dateStr"),
        RawEndpoint("respiration", "呼吸率", "wellness-service/wellness/daily/respiration/$dateStr"),
        RawEndpoint("intensity_minutes", "强度活动时间", "wellness-service/wellness/daily/im/$dateStr"),
        RawEndpoint("floors", "爬楼", "wellness-service/wellness/floorsChartData/daily/$dateStr"),
        RawEndpoint(
            key = "daily_events",
            label = "全天事件",
            path = "wellness-service/wellness/dailyEvents",
            params = mapOf("calendarDate" to dateStr),
        ),
        RawEndpoint("hydration", "饮水", "usersummary-service/usersummary/hydration/daily/$dateStr"),
        RawEndpoint(
            key = "body_composition",
            label = "体重/身体成分",
            path = "weight-service/weight/dayview/$dateStr",
            params = mapOf("includeAll" to "true"),
        ),
        RawEndpoint("lifestyle_logging", "生活记录", "lifestylelogging-service/dailyLog/$dateStr"),
        RawEndpoint("menstrual_dayview", "经期详情", "periodichealth-service/menstrualcycle/dayview/$dateStr"),
        RawEndpoint("max_metrics", "最大运动指标", "metrics-service/metrics/maxmet/daily/$dateStr/$dateStr"),
        RawEndpoint("training_readiness", "训练准备度", "metrics-service/metrics/trainingreadiness/$dateStr"),
        RawEndpoint("training_status", "训练状态", "metrics-service/metrics/trainingstatus/aggregated/$dateStr"),
        RawEndpoint(
            key = "endurance_score",
            label = "耐力分数",
            path = "metrics-service/metrics/endurancescore",
            params = mapOf("calendarDate" to dateStr),
        ),
        RawEndpoint(
            key = "hill_score",
            label = "爬坡分数",
            path = "metrics-service/metrics/hillscore",
            params = mapOf("calendarDate" to dateStr),
        ),
        RawEndpoint("fitness_age", "健身年龄", "fitnessage-service/fitnessage/$dateStr"),
        RawEndpoint("nutrition_food", "饮食日志", "nutrition-service/food/logs/$dateStr"),
        RawEndpoint("nutrition_meals", "餐食", "nutrition-service/meals/$dateStr"),
        RawEndpoint("nutrition_settings", "营养设置", "nutrition-service/settings/$dateStr"),
    )

    private fun rawActivityEndpoints(activityId: String, activityName: String?): List<RawEndpoint> {
        val labelSuffix = activityName?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        return listOf(
            RawEndpoint("activity_summary_$activityId", "运动摘要$labelSuffix", "activity-service/activity/$activityId"),
            RawEndpoint(
                key = "activity_details_$activityId",
                label = "运动图表/轨迹$labelSuffix",
                path = "activity-service/activity/$activityId/details",
                params = mapOf("maxChartSize" to "2000", "maxPolylineSize" to "4000"),
            ),
            RawEndpoint("activity_splits_$activityId", "运动分段$labelSuffix", "activity-service/activity/$activityId/splits"),
            RawEndpoint("activity_typed_splits_$activityId", "运动类型分段$labelSuffix", "activity-service/activity/$activityId/typedsplits"),
            RawEndpoint("activity_split_summaries_$activityId", "分段汇总$labelSuffix", "activity-service/activity/$activityId/split_summaries"),
            RawEndpoint("activity_weather_$activityId", "运动天气$labelSuffix", "activity-service/activity/$activityId/weather"),
            RawEndpoint("activity_hr_zones_$activityId", "心率区间$labelSuffix", "activity-service/activity/$activityId/hrTimeInZones"),
            RawEndpoint("activity_power_zones_$activityId", "功率区间$labelSuffix", "activity-service/activity/$activityId/powerTimeInZones"),
            RawEndpoint("activity_sets_$activityId", "训练组$labelSuffix", "activity-service/activity/$activityId/exerciseSets"),
        )
    }

    private fun requestActivities(
        startDate: String?,
        endDate: String?,
        limit: Int,
    ): List<GarminActivity> {
        val builder = authClient.connectApiUrl("activitylist-service/activities/search/activities")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("start", "0")
            .addQueryParameter("limit", limit.toString())
        if (startDate != null) builder.addQueryParameter("startDate", startDate)
        if (endDate != null) builder.addQueryParameter("endDate", endDate)
        val json = client.newCall(Request.Builder().url(builder.build()).build()).executeNullableJsonArray()
            ?: return emptyList()
        return (0 until json.length()).mapNotNull { index ->
            json.optJSONObject(index)?.toGarminActivity()
        }
    }

    private fun Response.errorMessage(): String {
        val retryAfter = header("Retry-After")?.let { " Retry-After=$it" }.orEmpty()
        val bodyText = runCatching { peekBody(300).string() }.getOrDefault("")
        return "HTTP $code ${message.ifBlank { "Garmin API 请求失败" }}$retryAfter ${bodyText.take(160)}".trim()
    }

    private fun parseDailyHeartRate(date: LocalDate, displayName: String): GarminHeartRateSummary? = runCatching {
        val dateStr = date.format(fmt)
        val url = authClient.connectApiUrl("wellness-service/wellness/dailyHeartRate")
            .toHttpUrl()
            .newBuilder()
            .addPathSegment(displayName)
            .addQueryParameter("date", dateStr)
            .build()
        val json = client.newCall(Request.Builder().url(url).build()).executeNullableJson() ?: return@runCatching null
        val values = json.optJSONArray("heartRateValues").readHeartRateValues()
        val average = values.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val resting = json.optPositiveInt("restingHeartRate")
            ?: json.optPositiveInt("restingHR")
            ?: values.takeIf { it.isNotEmpty() }?.minOrNull()
        if (average == null && resting == null) null else GarminHeartRateSummary(average, resting)
    }.getOrNull()

    private fun JSONArray?.readHeartRateValues(): List<Int> {
        if (this == null) return emptyList()
        val out = mutableListOf<Int>()
        for (i in 0 until length()) {
            val item = opt(i)
            val bpm = when (item) {
                is JSONArray -> item.optInt(1, 0)
                is JSONObject -> item.optInt("heartRate", item.optInt("bpm", 0))
                is Number -> item.toInt()
                else -> 0
            }
            if (bpm > 0) out.add(bpm)
        }
        return out
    }

    private fun JSONObject.optPositiveInt(name: String): Int? =
        optInt(name, 0).takeIf { it > 0 }

    /** 依次尝试多个候选字段名, 返回第一个 > 0 的整数。 */
    private fun JSONObject.firstPositiveInt(vararg names: String): Int? {
        for (n in names) {
            val v = optInt(n, 0)
            if (v > 0) return v
        }
        return null
    }

    /** 依次尝试多个候选字段名, 返回第一个 > 0 的浮点数。 */
    private fun JSONObject.firstPositiveDouble(vararg names: String): Double? {
        for (n in names) {
            val v = optDouble(n, 0.0)
            if (v > 0.0) return v
        }
        return null
    }

    private fun JSONObject.toGarminActivity(): GarminActivity? {
        val activityId = firstPositiveLong("activityId", "activityPk", "id") ?: return null
        val rawGmt = optString("startTimeGMT", "")
            .ifBlank { optString("beginTimestamp", "") }
        val rawLocal = optString("startTimeLocal", "")
        val startEpochMs = parseGarminTime(
            rawGmt.ifBlank { rawLocal }
        ) ?: return null
        val durationSec = firstPositiveDouble("duration", "movingDuration", "elapsedDuration") ?: 0.0
        val elapsedSec = firstPositiveDouble("elapsedDuration")
        val movingSec = firstPositiveDouble("movingDuration")
        val endEpochMs = startEpochMs + ((elapsedSec ?: durationSec).toLong() * 1000L).coerceAtLeast(0L)
        val activeCalories = firstPositiveDouble("calories", "activeKilocalories", "activeCalories")
        val bmrCalories = firstPositiveDouble("bmrCalories", "bmrKilocalories")
        val totalCalories = firstPositiveDouble("totalCalories", "totalKilocalories")
            ?: listOfNotNull(activeCalories, bmrCalories).takeIf { it.isNotEmpty() }?.sum()
        val type = optJSONObject("activityType")?.optString("typeKey")
            ?.takeIf { it.isNotBlank() }
            ?: optJSONObject("activityType")?.optInt("typeId", 0)?.takeIf { it > 0 }?.toString()
            ?: optString("activityType", "").takeIf { it.isNotBlank() }
            ?: "other"

        return GarminActivity(
            id = activityId.toString(),
            type = type,
            name = optString("activityName", "").takeIf { it.isNotBlank() },
            note = firstNonBlankString(
                "description",
                "activityDescription",
                "comments",
                "comment",
                "notes",
                "note",
                "activityNote",
            ),
            localDate = parseGarminActivityLocalDate(rawLocal, optString("calendarDate", "")),
            startEpochMs = startEpochMs,
            endEpochMs = endEpochMs,
            durationMinutes = (durationSec / 60.0).toLong().coerceAtLeast(0L),
            elapsedMinutes = elapsedSec?.let { (it / 60.0).toLong().coerceAtLeast(0L) },
            movingMinutes = movingSec?.let { (it / 60.0).toLong().coerceAtLeast(0L) },
            distanceMeters = firstPositiveDouble("distance"),
            activeCalories = activeCalories,
            bmrCalories = bmrCalories,
            totalCalories = totalCalories,
            avgHeartRate = firstPositiveInt("averageHR", "averageHeartRate", "avgHr"),
            maxHeartRate = firstPositiveInt("maxHR", "maxHeartRate"),
            steps = firstPositiveLong("steps"),
            avgCadence = firstPositiveDouble("averageRunningCadenceInStepsPerMinute", "averageCadence", "avgCadence"),
            maxCadence = firstPositiveDouble("maxRunningCadenceInStepsPerMinute", "maxCadence"),
            avgSpeed = firstPositiveDouble("averageSpeed", "avgSpeed"),
            maxSpeed = firstPositiveDouble("maxSpeed"),
            elevationGainMeters = firstPositiveDouble("elevationGain"),
            elevationLossMeters = firstPositiveDouble("elevationLoss"),
            minElevationMeters = firstPositiveDouble("minElevation"),
            maxElevationMeters = firstPositiveDouble("maxElevation"),
            trainingEffect = firstPositiveDouble("aerobicTrainingEffect", "trainingEffect"),
            anaerobicTrainingEffect = firstPositiveDouble("anaerobicTrainingEffect"),
            avgStrideLengthMeters = firstPositiveDouble("avgStrideLength", "averageStrideLength"),
            avgPowerWatts = firstPositiveDouble("avgPower", "averagePower"),
            maxPowerWatts = firstPositiveDouble("maxPower"),
            sweatLossMl = firstPositiveDouble("waterEstimated", "sweatLoss"),
        )
    }

    private fun JSONObject.firstPositiveLong(vararg names: String): Long? {
        for (n in names) {
            val v = optLong(n, 0L)
            if (v > 0L) return v
        }
        return null
    }

    private fun JSONObject.firstNonBlankString(vararg names: String): String? {
        for (name in names) {
            val value = optString(name, "").trim()
            if (value.isNotBlank() && value != "null") return value
        }
        return null
    }

    private fun parseGarminTime(raw: String): Long? = runCatching {
        val clean = raw.trim()
        when {
            clean.isBlank() -> null
            clean.endsWith("Z") -> java.time.Instant.parse(clean).toEpochMilli()
            clean.contains("T") -> LocalDateTime.parse(clean.substringBefore(".").replace("Z", "")).toInstant(ZoneOffset.UTC).toEpochMilli()
            else -> LocalDateTime.parse(clean, garminDateTimeFmt).toInstant(ZoneOffset.UTC).toEpochMilli()
        }
    }.getOrNull()

    private fun parseGarminActivityLocalDate(rawLocal: String, rawCalendarDate: String): LocalDate? =
        runCatching {
            rawCalendarDate.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
                ?: rawLocal.takeIf { it.isNotBlank() }
                    ?.let { LocalDateTime.parse(it, garminDateTimeFmt).toLocalDate() }
        }.getOrNull()

    private fun GarminActivity.occursOn(date: LocalDate): Boolean =
        localDate == date ||
            Instant.ofEpochMilli(startEpochMs).atZone(ZoneId.systemDefault()).toLocalDate() == date

    private fun JSONObject.readBodyBatteryPoints(): List<GarminBodyBatteryPoint> {
        val arrays = listOfNotNull(
            optJSONArray("bodyBatteryValuesArray"),
            optJSONArray("bodyBatteryValues"),
            optJSONArray("bodyBatteryValueDescriptorDTOList"),
            optJSONArray("allDayBodyBattery"),
        )
        return arrays.flatMap { values ->
            (0 until values.length()).mapNotNull { index ->
                val item = values.opt(index)
                when (item) {
                    is JSONArray -> {
                        val time = item.opt(0).toEpochFromGarminValue()
                        val level = item.optInt(1, 0).takeIf { it in 1..100 }
                        if (time != null && level != null) GarminBodyBatteryPoint(time, level) else null
                    }
                    is JSONObject -> {
                        val time = (
                            item.opt("timestamp")
                                ?: item.opt("startGMT")
                                ?: item.opt("calendarDate")
                                ?: item.opt("time")
                        ).toEpochFromGarminValue()
                        val level = item.firstPositiveInt("bodyBatteryLevel", "bodyBattery", "value", "level")
                            ?.takeIf { it in 1..100 }
                        if (time != null && level != null) GarminBodyBatteryPoint(time, level) else null
                    }
                    else -> null
                }
            }
        }.distinctBy { it.timeEpochMs }.sortedBy { it.timeEpochMs }
    }

    private fun Any?.toEpochFromGarminValue(): Long? = when (this) {
        is Number -> {
            val v = toLong()
            when {
                v <= 0L -> null
                v > 1_000_000_000_000L -> v
                else -> v * 1000L
            }
        }
        is String -> parseGarminTime(this)
        else -> null
    }
}

class GarminApiException(val code: Int, message: String) : IOException(message)

data class GarminDailySummary(
    val totalSteps: Int,
    val totalDistanceMeters: Double,
    val activeKilocalories: Int,
    val totalKilocalories: Int,
    val activeMinutes: Long,
    val averageHeartRate: Int?,
    val restingHeartRate: Int,
    val floorsAscended: Double,
    val avgStress: Int? = null,
    val maxStress: Int? = null,
    val bodyBatteryHigh: Int? = null,
    val bodyBatteryLow: Int? = null,
    val avgSpo2: Int? = null,
    val avgRespiration: Double? = null,
) {
    /**
     * 是否有「真实」数据。
     * 注意: 故意**不**把 [totalKilocalories] 单独算进来 —— 没戴表的日子佳明仍会返回一个
     * 几乎恒定的基础代谢(BMR)总消耗, 若据此判定「有数据」, 整列卡路里就会全是同一个值,
     * 还会把月/年图表拉回到很多年前(详见 #1/#2)。所以必须有真实活动或生理信号才算有数据。
     */
    val hasData: Boolean
        get() = totalSteps > 0 ||
            totalDistanceMeters > 0.0 ||
            activeKilocalories > 0 ||
            activeMinutes > 0 ||
            (averageHeartRate ?: 0) > 0 ||
            restingHeartRate > 0 ||
            floorsAscended > 0.0 ||
            (avgStress ?: 0) > 0 ||
            (maxStress ?: 0) > 0 ||
            (avgSpo2 ?: 0) > 0 ||
            (avgRespiration ?: 0.0) > 0.0
}

data class GarminSleep(
    val startEpochMs: Long,
    val endEpochMs: Long,
    val totalMinutes: Long,
    val deepMinutes: Long,
    val lightMinutes: Long,
    val remMinutes: Long,
    val awakeMinutes: Long,
    val sleepScore: Int?,
)

data class GarminCycle(
    val isPeriodDay: Boolean,
    val flow: Int
)

data class GarminActivity(
    val id: String,
    val type: String,
    val name: String?,
    val note: String?,
    val localDate: LocalDate?,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val durationMinutes: Long,
    val elapsedMinutes: Long?,
    val movingMinutes: Long?,
    val distanceMeters: Double?,
    val activeCalories: Double?,
    val bmrCalories: Double?,
    val totalCalories: Double?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val steps: Long?,
    val avgCadence: Double?,
    val maxCadence: Double?,
    val avgSpeed: Double?,
    val maxSpeed: Double?,
    val elevationGainMeters: Double?,
    val elevationLossMeters: Double?,
    val minElevationMeters: Double?,
    val maxElevationMeters: Double?,
    val trainingEffect: Double?,
    val anaerobicTrainingEffect: Double?,
    val avgStrideLengthMeters: Double?,
    val avgPowerWatts: Double?,
    val maxPowerWatts: Double?,
    val sweatLossMl: Double?,
)

data class GarminBodyBatteryPoint(
    val timeEpochMs: Long,
    val level: Int,
)

private data class GarminHeartRateSummary(
    val averageHeartRate: Int?,
    val restingHeartRate: Int?,
)

data class GarminRawPayload(
    val categoryKey: String,
    val categoryLabel: String,
    val endpointPath: String,
    val payloadJson: String,
)

private data class RawEndpoint(
    val key: String,
    val label: String,
    val path: String,
    val params: Map<String, String> = emptyMap(),
)
