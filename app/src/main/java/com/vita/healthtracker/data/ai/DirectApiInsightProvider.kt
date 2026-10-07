package com.vita.healthtracker.data.ai

import com.vita.healthtracker.data.prefs.SettingsPreferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** API 协议方言: 市面绝大多数服务商兼容 OpenAI Chat Completions, Claude 官方 API 用 Anthropic Messages。 */
enum class AiApiDialect { OPENAI, ANTHROPIC }

/** 服务商预设: 选中后自动填 base URL / 协议 / 默认模型, 三者都允许手动覆盖。 */
data class AiApiPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val dialect: AiApiDialect,
    val defaultModel: String,
)

object AiApiPresets {
    val ALL = listOf(
        AiApiPreset("deepseek", "DeepSeek", "https://api.deepseek.com/v1", AiApiDialect.OPENAI, "deepseek-chat"),
        AiApiPreset("anthropic", "Claude", "https://api.anthropic.com", AiApiDialect.ANTHROPIC, "claude-sonnet-4-6"),
        AiApiPreset("openai", "OpenAI", "https://api.openai.com/v1", AiApiDialect.OPENAI, "gpt-5.1"),
        AiApiPreset("moonshot", "Kimi", "https://api.moonshot.cn/v1", AiApiDialect.OPENAI, "kimi-latest"),
        AiApiPreset("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", AiApiDialect.OPENAI, "qwen-plus"),
        AiApiPreset("zhipu", "智谱", "https://open.bigmodel.cn/api/paas/v4", AiApiDialect.OPENAI, "glm-4.6"),
        AiApiPreset("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", AiApiDialect.OPENAI, "gemini-2.5-flash"),
        AiApiPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", AiApiDialect.OPENAI, "anthropic/claude-sonnet-4.5"),
        AiApiPreset("custom", "自定义", "", AiApiDialect.OPENAI, ""),
    )

    fun byId(id: String?): AiApiPreset = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

/**
 * API 直连: 手机直接调大模型 API 分析, 不依赖家里的 Mac (出门 / Mac 关机 / 局域网不通时也能用)。
 * 提示词由 app 端 [AiAnalysisPrompt] 拼, 与 Mac 端保持一致; 费用走用户自己的 API Key。
 */
class DirectApiInsightProvider(
    private val prefs: SettingsPreferences,
) : AiInsightProvider {

    private val json = Json {
        explicitNulls = false
        encodeDefaults = false
        ignoreUnknownKeys = true
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // 一年数据的深度分析可能要几分钟, 读超时放宽
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private data class ApiConfig(
        val dialect: AiApiDialect,
        val baseUrl: String,
        val apiKey: String,
        val model: String,
    )

    override suspend fun analyze(payload: AiAnalysisPayload): AiInsight = withContext(Dispatchers.IO) {
        val cfg = resolveConfig()
        val dataJson = json.encodeToString(AiAnalysisPayload.serializer(), payload)
        val prompt = if (payload.focus == AiAnalysisPayload.FOCUS_TODAY) {
            AiAnalysisPrompt.buildToday(dataJson)
        } else {
            AiAnalysisPrompt.build(dataJson)
        }
        val text = complete(cfg, prompt, maxTokens = ANALYZE_MAX_TOKENS)
        val insight = runCatching {
            json.decodeFromString(AiInsight.serializer(), extractJsonObject(text))
        }.getOrElse {
            throw AiInsightException("模型返回了无法解析的内容, 换个模型或重试一次。", it)
        }
        if (insight.overall.isBlank()) {
            throw AiInsightException("分析结果缺少总评, 换个模型或重试一次。")
        }
        if (insight.model == null) insight.copy(model = cfg.model) else insight
    }

    /** 设置页「测试连接」: 发一个最小请求, 成功返回可展示的确认文案, 失败抛可读错误。 */
    suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val cfg = resolveConfig()
        complete(cfg, "请只回复两个字: 正常", maxTokens = 64)
        "连接正常 · ${cfg.model}"
    }

    // ─── 配置 ────────────────────────────────────────────────

    private suspend fun resolveConfig(): ApiConfig {
        val preset = AiApiPresets.byId(prefs.aiApiPreset.first())
        val baseUrl = prefs.aiApiBaseUrl.first().trim().ifEmpty { preset.baseUrl }
        val apiKey = prefs.aiApiKey.first().trim()
        val model = prefs.aiApiModel.first().trim().ifEmpty { preset.defaultModel }
        if (baseUrl.isEmpty()) throw AiInsightException("请先在设置页填写 API 地址。")
        if (apiKey.isEmpty()) throw AiInsightException("请先在设置页填写 API Key。")
        if (model.isEmpty()) throw AiInsightException("请先在设置页填写模型名称。")
        return ApiConfig(preset.dialect, normalizeBaseUrl(baseUrl), apiKey, model)
    }

    private fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim().removeSuffix("/")
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        return url
    }

    // ─── 请求 ────────────────────────────────────────────────

    private fun complete(cfg: ApiConfig, prompt: String, maxTokens: Int): String = when (cfg.dialect) {
        AiApiDialect.OPENAI -> completeOpenAi(cfg, prompt)
        AiApiDialect.ANTHROPIC -> completeAnthropic(cfg, prompt, maxTokens)
    }

    /** OpenAI Chat Completions 协议 (DeepSeek/Kimi/通义/智谱/Gemini 兼容端点/OpenRouter 通用)。 */
    private fun completeOpenAi(cfg: ApiConfig, prompt: String): String {
        // 不带 temperature / max_tokens: 各家可选参数名并不统一 (如 max_completion_tokens), 省略最兼容
        val body = buildJsonObject {
            put("model", cfg.model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
        }
        val root = execute(
            cfg,
            url = "${cfg.baseUrl}/chat/completions",
            headers = mapOf("Authorization" to "Bearer ${cfg.apiKey}"),
            bodyJson = body.toString(),
        )
        val content = runCatching {
            root.jsonObject["choices"]!!.jsonArray[0]
                .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        }.getOrNull()
        if (content.isNullOrBlank()) throw AiInsightException("模型没有返回内容, 换个模型或重试一次。")
        return content
    }

    /** Anthropic Messages 协议 (Claude 官方 API)。 */
    private fun completeAnthropic(cfg: ApiConfig, prompt: String, maxTokens: Int): String {
        val body = buildJsonObject {
            put("model", cfg.model)
            put("max_tokens", maxTokens)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
        }
        val root = execute(
            cfg,
            url = "${cfg.baseUrl}/v1/messages",
            headers = mapOf(
                "x-api-key" to cfg.apiKey,
                "anthropic-version" to "2023-06-01",
            ),
            bodyJson = body.toString(),
        )
        val content = runCatching {
            root.jsonObject["content"]!!.jsonArray
                .map { it.jsonObject }
                .filter { it["type"]?.jsonPrimitive?.content == "text" }
                .joinToString("") { it["text"]?.jsonPrimitive?.content.orEmpty() }
        }.getOrNull()
        if (content.isNullOrBlank()) throw AiInsightException("模型没有返回内容, 换个模型或重试一次。")
        return content
    }

    private fun execute(
        cfg: ApiConfig,
        url: String,
        headers: Map<String, String>,
        bodyJson: String,
    ): kotlinx.serialization.json.JsonElement {
        val builder = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
        headers.forEach { (k, v) -> builder.header(k, v) }
        val response = runCatching { client.newCall(builder.build()).execute() }.getOrElse {
            throw AiInsightException("连不上 ${cfg.baseUrl}，检查网络或 API 地址。", it)
        }
        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw AiInsightException("API 返回错误: ${describeError(resp.code, text)}")
            }
            return runCatching { json.parseToJsonElement(text) }.getOrElse {
                throw AiInsightException("API 返回了无法解析的内容 (HTTP ${resp.code})。", it)
            }
        }
    }

    /** 从各家不同形状的错误体里抠出人话 ({"error":{"message":..}} / {"error":".."} / {"message":..})。 */
    private fun describeError(code: Int, body: String): String {
        val message = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val error = root["error"]
            when {
                error != null && error is kotlinx.serialization.json.JsonObject ->
                    error["message"]?.jsonPrimitive?.content
                error != null -> error.jsonPrimitive.content
                else -> root["message"]?.jsonPrimitive?.content
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return message?.take(200) ?: "HTTP $code"
    }

    companion object {
        private const val ANALYZE_MAX_TOKENS = 8192

        /** 从模型输出里抠出 JSON 对象 (容忍代码围栏和前后杂质), 与 Mac 端 extract_json_object 同逻辑。 */
        internal fun extractJsonObject(text: String): String {
            val cleaned = text.trim()
                .replace(Regex("^```[a-zA-Z]*\\s*"), "")
                .replace(Regex("\\s*```$"), "")
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start == -1 || end <= start) {
                throw AiInsightException("模型输出里没有 JSON 对象。")
            }
            return cleaned.substring(start, end + 1)
        }
    }
}
