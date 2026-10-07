package com.vita.healthtracker.data.ai

import com.vita.healthtracker.data.prefs.SettingsPreferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * AI 洞察的执行层抽象。
 * 实现: [LanBridgeInsightProvider] (同 Wi-Fi 下的 Mac, 走 charlotte 的 Claude 订阅额度)
 * 和 [DirectApiInsightProvider] (手机直连大模型 API, 用户自带 Key)。
 * 将来若要普世化 (自有后端 / Sign in with Claude / 端侧模型),
 * 只需新增实现, 数据打包、洞察 schema 和 UI 全部不动。
 */
interface AiInsightProvider {
    suspend fun analyze(payload: AiAnalysisPayload): AiInsight
}

/**
 * 按设置页「分析方式」在 Mac 桥接与 API 直连之间路由。
 * 每次分析时现读偏好, 切换方式后无需重启 app。
 */
class ModeRoutingInsightProvider(
    private val prefs: SettingsPreferences,
    private val lan: AiInsightProvider,
    private val api: AiInsightProvider,
) : AiInsightProvider {
    override suspend fun analyze(payload: AiAnalysisPayload): AiInsight =
        when (prefs.aiMode.first()) {
            SettingsPreferences.AI_MODE_API -> api.analyze(payload)
            else -> lan.analyze(payload)
        }
}

/**
 * 局域网桥接: 手机和 Mac 在同一 Wi-Fi 下, 直接 HTTP 调 Mac 上的 vita_ai_server.py。
 * 安全优先: 不做自动发现, 必须在设置页手动填写地址和口令。
 */
class LanBridgeInsightProvider(
    private val prefs: SettingsPreferences,
) : AiInsightProvider {

    private val json = Json {
        explicitNulls = false
        encodeDefaults = false
        ignoreUnknownKeys = true
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        // Claude 跑一年数据的深度分析可能要几分钟, 读超时放宽
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun analyze(payload: AiAnalysisPayload): AiInsight = withContext(Dispatchers.IO) {
        val baseUrl = resolveBaseUrl()
            ?: throw AiInsightException("请先在设置页填写 Mac 分析服务地址。")
        val token = prefs.aiServerToken.first().trim()
        if (token.isEmpty()) {
            throw AiInsightException("请先在设置页填写 AI 分析口令。")
        }
        val body = json.encodeToString(AiAnalysisPayload.serializer(), payload)
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/analyze")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        val response = runCatching { client.newCall(request).execute() }.getOrElse {
            throw AiInsightException("连不上 $baseUrl，确认 Mac 上的分析服务在运行。", it)
        }
        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val detail = runCatching {
                    json.decodeFromString(ServerError.serializer(), text).error
                }.getOrNull() ?: "HTTP ${resp.code}"
                throw AiInsightException("分析失败：$detail")
            }
            runCatching { json.decodeFromString(AiInsight.serializer(), text) }.getOrElse {
                throw AiInsightException("分析服务返回了无法解析的内容。", it)
            }
        }
    }

    /** 返回形如 http://192.168.1.5:8787 的根地址; 解析失败返回 null。 */
    private suspend fun resolveBaseUrl(): String? {
        val manual = prefs.aiServerAddress.first().trim()
        return manual.takeIf { it.isNotEmpty() }?.let(::normalize)
    }

    private fun normalize(address: String): String {
        var a = address.removeSuffix("/")
        if (!a.startsWith("http://") && !a.startsWith("https://")) a = "http://$a"
        // 没写端口就补默认端口
        val afterScheme = a.substringAfter("://")
        if (!afterScheme.contains(":")) a = "$a:$DEFAULT_PORT"
        return a
    }

    @kotlinx.serialization.Serializable
    private data class ServerError(val error: String? = null)

    companion object {
        private const val DEFAULT_PORT = 8787
    }
}
