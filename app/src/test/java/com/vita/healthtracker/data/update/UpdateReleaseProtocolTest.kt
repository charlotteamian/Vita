package com.vita.healthtracker.data.update

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.HttpUrl.Companion.toHttpUrl

class UpdateReleaseProtocolTest {
    private val certificate = "a".repeat(64)
    private val installed = UpdatePackageIdentity("com.vita.healthtracker.debug", 2, setOf(certificate))
    private val manifest = ReleaseManifest("0.3.0", 3, installed.applicationId, certificate, ReleaseApk("Vita-0.3.0.apk", "b".repeat(64)))

    @Test fun acceptsManifestWithFutureFields() {
        val json = """{
            "versionName":"0.3.0","versionCode":3,"applicationId":"com.vita.healthtracker.debug",
            "signingCertificateSha256":"$certificate",
            "apk":{"name":"Vita-0.3.0.apk","sha256":"${"b".repeat(64)}","size":100},
            "futureField":true
        }""".trimIndent()
        assertEquals(manifest, UpdateReleaseProtocol.parseManifest(json))
    }

    @Test fun rejectsMalformedManifestAndUnsafeFilename() {
        val valid = UpdateReleaseProtocol.json.encodeToString(ReleaseManifest.serializer(), manifest)
        listOf(
            "{}", "not json", valid.replace("\"versionCode\":3", "\"versionCode\":0"),
            valid.replace("\"versionCode\":3", "\"versionCode\":2147483648"),
            valid.replace("Vita-0.3.0.apk", "../Vita.apk"),
            valid.replace("b".repeat(64), "not-a-sha256"),
        ).forEach { assertError(UpdateError.InvalidRelease) { UpdateReleaseProtocol.parseManifest(it) } }
    }

    @Test fun versionCodeControlsUpdatesInsteadOfVersionName() {
        assertTrue(UpdateReleaseProtocol.validateTarget(manifest.copy(versionName = "0.1.0"), installed))
        assertFalse(UpdateReleaseProtocol.validateTarget(manifest.copy(versionCode = 2, versionName = "9.9.9"), installed))
        assertFalse(UpdateReleaseProtocol.validateTarget(manifest.copy(versionCode = 1), installed))
    }

    @Test fun wrongManifestPackageCannotOfferUpdate() {
        assertError(UpdateError.WrongPackage) {
            UpdateReleaseProtocol.validateTarget(manifest.copy(applicationId = "com.vita.healthtracker"), installed)
        }
    }

    @Test fun wrongManifestCertificateCannotOfferUpdate() {
        assertError(UpdateError.WrongSignature) {
            UpdateReleaseProtocol.validateTarget(manifest.copy(signingCertificateSha256 = "c".repeat(64)), installed)
        }
    }

    @Test fun apkMustHaveExactCurrentSignersPackageAndManifestVersion() {
        val archive = installed.copy(versionCode = 3)
        UpdateReleaseProtocol.validateApk(manifest, installed, archive)
        assertError(UpdateError.WrongPackage) {
            UpdateReleaseProtocol.validateApk(manifest, installed, archive.copy(applicationId = "com.example.other"))
        }
        assertError(UpdateError.WrongSignature) {
            UpdateReleaseProtocol.validateApk(manifest, installed, archive.copy(certificateSha256 = setOf("c".repeat(64))))
        }
        assertError(UpdateError.WrongSignature) {
            UpdateReleaseProtocol.validateApk(manifest, installed, archive.copy(certificateSha256 = setOf(certificate, "c".repeat(64))))
        }
        assertError(UpdateError.WrongVersion) {
            UpdateReleaseProtocol.validateApk(manifest, installed, archive.copy(versionCode = 4))
        }
        assertError(UpdateError.WrongVersion) {
            UpdateReleaseProtocol.validateApk(manifest.copy(versionCode = 2), installed, installed)
        }
    }

    @Test fun onlyConfiguredRepositoryReleaseAssetsAreAccepted() {
        val url = "https://github.com/charlotteamian/Vita/releases/download/v0.3.0/Vita-0.3.0.apk"
        assertEquals(url, UpdateReleaseProtocol.assetUrl("charlotteamian/Vita", "v0.3.0", manifest.apk.name, url).toString())
        listOf(
            url.replace("https://", "http://"),
            url.replace("github.com", "github.com.evil.example"),
            url.replace("github.com", "user:password@github.com"),
            url.replace("github.com", "github.com:8443"),
            url.replace("charlotteamian/Vita", "charlotteamian/Fortuna"),
            url.replace("v0.3.0", "v0.2.0"),
            "$url?redirect=1", "$url#fragment",
        ).forEach {
            assertError(UpdateError.InvalidRelease) {
                UpdateReleaseProtocol.assetUrl("charlotteamian/Vita", "v0.3.0", manifest.apk.name, it)
            }
        }
    }

    @Test fun repositoryCannotInjectApiPathOrQuery() {
        assertEquals("https://api.github.com/repos/charlotteamian/Vita/releases/latest", UpdateReleaseProtocol.apiUrl("charlotteamian/Vita"))
        listOf("", "charlotteamian", "charlotteamian/Vita/extra", "charlotteamian/..", "charlotteamian/Vita?x=y").forEach {
            assertError(UpdateError.NotConfigured) { UpdateReleaseProtocol.apiUrl(it) }
        }
    }

    @Test fun redirectsAllowGitHubAssetCdnButRejectOtherHostsAndCleartext() {
        assertTrue(UpdateReleaseProtocol.isAllowedRedirect("https://release-assets.githubusercontent.com/github-production-release-asset/test?token=a".toHttpUrl()))
        assertFalse(UpdateReleaseProtocol.isAllowedRedirect("http://release-assets.githubusercontent.com/file".toHttpUrl()))
        assertFalse(UpdateReleaseProtocol.isAllowedRedirect("https://evil.githubusercontent.com/file".toHttpUrl()))
        assertFalse(UpdateReleaseProtocol.isAllowedRedirect("https://release-assets.githubusercontent.com.evil.example/file".toHttpUrl()))
    }

    @Test fun sha256MatchesStandardTestVectorIncludingStreamedDigest() {
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(expected, UpdateReleaseProtocol.sha256("abc".toByteArray()))
        val streamed = MessageDigest.getInstance("SHA-256")
        streamed.update("a".toByteArray())
        streamed.update("bc".toByteArray())
        assertEquals(expected, UpdateReleaseProtocol.digestHex(streamed))
    }

    private fun assertError(expected: UpdateError, action: () -> Unit) {
        assertEquals(expected, assertThrows(UpdateException::class.java, action).error)
    }
}
