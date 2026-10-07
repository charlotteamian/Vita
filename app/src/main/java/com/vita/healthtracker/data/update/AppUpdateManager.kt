package com.vita.healthtracker.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import com.vita.healthtracker.BuildConfig
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.OkHttpClient

enum class UpdatePhase { Idle, Checking, Latest, Available, Downloading, Ready, Failed }

data class AppUpdateState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    val available: AvailableUpdate? = null,
    val progress: Int = 0,
    val error: UpdateError? = null,
    val downloadedApk: File? = null,
) {
    val busy: Boolean get() = phase == UpdatePhase.Checking || phase == UpdatePhase.Downloading
}

/** Downloads belong to the application scope, so leaving Settings does not discard progress. */
class AppUpdateManager(
    context: Context,
    private val scope: CoroutineScope,
    private val repository: String = BuildConfig.UPDATE_REPOSITORY,
) {
    private val context = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val _state = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()
    private var job: Job? = null

    fun checkForUpdate() {
        if (job?.isActive == true) return
        _state.value = AppUpdateState(phase = UpdatePhase.Checking)
        job = scope.launch {
            try {
                val update = withContext(Dispatchers.IO) { fetchLatest() }
                _state.value = if (update == null) AppUpdateState(phase = UpdatePhase.Latest)
                else AppUpdateState(phase = UpdatePhase.Available, available = update)
            } catch (error: Exception) {
                handleFailure(error)
            }
        }
    }

    fun downloadUpdate() {
        if (job?.isActive == true) return
        val available = _state.value.available ?: return
        _state.value = _state.value.copy(phase = UpdatePhase.Downloading, progress = 0, error = null, downloadedApk = null)
        job = scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) { downloadAndVerify(available) }
                _state.value = _state.value.copy(phase = UpdatePhase.Ready, progress = 100, downloadedApk = apk)
            } catch (error: Exception) {
                handleFailure(error)
            }
        }
    }

    fun installDownloadedUpdate() {
        if (job?.isActive == true) return
        val snapshot = _state.value
        val available = snapshot.available ?: return
        val apk = snapshot.downloadedApk ?: return
        job = scope.launch {
            try {
                withContext(Dispatchers.IO) { verifyDownloaded(apk, available.manifest) }
                if (!context.packageManager.canRequestPackageInstalls()) throw UpdateException(UpdateError.InstallPermission)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                withContext(Dispatchers.Main) { context.startActivity(intent) }
                _state.value = snapshot.copy(phase = UpdatePhase.Ready, error = null)
            } catch (error: Exception) {
                handleFailure(error, UpdateError.InstallerUnavailable)
            }
        }
    }

    fun reportInstallerUnavailable() {
        _state.value = _state.value.copy(error = UpdateError.InstallerUnavailable)
    }

    private fun handleFailure(error: Exception, fallback: UpdateError = UpdateError.Network) {
        if (error is CancellationException) throw error
        val updateError = (error as? UpdateException)?.error ?: fallback
        val invalidDownload = updateError in setOf(
            UpdateError.CorruptDownload, UpdateError.InvalidApk, UpdateError.WrongPackage,
            UpdateError.WrongSignature, UpdateError.WrongVersion,
        )
        val snapshot = _state.value
        if (invalidDownload) snapshot.downloadedApk?.delete()
        val downloaded = snapshot.downloadedApk.takeUnless { invalidDownload }
        _state.value = snapshot.copy(
            phase = if (downloaded != null) UpdatePhase.Ready else UpdatePhase.Failed,
            error = updateError,
            downloadedApk = downloaded,
        )
    }

    private suspend fun fetchLatest(): AvailableUpdate? {
        val raw = readText(UpdateReleaseProtocol.apiUrl(repository), 2 * 1024 * 1024, apiRequest = true)
        val release = try {
            UpdateReleaseProtocol.json.decodeFromString<GitHubRelease>(raw)
        } catch (_: Exception) {
            throw UpdateException(UpdateError.InvalidRelease)
        }
        if (release.draft || release.prerelease || release.tagName.isBlank()) throw UpdateException(UpdateError.InvalidRelease)
        val metadata = release.assets.singleOrNull { it.name == "release-manifest.json" }
            ?: throw UpdateException(UpdateError.InvalidRelease)
        val metadataUrl = UpdateReleaseProtocol.assetUrl(repository, release.tagName, metadata.name, metadata.downloadUrl)
        val manifest = UpdateReleaseProtocol.parseManifest(readText(metadataUrl.toString(), 256 * 1024))
        if (!UpdateReleaseProtocol.validateTarget(manifest, installedIdentity())) return null
        val apk = release.assets.singleOrNull { it.name == manifest.apk.name }
            ?: throw UpdateException(UpdateError.InvalidRelease)
        if (apk.size !in 1..UpdateReleaseProtocol.MAX_APK_BYTES) throw UpdateException(UpdateError.InvalidRelease)
        UpdateReleaseProtocol.assetUrl(repository, release.tagName, apk.name, apk.downloadUrl)
        return AvailableUpdate(manifest, apk.downloadUrl, apk.size, release.body.orEmpty().take(2000))
    }

    private suspend fun downloadAndVerify(update: AvailableUpdate): File {
        val directory = File(context.cacheDir, "updates")
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create update cache")
        val apk = File(directory, "vita-${update.manifest.versionCode}.apk")
        if (apk.isFile) {
            try {
                verifyDownloaded(apk, update.manifest)
                return apk
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                apk.delete()
            }
        }
        val partial = File(directory, "vita-${update.manifest.versionCode}.part.apk")
        try {
            openResponse(update.downloadUrl).use { response ->
                val body = response.body ?: throw UpdateException(UpdateError.CorruptDownload)
                val length = body.contentLength()
                if (length > 0 && length != update.sizeBytes) throw UpdateException(UpdateError.CorruptDownload)
                var bytes = 0L
                var lastProgress = -1
                val digest = MessageDigest.getInstance("SHA-256")
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            bytes += read
                            if (bytes > update.sizeBytes) throw UpdateException(UpdateError.CorruptDownload)
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            val progress = (bytes * 100 / update.sizeBytes).toInt().coerceIn(0, 99)
                            if (progress != lastProgress) {
                                _state.value = _state.value.copy(progress = progress)
                                lastProgress = progress
                            }
                        }
                    }
                }
                if (bytes != update.sizeBytes || UpdateReleaseProtocol.digestHex(digest) != update.manifest.apk.sha256) {
                    throw UpdateException(UpdateError.CorruptDownload)
                }
            }
            verifyArchive(partial, update.manifest)
            if (!partial.renameTo(apk)) throw IOException("Cannot save update APK")
            return apk
        } finally {
            partial.delete()
        }
    }

    private suspend fun verifyDownloaded(apk: File, manifest: ReleaseManifest) {
        if (!apk.isFile || apk.length() !in 1..UpdateReleaseProtocol.MAX_APK_BYTES) throw UpdateException(UpdateError.CorruptDownload)
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        if (UpdateReleaseProtocol.digestHex(digest) != manifest.apk.sha256) throw UpdateException(UpdateError.CorruptDownload)
        verifyArchive(apk, manifest)
    }

    @Suppress("DEPRECATION")
    private fun installedIdentity(): UpdatePackageIdentity = identity(
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    )

    @Suppress("DEPRECATION")
    private fun verifyArchive(apk: File, manifest: ReleaseManifest) {
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw UpdateException(UpdateError.InvalidApk)
        UpdateReleaseProtocol.validateApk(manifest, installedIdentity(), identity(archive))
    }

    private fun identity(info: PackageInfo): UpdatePackageIdentity {
        val certificates = info.signingInfo?.apkContentsSigners.orEmpty()
            .map { UpdateReleaseProtocol.sha256(it.toByteArray()) }.toSet()
        if (certificates.isEmpty()) throw UpdateException(UpdateError.WrongSignature)
        return UpdatePackageIdentity(info.packageName, info.longVersionCode, certificates)
    }

    private suspend fun readText(url: String, maximumBytes: Int, apiRequest: Boolean = false): String =
        openResponse(url, apiRequest).use { response ->
            val body = response.body ?: throw UpdateException(UpdateError.InvalidRelease)
            if (body.contentLength() > maximumBytes) throw UpdateException(UpdateError.InvalidRelease)
            body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > maximumBytes) throw UpdateException(UpdateError.InvalidRelease)
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
        }

    /** Redirects are followed explicitly so even a malformed release cannot contact arbitrary hosts. */
    private fun openResponse(initialUrl: String, apiRequest: Boolean = false): Response {
        var request = Request.Builder().url(initialUrl)
            .header("User-Agent", "Vita/${BuildConfig.VERSION_NAME}")
            .apply {
                if (apiRequest) {
                    header("Accept", "application/vnd.github+json")
                    header("X-GitHub-Api-Version", "2022-11-28")
                }
            }.build()
        repeat(6) {
            val response = client.newCall(request).execute()
            if (response.code in setOf(301, 302, 303, 307, 308)) {
                val redirected = response.header("Location")?.let { request.url.resolve(it) }
                response.close()
                if (redirected == null || !UpdateReleaseProtocol.isAllowedRedirect(redirected)) {
                    throw UpdateException(UpdateError.InvalidRelease)
                }
                request = request.newBuilder().url(redirected).build()
            } else {
                if (response.isSuccessful) return response
                val error = when {
                    response.code == 404 && apiRequest -> UpdateError.NoRelease
                    response.code == 429 || response.code == 403 -> UpdateError.RateLimited
                    else -> UpdateError.Network
                }
                response.close()
                throw UpdateException(error)
            }
        }
        throw UpdateException(UpdateError.InvalidRelease)
    }
}
