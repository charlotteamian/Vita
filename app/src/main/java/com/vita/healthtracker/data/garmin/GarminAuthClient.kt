package com.vita.healthtracker.data.garmin

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.EOFException
import java.net.URLEncoder
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.UUID
import javax.crypto.Mac
import javax.net.ssl.SSLException
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.TimeUnit

class GarminAuthClient(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "garmin_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * 可清空、按域名匹配的内存 CookieJar。
     * 用扁平列表 + [Cookie.matches] 做正确的域/路径匹配, 这样从网页登录抓到的、
     * 挂在 `.garmin.com` 上的 cookie 能同时发往 connect / sso / connectapi 各子域。
     */
    private class InMemoryCookieJar : CookieJar {
        private val all = mutableListOf<Cookie>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = addAll(cookies)
        override fun loadForRequest(url: HttpUrl): List<Cookie> = all.filter { it.matches(url) }
        fun addAll(cookies: List<Cookie>) {
            cookies.forEach { c ->
                all.removeAll { it.name == c.name && it.domain == c.domain }
                all.add(c)
            }
        }
        fun clear() = all.clear()
    }

    private val cookieJar = InMemoryCookieJar()

    private val rawClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.113 Mobile Safari/537.36")
                .header("NK", "NT")
                .build()
            chain.proceed(req)
        }
        .build()

    private val client = rawClient.newBuilder()
        .addInterceptor { chain ->
            val original = chain.request()
            val shouldAttachBearer =
                original.url.host == "connectapi.$garminDomain" &&
                    !original.url.encodedPath.startsWith("/oauth-service/")
            val req = if (shouldAttachBearer) {
                val bearer = runCatching { ensureAccessToken() }.getOrNull()
                if (bearer.isNullOrBlank()) original
                else original.newBuilder()
                    .header("Authorization", "Bearer $bearer")
                    .header("User-Agent", NATIVE_API_USER_AGENT)
                    .header("X-Garmin-User-Agent", NATIVE_X_GARMIN_USER_AGENT)
                    .header("X-Garmin-Paired-App-Version", "10861")
                    .header("X-Garmin-Client-Platform", "Android")
                    .header("X-App-Ver", "10861")
                    .header("X-Lang", "en")
                    .header("X-GCExperience", "GC5")
                    .header("Accept", "application/json")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
            } else {
                original
            }
            chain.proceed(req)
        }
        .build()

    private var oauth1Token: String? = prefs.getString("oauth1_token", null)
    private var oauth1Secret: String? = prefs.getString("oauth1_secret", null)
    private var mfaToken: String? = prefs.getString("mfa_token", null)
    private var oauth2AccessToken: String? = prefs.getString("oauth2_access_token", null)
    private var oauth2RefreshToken: String? = prefs.getString("oauth2_refresh_token", null)
    private var oauth2ExpiresAt: Long = prefs.getLong("oauth2_expires_at", 0L)
    private var oauth2RefreshExpiresAt: Long = prefs.getLong("oauth2_refresh_expires_at", 0L)
    private var diClientId: String? = prefs.getString("di_client_id", null)
    private var displayName: String? = prefs.getString("display_name", null)
    private var consumerKey: String? = prefs.getString("consumer_key", null)
    private var consumerSecret: String? = prefs.getString("consumer_secret", null)
    var garminDomain: String = prefs.getString("garmin_domain", "garmin.com") ?: "garmin.com"
    var isLogged: Boolean = oauth2AccessToken != null
        private set

    private var pendingMfa: PendingMfa? = null

    init {
        migrateLegacySession()
        loadSession(garminDomain)
        prefs.getString("saved_cookies", null)?.let {
            val parsedCookies = it.split(";").mapNotNull { part ->
                val parts = part.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    Cookie.Builder()
                        .name(parts[0])
                        .value(parts[1])
                        .domain(garminDomain)
                        .path("/")
                        .secure()
                        .build()
                } else null
            }
            cookieJar.addAll(parsedCookies)
            isLogged = true
        }
    }

    fun activeDomain(): String = garminDomain

    fun loggedDomains(): List<String> = GARMIN_DOMAINS.filter { isLogged(it) }

    fun isLogged(domain: String): Boolean =
        !prefs.getString(domainKey(domain, "oauth2_access_token"), null).isNullOrBlank()

    fun activateDomain(domain: String): Boolean = loadSession(domain)

    fun saveSessionCookies(cookieStr: String, domain: String): AuthResult {
        try {
            garminDomain = domain
            prefs.edit().putString("garmin_domain", domain).apply()
            val parsedCookies = cookieStr.split(";").mapNotNull { part ->
                val parts = part.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    Cookie.Builder()
                        .name(parts[0])
                        .value(parts[1])
                        .domain(domain)
                        .path("/")
                        .secure()
                        .build()
                } else null
            }
            cookieJar.addAll(parsedCookies)
            oauth1Token = null
            oauth1Secret = null
            mfaToken = null
            oauth2AccessToken = null
            oauth2RefreshToken = null
            oauth2ExpiresAt = 0L
            oauth2RefreshExpiresAt = 0L
            diClientId = null
            prefs.edit()
                .putString("garmin_domain", domain)
                .putString("saved_cookies", cookieStr)
                .remove("oauth1_token")
                .remove("oauth1_secret")
                .remove("mfa_token")
                .remove("oauth2_access_token")
                .remove("oauth2_refresh_token")
                .remove("oauth2_expires_at")
                .remove("oauth2_refresh_expires_at")
                .remove("di_client_id")
                .apply()
            isLogged = true
            return AuthResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            return AuthResult.Error(e.message ?: "解析 Cookie 失败")
        }
    }

    suspend fun login(email: String, password: String, domain: String): AuthResult = withContext(Dispatchers.IO) {
        runCatching {
            resetLoginSession(domain)
            pendingMfa = null

            val primary = performMobileLogin(email, password, domain, LoginFlow.Di)
            if (domain == "garmin.cn" && primary is AuthResult.Error && !primary.msg.contains("INVALID_USERNAME_PASSWORD")) {
                performMobileLogin(email, password, domain, LoginFlow.Legacy)
            } else {
                primary
            }
        }.getOrElse { AuthResult.Error(it.friendlyMessage()) }
    }

    suspend fun submitMfa(code: String): AuthResult = withContext(Dispatchers.IO) {
        val pending = pendingMfa ?: return@withContext AuthResult.Error("请先输入账号密码发起登录")
        runCatching {
            val body = JSONObject()
                .put("mfaMethod", pending.method)
                .put("mfaVerificationCode", code)
                .put("rememberMyBrowser", false)
                .put("reconsentList", JSONArray())
                .put("mfaSetup", false)
                .toString()
                .toRequestBody(JSON)

            val response = rawClient.newCall(
                Request.Builder()
                    .url(ssoUrl(pending.domain, "mobile/api/mfa/verifyCode", pending.params))
                    .headers(mobileApiHeaders(pending.domain))
                    .post(body)
                    .build()
            ).executeJson()

            if (response.responseType() == SSO_SUCCESSFUL) {
                pendingMfa = null
                when (pending.flow) {
                    LoginFlow.Di -> completeDiLogin(
                        ticket = response.getString("serviceTicketId"),
                        domain = pending.domain,
                        serviceUrl = pending.serviceUrl,
                    )
                    LoginFlow.Legacy -> completeLogin(response.getString("serviceTicketId"), pending.domain)
                }
            } else {
                AuthResult.Error(response.responseMessage(response.responseType()))
            }
        }.getOrElse { AuthResult.Error(it.friendlyMessage()) }
    }

    suspend fun exchangeOAuth2() = withContext(Dispatchers.IO) {
        ensureAccessToken()
    }

    fun getClient(): OkHttpClient = client

    fun connectApiUrl(path: String): String {
        val normalized = path.removePrefix("/")
        return "https://connectapi.$garminDomain/$normalized"
    }

    fun connectApiUrl(path: String, query: Map<String, String>): String {
        val base = connectApiUrl(path).toHttpUrl().newBuilder()
        query.forEach { (key, value) -> base.addQueryParameter(key, value) }
        return base.build().toString()
    }

    fun requireDisplayName(): String =
        displayName ?: refreshDisplayName() ?: throw IOException("Garmin 未返回用户显示名, 无法读取日汇总")
    
    fun logout(domain: String? = null) {
        val targetDomain = domain ?: garminDomain
        prefs.edit()
            .remove(domainKey(targetDomain, "oauth1_token"))
            .remove(domainKey(targetDomain, "oauth1_secret"))
            .remove(domainKey(targetDomain, "mfa_token"))
            .remove(domainKey(targetDomain, "oauth2_access_token"))
            .remove(domainKey(targetDomain, "oauth2_refresh_token"))
            .remove(domainKey(targetDomain, "oauth2_expires_at"))
            .remove(domainKey(targetDomain, "oauth2_refresh_expires_at"))
            .remove(domainKey(targetDomain, "di_client_id"))
            .remove(domainKey(targetDomain, "display_name"))
            .remove("saved_cookies")
            .apply()
        cookieJar.clear()
        pendingMfa = null
        val fallbackDomain = loggedDomains().firstOrNull()
        if (fallbackDomain != null) {
            loadSession(fallbackDomain)
        } else {
            clearActiveSession(targetDomain)
        }
    }

    private fun resetLoginSession(domain: String) {
        val domainChanged = garminDomain != domain
        loadSession(domain)
        garminDomain = domain
        cookieJar.clear()
        if (domainChanged) displayName = null
        prefs.edit()
            .putString("garmin_domain", domain)
            .remove("saved_cookies")
            .apply {
                if (domainChanged) remove("display_name")
            }
            .apply()
    }

    private fun loadSession(domain: String): Boolean {
        garminDomain = domain
        oauth1Token = prefs.getString(domainKey(domain, "oauth1_token"), null)
        oauth1Secret = prefs.getString(domainKey(domain, "oauth1_secret"), null)
        mfaToken = prefs.getString(domainKey(domain, "mfa_token"), null)
        oauth2AccessToken = prefs.getString(domainKey(domain, "oauth2_access_token"), null)
        oauth2RefreshToken = prefs.getString(domainKey(domain, "oauth2_refresh_token"), null)
        oauth2ExpiresAt = prefs.getLong(domainKey(domain, "oauth2_expires_at"), 0L)
        oauth2RefreshExpiresAt = prefs.getLong(domainKey(domain, "oauth2_refresh_expires_at"), 0L)
        diClientId = prefs.getString(domainKey(domain, "di_client_id"), null)
        displayName = prefs.getString(domainKey(domain, "display_name"), null)
        isLogged = oauth2AccessToken != null
        prefs.edit().putString("garmin_domain", domain).apply()
        return isLogged
    }

    private fun clearActiveSession(domain: String = garminDomain) {
        garminDomain = domain
        oauth1Token = null
        oauth1Secret = null
        mfaToken = null
        oauth2AccessToken = null
        oauth2RefreshToken = null
        oauth2ExpiresAt = 0L
        oauth2RefreshExpiresAt = 0L
        diClientId = null
        displayName = null
        isLogged = false
        prefs.edit()
            .putString("garmin_domain", domain)
            .remove("oauth1_token")
            .remove("oauth1_secret")
            .remove("mfa_token")
            .remove("oauth2_access_token")
            .remove("oauth2_refresh_token")
            .remove("oauth2_expires_at")
            .remove("oauth2_refresh_expires_at")
            .remove("di_client_id")
            .remove("display_name")
            .apply()
    }

    private fun migrateLegacySession() {
        val token = oauth2AccessToken ?: return
        if (isLogged(garminDomain)) return
        prefs.edit()
            .putString(domainKey(garminDomain, "oauth1_token"), oauth1Token)
            .putString(domainKey(garminDomain, "oauth1_secret"), oauth1Secret)
            .putString(domainKey(garminDomain, "mfa_token"), mfaToken)
            .putString(domainKey(garminDomain, "oauth2_access_token"), token)
            .putString(domainKey(garminDomain, "oauth2_refresh_token"), oauth2RefreshToken)
            .putLong(domainKey(garminDomain, "oauth2_expires_at"), oauth2ExpiresAt)
            .putLong(domainKey(garminDomain, "oauth2_refresh_expires_at"), oauth2RefreshExpiresAt)
            .putString(domainKey(garminDomain, "di_client_id"), diClientId)
            .putString(domainKey(garminDomain, "display_name"), displayName)
            .apply()
    }

    private fun performMobileLogin(
        email: String,
        password: String,
        domain: String,
        flow: LoginFlow,
    ): AuthResult {
        val clientId = when (flow) {
            LoginFlow.Di -> IOS_CLIENT_ID
            LoginFlow.Legacy -> ANDROID_CLIENT_ID
        }
        val service = when (flow) {
            LoginFlow.Di -> iosServiceUrl(domain)
            LoginFlow.Legacy -> androidServiceUrl(domain)
        }
        val params = loginParams(clientId, service)
        val signInUrl = HttpUrl.Builder()
            .scheme("https")
            .host("sso.$domain")
            .addPathSegments("mobile/sso/en/sign-in")
            .addQueryParameter("clientId", clientId)
            .build()
        rawClient.newCall(
            Request.Builder()
                .url(signInUrl)
                .headers(ssoHeaders())
                .build()
        ).execute().use { /* Sets SSO cookies. */ }

        val body = JSONObject()
            .put("username", email)
            .put("password", password)
            .put("rememberMe", false)
            .put("captchaToken", "")
            .toString()
            .toRequestBody(JSON)

        val response = rawClient.newCall(
            Request.Builder()
                .url(ssoUrl(domain, "mobile/api/login", params))
                .headers(mobileApiHeaders(domain))
                .post(body)
                .build()
        ).executeJson()

        return when (val type = response.responseType()) {
            SSO_SUCCESSFUL -> {
                val ticket = response.getString("serviceTicketId")
                when (flow) {
                    LoginFlow.Di -> completeDiLogin(ticket, domain, service)
                    LoginFlow.Legacy -> completeLogin(ticket, domain)
                }
            }
            SSO_MFA_REQUIRED -> {
                val method = response.optJSONObject("customerMfaInfo")
                    ?.optString("mfaLastMethodUsed")
                    ?.takeIf { it.isNotBlank() }
                    ?: "email"
                pendingMfa = PendingMfa(
                    domain = domain,
                    params = params,
                    method = method,
                    flow = flow,
                    serviceUrl = service,
                )
                AuthResult.NeedsMFA(method)
            }
            else -> AuthResult.Error(response.responseMessage(type))
        }
    }

    private fun completeDiLogin(ticket: String, domain: String, serviceUrl: String): AuthResult {
        val oauth2 = exchangeServiceTicket(ticket, domain, serviceUrl)
        saveDiTokens(oauth2, domain)
        refreshDisplayName()
        return AuthResult.Success
    }

    private fun completeLogin(ticket: String, domain: String): AuthResult {
        runCatching {
            rawClient.newCall(
                Request.Builder()
                    .url(ssoEmbedUrl(domain))
                    .headers(ssoHeaders())
                    .build()
            ).execute().close()
        }

        val oauth1 = requestOAuth1(ticket, domain)
        oauth1Token = oauth1.oauthToken
        oauth1Secret = oauth1.oauthTokenSecret
        mfaToken = oauth1.mfaToken

        val oauth2 = exchangeOAuth2(oauth1, domain, login = true)
        saveTokens(oauth1, oauth2, domain)
        refreshDisplayName()
        return AuthResult.Success
    }

    private fun requestOAuth1(ticket: String, domain: String): OAuth1Token {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("connectapi.$domain")
            .addPathSegments("oauth-service/oauth/preauthorized")
            .addQueryParameter("ticket", ticket)
            .addQueryParameter("login-url", ssoEmbedUrl(domain))
            .addQueryParameter("accepts-mfa-tokens", "true")
            .build()

        val request = oauthSignedRequest("GET", url, emptyMap(), token = null, tokenSecret = null)
        val text = rawClient.newCall(request).executeText()
        val parts = text.split("&").mapNotNull { item ->
            val pair = item.split("=", limit = 2)
            if (pair.size == 2) pair[0] to pair[1] else null
        }.toMap()
        return OAuth1Token(
            oauthToken = parts["oauth_token"] ?: throw IOException("Garmin 未返回 OAuth1 token"),
            oauthTokenSecret = parts["oauth_token_secret"] ?: throw IOException("Garmin 未返回 OAuth1 secret"),
            mfaToken = parts["mfa_token"],
        )
    }

    private fun exchangeOAuth2(oauth1: OAuth1Token, domain: String, login: Boolean): OAuth2Token {
        val form = buildMap {
            if (login) put("audience", "GARMIN_CONNECT_MOBILE_ANDROID_DI")
            oauth1.mfaToken?.let { put("mfa_token", it) }
        }
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("connectapi.$domain")
            .addPathSegments("oauth-service/oauth/exchange/user/2.0")
            .build()
        val request = oauthSignedRequest(
            method = "POST",
            url = url,
            form = form,
            token = oauth1.oauthToken,
            tokenSecret = oauth1.oauthTokenSecret,
        )
        val json = rawClient.newCall(request).executeJson()
        val now = System.currentTimeMillis() / 1000
        return OAuth2Token(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAt = json.optLong("expires_at", now + json.optLong("expires_in", 0L)),
            refreshExpiresAt = json.optLong("refresh_token_expires_at", now + json.optLong("refresh_token_expires_in", 0L)),
        )
    }

    private fun exchangeServiceTicket(ticket: String, domain: String, serviceUrl: String): OAuth2Token {
        var lastError = "未返回详细错误"
        for (clientId in DI_CLIENT_IDS) {
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("service_ticket", ticket)
                .add("grant_type", DI_GRANT_TYPE)
                .add("service_url", serviceUrl)
                .build()
            val req = Request.Builder()
                .url(diTokenUrl(domain))
                .headers(diHeaders(clientId))
                .post(body)
                .build()
            rawClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.code == 429) {
                    throw IOException("HTTP 429: DI token exchange rate limited")
                }
                if (!resp.isSuccessful) {
                    lastError = "HTTP ${resp.code}: ${text.take(160)}"
                    return@use
                }
                val json = JSONObject(text)
                val accessToken = json.optString("access_token").takeIf { it.isNotBlank() }
                if (accessToken.isNullOrBlank()) {
                    lastError = "DI token response missing access_token"
                    return@use
                }
                val now = System.currentTimeMillis() / 1000
                val resolvedClientId = extractClientIdFromJwt(accessToken) ?: clientId
                return OAuth2Token(
                    accessToken = accessToken,
                    refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                    expiresAt = json.optLong("expires_at", jwtExpiresAt(accessToken) ?: (now + json.optLong("expires_in", 3600L))),
                    refreshExpiresAt = json.optLong("refresh_token_expires_at", now + json.optLong("refresh_token_expires_in", 0L)),
                    clientId = resolvedClientId,
                )
            }
        }
        throw IOException("Garmin DI token exchange failed: $lastError")
    }

    private fun refreshDiToken(domain: String, clientId: String, refreshToken: String): OAuth2Token {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("refresh_token", refreshToken)
            .build()
        val req = Request.Builder()
            .url(diTokenUrl(domain))
            .headers(diHeaders(clientId, accept = "application/json"))
            .post(body)
            .build()
        val json = rawClient.newCall(req).executeJson()
        val accessToken = json.getString("access_token")
        val now = System.currentTimeMillis() / 1000
        return OAuth2Token(
            accessToken = accessToken,
            refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: refreshToken,
            expiresAt = json.optLong("expires_at", jwtExpiresAt(accessToken) ?: (now + json.optLong("expires_in", 3600L))),
            refreshExpiresAt = json.optLong("refresh_token_expires_at", now + json.optLong("refresh_token_expires_in", 0L)),
            clientId = extractClientIdFromJwt(accessToken) ?: clientId,
        )
    }

    private fun ensureAccessToken(): String? {
        val existing = oauth2AccessToken ?: return null
        val now = System.currentTimeMillis() / 1000
        if (oauth2ExpiresAt > now + 60) return existing

        synchronized(this) {
            val current = oauth2AccessToken ?: return null
            val checkedNow = System.currentTimeMillis() / 1000
            if (oauth2ExpiresAt > checkedNow + 60) return current
            val diId = diClientId
            val refresh = oauth2RefreshToken
            if (!diId.isNullOrBlank() && !refresh.isNullOrBlank()) {
                val oauth2 = refreshDiToken(garminDomain, diId, refresh)
                saveDiTokens(oauth2, garminDomain)
                return oauth2.accessToken
            }
            val token = oauth1Token ?: return current
            val secret = oauth1Secret ?: return current
            if (oauth2RefreshExpiresAt != 0L && oauth2RefreshExpiresAt <= checkedNow) return current
            val oauth1 = OAuth1Token(token, secret, mfaToken)
            val oauth2 = exchangeOAuth2(oauth1, garminDomain, login = false)
            saveTokens(oauth1, oauth2, garminDomain)
            return oauth2.accessToken
        }
    }

    private fun saveTokens(oauth1: OAuth1Token, oauth2: OAuth2Token, domain: String) {
        oauth1Token = oauth1.oauthToken
        oauth1Secret = oauth1.oauthTokenSecret
        mfaToken = oauth1.mfaToken
        oauth2AccessToken = oauth2.accessToken
        oauth2RefreshToken = oauth2.refreshToken
        oauth2ExpiresAt = oauth2.expiresAt
        oauth2RefreshExpiresAt = oauth2.refreshExpiresAt
        diClientId = null
        garminDomain = domain
        isLogged = true
        prefs.edit()
            .putString("garmin_domain", domain)
            .putString("oauth1_token", oauth1.oauthToken)
            .putString("oauth1_secret", oauth1.oauthTokenSecret)
            .putString("mfa_token", oauth1.mfaToken)
            .putString("oauth2_access_token", oauth2.accessToken)
            .putString("oauth2_refresh_token", oauth2.refreshToken)
            .putLong("oauth2_expires_at", oauth2.expiresAt)
            .putLong("oauth2_refresh_expires_at", oauth2.refreshExpiresAt)
            .remove("di_client_id")
            .remove("saved_cookies")
            .putString(domainKey(domain, "oauth1_token"), oauth1.oauthToken)
            .putString(domainKey(domain, "oauth1_secret"), oauth1.oauthTokenSecret)
            .putString(domainKey(domain, "mfa_token"), oauth1.mfaToken)
            .putString(domainKey(domain, "oauth2_access_token"), oauth2.accessToken)
            .putString(domainKey(domain, "oauth2_refresh_token"), oauth2.refreshToken)
            .putLong(domainKey(domain, "oauth2_expires_at"), oauth2.expiresAt)
            .putLong(domainKey(domain, "oauth2_refresh_expires_at"), oauth2.refreshExpiresAt)
            .remove(domainKey(domain, "di_client_id"))
            .apply()
    }

    private fun saveDiTokens(oauth2: OAuth2Token, domain: String) {
        oauth1Token = null
        oauth1Secret = null
        mfaToken = null
        oauth2AccessToken = oauth2.accessToken
        oauth2RefreshToken = oauth2.refreshToken
        oauth2ExpiresAt = oauth2.expiresAt
        oauth2RefreshExpiresAt = oauth2.refreshExpiresAt
        diClientId = oauth2.clientId
        garminDomain = domain
        isLogged = true
        prefs.edit()
            .putString("garmin_domain", domain)
            .remove("oauth1_token")
            .remove("oauth1_secret")
            .remove("mfa_token")
            .putString("oauth2_access_token", oauth2.accessToken)
            .putString("oauth2_refresh_token", oauth2.refreshToken)
            .putLong("oauth2_expires_at", oauth2.expiresAt)
            .putLong("oauth2_refresh_expires_at", oauth2.refreshExpiresAt)
            .putString("di_client_id", oauth2.clientId)
            .remove("saved_cookies")
            .remove(domainKey(domain, "oauth1_token"))
            .remove(domainKey(domain, "oauth1_secret"))
            .remove(domainKey(domain, "mfa_token"))
            .putString(domainKey(domain, "oauth2_access_token"), oauth2.accessToken)
            .putString(domainKey(domain, "oauth2_refresh_token"), oauth2.refreshToken)
            .putLong(domainKey(domain, "oauth2_expires_at"), oauth2.expiresAt)
            .putLong(domainKey(domain, "oauth2_refresh_expires_at"), oauth2.refreshExpiresAt)
            .putString(domainKey(domain, "di_client_id"), oauth2.clientId)
            .apply()
    }

    private fun refreshDisplayName(): String? {
        val req = Request.Builder()
            .url(connectApiUrl("userprofile-service/socialProfile"))
            .build()
        val json = client.newCall(req).executeJson()
        val name = json.optString("displayName").takeIf { it.isNotBlank() }
            ?: json.optString("profileId").takeIf { it.isNotBlank() }
        if (!name.isNullOrBlank()) {
            displayName = name
            prefs.edit()
                .putString("display_name", name)
                .putString(domainKey(garminDomain, "display_name"), name)
                .apply()
        }
        return name
    }

    private fun oauthSignedRequest(
        method: String,
        url: HttpUrl,
        form: Map<String, String>,
        token: String?,
        tokenSecret: String?,
    ): Request {
        val consumer = loadConsumer()
        val oauthParams = mutableMapOf(
            "oauth_consumer_key" to consumer.first,
            "oauth_nonce" to UUID.randomUUID().toString().replace("-", ""),
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to (System.currentTimeMillis() / 1000).toString(),
            "oauth_version" to "1.0",
        )
        token?.let { oauthParams["oauth_token"] = it }

        val queryParams = url.queryParameterNames.flatMap { name ->
            url.queryParameterValues(name).map { value -> name to (value ?: "") }
        }
        val signatureParams = oauthParams.map { it.key to it.value } + queryParams + form.map { it.key to it.value }
        val signatureBase = listOf(
            method.uppercase(),
            percentEncode(url.newBuilder().query(null).build().toString()),
            percentEncode(
                signatureParams
                    .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
                    .joinToString("&") { "${percentEncode(it.first)}=${percentEncode(it.second)}" }
            )
        ).joinToString("&")
        val signingKey = "${percentEncode(consumer.second)}&${percentEncode(tokenSecret.orEmpty())}"
        oauthParams["oauth_signature"] = hmacSha1(signatureBase, signingKey)

        val authHeader = "OAuth " + oauthParams
            .toSortedMap()
            .entries
            .joinToString(", ") { "${percentEncode(it.key)}=\"${percentEncode(it.value)}\"" }

        val builder = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .header("User-Agent", OAUTH_USER_AGENT)

        return if (method.equals("POST", ignoreCase = true)) {
            val body = FormBody.Builder().apply {
                form.forEach { (key, value) -> add(key, value) }
            }.build()
            builder
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(body)
                .build()
        } else {
            builder.get().build()
        }
    }

    private fun loadConsumer(): Pair<String, String> {
        val savedKey = consumerKey
        val savedSecret = consumerSecret
        if (!savedKey.isNullOrBlank() && !savedSecret.isNullOrBlank()) return savedKey to savedSecret

        val json = rawClient.newCall(Request.Builder().url(OAUTH_CONSUMER_URL).build()).executeJson()
        val key = json.getString("consumer_key")
        val secret = json.getString("consumer_secret")
        consumerKey = key
        consumerSecret = secret
        prefs.edit().putString("consumer_key", key).putString("consumer_secret", secret).apply()
        return key to secret
    }

    private fun ssoUrl(domain: String, path: String, params: Map<String, String>): HttpUrl {
        val builder = HttpUrl.Builder()
            .scheme("https")
            .host("sso.$domain")
            .addPathSegments(path)
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    private fun loginParams(clientId: String, serviceUrl: String): Map<String, String> = mapOf(
        "clientId" to clientId,
        "locale" to "en-US",
        "service" to serviceUrl,
    )

    private fun androidServiceUrl(domain: String): String = "https://mobile.integration.$domain/gcm/android"

    private fun iosServiceUrl(domain: String): String =
        if (domain == "garmin.com") IOS_SERVICE_URL else "https://mobile.integration.$domain/gcm/ios"

    private fun ssoEmbedUrl(domain: String): String = "https://sso.$domain/sso/embed"

    private fun ssoHeaders(): Headers = Headers.Builder()
        .add("User-Agent", SSO_USER_AGENT)
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .add("Accept-Language", "en-US,en;q=0.9")
        .add("Sec-Fetch-Mode", "navigate")
        .add("Sec-Fetch-Dest", "document")
        .build()

    private fun mobileApiHeaders(domain: String): Headers = Headers.Builder()
        .add("User-Agent", SSO_USER_AGENT)
        .add("Accept", "application/json, text/plain, */*")
        .add("Accept-Language", "en-US,en;q=0.9")
        .add("Origin", "https://sso.$domain")
        .add("Content-Type", "application/json")
        .build()

    private fun diHeaders(clientId: String, accept: String = "application/json,text/html;q=0.9,*/*;q=0.8"): Headers =
        Headers.Builder()
            .add("User-Agent", NATIVE_API_USER_AGENT)
            .add("X-Garmin-User-Agent", NATIVE_X_GARMIN_USER_AGENT)
            .add("X-Garmin-Paired-App-Version", "10861")
            .add("X-Garmin-Client-Platform", "Android")
            .add("X-App-Ver", "10861")
            .add("X-Lang", "en")
            .add("X-GCExperience", "GC5")
            .add("Accept-Language", "en-US,en;q=0.9")
            .add("Accept", accept)
            .add("Content-Type", "application/x-www-form-urlencoded")
            .add("Cache-Control", "no-cache")
            .add("Authorization", basicAuth(clientId))
            .build()

    private fun diTokenUrl(domain: String): String =
        "https://diauth.$domain/di-oauth2-service/oauth/token"

    private fun basicAuth(clientId: String): String =
        "Basic " + android.util.Base64.encodeToString("$clientId:".toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)

    private fun jwtExpiresAt(token: String): Long? =
        decodeJwtPayload(token)?.optLong("exp")?.takeIf { it > 0L }

    private fun extractClientIdFromJwt(token: String): String? =
        decodeJwtPayload(token)?.optString("client_id")?.takeIf { it.isNotBlank() }

    private fun decodeJwtPayload(token: String): JSONObject? = runCatching {
        val parts = token.split(".")
        if (parts.size < 2) return@runCatching null
        val padded = parts[1] + "=".repeat((4 - parts[1].length % 4) % 4)
        val decoded = android.util.Base64.decode(
            padded,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP,
        )
        JSONObject(String(decoded, Charsets.UTF_8))
    }.getOrNull()

    private fun percentEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")

    private fun hmacSha1(value: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return android.util.Base64.encodeToString(mac.doFinal(value.toByteArray(Charsets.UTF_8)), android.util.Base64.NO_WRAP)
    }

    private fun Call.executeText(): String =
        execute().use { resp ->
            if (!resp.isSuccessful) {
                val retryAfter = resp.header("Retry-After")?.let { " Retry-After=$it" }.orEmpty()
                val bodyText = runCatching { resp.peekBody(300).string() }.getOrDefault("")
                throw IOException("HTTP ${resp.code}: ${resp.message}$retryAfter ${bodyText.take(160)}".trim())
            }
            resp.body?.string().orEmpty()
        }

    private fun Call.executeJson(): JSONObject = JSONObject(executeText())

    private fun JSONObject.responseType(): String = optJSONObject("responseStatus")?.optString("type").orEmpty()

    private fun JSONObject.responseMessage(type: String): String {
        val message = optJSONObject("responseStatus")?.optString("message").orEmpty()
        return listOf(type, message).filter { it.isNotBlank() }.joinToString(": ").ifBlank { "Garmin 登录失败" }
    }

    private fun Throwable.friendlyMessage(): String =
        when {
            message?.contains("HTTP 429") == true ->
                "Garmin 服务器正在限流。请先停 15-30 分钟再试；连续重试会让服务端更容易返回 429 或直接断开连接。"
            this is SocketException || this is EOFException || this is SSLException ->
                "Garmin 连接被服务器中断。连续重试或刚被限流后，服务端可能会直接断开连接；请稍后再试。"
            this is SocketTimeoutException ->
                "Garmin 响应超时。请稍后重试，或先用国区/Health Connect 同步。"
            this is IOException -> message ?: "网络请求失败"
            else -> message ?: "登录失败"
        }

    private data class PendingMfa(
        val domain: String,
        val params: Map<String, String>,
        val method: String,
        val flow: LoginFlow,
        val serviceUrl: String,
    )

    private enum class LoginFlow { Di, Legacy }

    private data class OAuth1Token(
        val oauthToken: String,
        val oauthTokenSecret: String,
        val mfaToken: String?,
    )

    private data class OAuth2Token(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAt: Long,
        val refreshExpiresAt: Long,
        val clientId: String? = null,
    )

    sealed class AuthResult {
        object Success : AuthResult()
        data class NeedsMFA(val method: String) : AuthResult()
        data class Error(val msg: String) : AuthResult()
    }

    companion object {
        private const val ANDROID_CLIENT_ID = "GCM_ANDROID_DARK"
        private const val IOS_CLIENT_ID = "GCM_IOS_DARK"
        private const val IOS_SERVICE_URL = "https://mobile.integration.garmin.com/gcm/ios"
        private const val SSO_SUCCESSFUL = "SUCCESSFUL"
        private const val SSO_MFA_REQUIRED = "MFA_REQUIRED"
        private const val OAUTH_USER_AGENT = "com.garmin.android.apps.connectmobile"
        private const val NATIVE_API_USER_AGENT = "GCM-Android-5.23"
        private const val NATIVE_X_GARMIN_USER_AGENT =
            "com.garmin.android.apps.connectmobile/5.23; ; Google/sdk_gphone64_arm64/google; Android/33; Dalvik/2.1.0"
        private const val DI_GRANT_TYPE =
            "https://connectapi.garmin.com/di-oauth2-service/oauth/grant/service_ticket"
        private const val OAUTH_CONSUMER_URL = "https://thegarth.s3.amazonaws.com/oauth_consumer.json"
        private const val SSO_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"
        private val DI_CLIENT_IDS = listOf(
            "GARMIN_CONNECT_MOBILE_ANDROID_DI_2025Q2",
            "GARMIN_CONNECT_MOBILE_ANDROID_DI_2024Q4",
            "GARMIN_CONNECT_MOBILE_ANDROID_DI",
            "GARMIN_CONNECT_MOBILE_IOS_DI",
        )
        private val GARMIN_DOMAINS = listOf("garmin.com", "garmin.cn")
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun domainKey(domain: String, name: String): String =
            "session_${domain.replace(".", "_")}_$name"
    }
}
