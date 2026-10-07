package com.vita.healthtracker.data.update

import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
data class ReleaseManifest(
    val versionName: String,
    val versionCode: Int,
    val applicationId: String,
    val signingCertificateSha256: String,
    val apk: ReleaseApk,
)

@Serializable
data class ReleaseApk(val name: String, val sha256: String)

@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val body: String? = null,
    val assets: List<GitHubAsset>,
)

@Serializable
internal data class GitHubAsset(
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val downloadUrl: String,
)

data class AvailableUpdate(
    val manifest: ReleaseManifest,
    val downloadUrl: String,
    val sizeBytes: Long,
    val notes: String,
)

/** The current APK signers are compared exactly. Signing-key rotation is deliberately unsupported. */
internal data class UpdatePackageIdentity(
    val applicationId: String,
    val versionCode: Long,
    val certificateSha256: Set<String>,
)

enum class UpdateError {
    NotConfigured, NoRelease, Network, RateLimited, InvalidRelease,
    WrongPackage, WrongSignature, WrongVersion, CorruptDownload, InvalidApk,
    InstallPermission, InstallerUnavailable,
}

internal class UpdateException(val error: UpdateError) : Exception(error.name)

/** Pure protocol validation, shared by checking and the final pre-install verification. */
internal object UpdateReleaseProtocol {
    val json = Json { ignoreUnknownKeys = true }
    private val hashPattern = Regex("[a-f0-9]{64}")
    private val ownerPattern = Regex("[A-Za-z0-9][A-Za-z0-9-]*")
    private val repositoryPattern = Regex("[A-Za-z0-9_.-]+")
    private val apkNamePattern = Regex("[A-Za-z0-9][A-Za-z0-9_.-]*\\.apk", RegexOption.IGNORE_CASE)
    const val MAX_APK_BYTES = 512L * 1024 * 1024

    fun repositoryParts(repository: String): List<String> {
        val parts = repository.split('/')
        if (parts.size != 2 || !ownerPattern.matches(parts[0]) ||
            !repositoryPattern.matches(parts[1]) || parts[1] in setOf(".", "..")
        ) throw UpdateException(UpdateError.NotConfigured)
        return parts
    }

    fun apiUrl(repository: String): String {
        val (owner, name) = repositoryParts(repository)
        return "https://api.github.com/repos/$owner/$name/releases/latest"
    }

    fun assetUrl(repository: String, tag: String, name: String, url: String): HttpUrl {
        val repo = repositoryParts(repository)
        val parsed = url.toHttpUrlOrNull() ?: throw UpdateException(UpdateError.InvalidRelease)
        val path = parsed.pathSegments
        if (!isHttpsUrl(parsed) || parsed.host != "github.com" ||
            path.size != 6 || path.take(2).map(String::lowercase) != repo.map(String::lowercase) ||
            path[2] != "releases" || path[3] != "download" || path[4] != tag || path[5] != name ||
            parsed.query != null || parsed.fragment != null
        ) throw UpdateException(UpdateError.InvalidRelease)
        return parsed
    }

    fun isAllowedRedirect(url: HttpUrl): Boolean = isHttpsUrl(url) && url.host in setOf(
        "github.com", "api.github.com", "release-assets.githubusercontent.com",
        "objects.githubusercontent.com", "github-releases.githubusercontent.com",
    )

    private fun isHttpsUrl(url: HttpUrl): Boolean =
        url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty()

    fun parseManifest(raw: String): ReleaseManifest {
        val manifest = try {
            json.decodeFromString<ReleaseManifest>(raw)
        } catch (_: Exception) {
            throw UpdateException(UpdateError.InvalidRelease)
        }
        if (manifest.versionCode <= 0 || manifest.versionName.isBlank() ||
            manifest.versionName.length > 100 || manifest.applicationId.isBlank() ||
            !hashPattern.matches(manifest.signingCertificateSha256) ||
            !hashPattern.matches(manifest.apk.sha256) || !apkNamePattern.matches(manifest.apk.name)
        ) throw UpdateException(UpdateError.InvalidRelease)
        return manifest
    }

    fun validateTarget(manifest: ReleaseManifest, installed: UpdatePackageIdentity): Boolean {
        if (manifest.applicationId != installed.applicationId) throw UpdateException(UpdateError.WrongPackage)
        if (installed.certificateSha256 != setOf(manifest.signingCertificateSha256)) {
            throw UpdateException(UpdateError.WrongSignature)
        }
        return manifest.versionCode.toLong() > installed.versionCode
    }

    fun validateApk(manifest: ReleaseManifest, installed: UpdatePackageIdentity, archive: UpdatePackageIdentity) {
        if (!validateTarget(manifest, installed)) throw UpdateException(UpdateError.WrongVersion)
        if (archive.applicationId != installed.applicationId) throw UpdateException(UpdateError.WrongPackage)
        if (archive.versionCode != manifest.versionCode.toLong()) throw UpdateException(UpdateError.WrongVersion)
        if (archive.certificateSha256 != installed.certificateSha256) throw UpdateException(UpdateError.WrongSignature)
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun digestHex(digest: MessageDigest): String =
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
