import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

abstract class VerifyPersonalSigning : DefaultTask() {
    @get:InputFile
    abstract val keystoreFile: RegularFileProperty

    @get:Internal
    abstract val storePassword: Property<String>

    @get:Input
    abstract val keyAlias: Property<String>

    @get:Input
    abstract val expectedSha256: Property<String>

    @TaskAction
    fun verify() {
        val store = KeyStore.getInstance(keystoreFile.get().asFile, storePassword.get().toCharArray())
        check(store.isKeyEntry(keyAlias.get())) { "Personal signing alias must contain the existing private key." }
        val certificate = store.getCertificate(keyAlias.get())
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
        check(digest == expectedSha256.get()) {
            "Signing certificate changed. Refusing to build an APK that cannot update the installed Vita."
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

fun projectProperties(name: String): Properties = Properties().apply {
    load(providers.fileContents(rootProject.layout.projectDirectory.file(name)).asText.get().reader())
}

val appVersion = projectProperties("version.properties")
val distribution = projectProperties("distribution.properties")
val personalStoreFile = providers.environmentVariable("VITA_SIGNING_KEYSTORE_PATH")
    .orElse(providers.gradleProperty("vitaSigningStoreFile"))
    .orElse("${System.getProperty("user.home")}/.android/debug.keystore")
val personalStorePassword = providers.environmentVariable("VITA_SIGNING_STORE_PASSWORD").orElse("android")
val personalKeyAlias = providers.environmentVariable("VITA_SIGNING_KEY_ALIAS").orElse("androiddebugkey")
val personalKeyPassword = providers.environmentVariable("VITA_SIGNING_KEY_PASSWORD").orElse("android")

android {
    namespace = "com.vita.healthtracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vita.healthtracker"
        minSdk = 28
        targetSdk = 35
        versionCode = appVersion.getProperty("versionCode").toInt().also {
            require(it in 2..2_100_000_000) { "versionCode must exceed the original installed version (1)." }
        }
        versionName = appVersion.getProperty("versionName").also {
            require(it.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "versionName must use major.minor.patch." }
        }
        buildConfigField("String", "UPDATE_REPOSITORY", "\"${distribution.getProperty("updateRepository")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("personal") {
            storeFile = file(personalStoreFile.get())
            storePassword = personalStorePassword.get()
            keyAlias = personalKeyAlias.get()
            keyPassword = personalKeyPassword.get()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        create("personal") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            isDebuggable = false
            signingConfig = signingConfigs.getByName("personal")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

val verifyPersonalSigning = tasks.register<VerifyPersonalSigning>("verifyPersonalSigning") {
    keystoreFile.set(layout.file(personalStoreFile.map { file(it) }))
    storePassword.set(personalStorePassword)
    keyAlias.set(personalKeyAlias)
    expectedSha256.set(distribution.getProperty("signingCertificateSha256"))
}
tasks.matching { it.name == "prePersonalBuild" }.configureEach {
    dependsOn(verifyPersonalSigning)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.health.connect.client)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)

    implementation(libs.vico.compose.m3)

    implementation(libs.okhttp)
    implementation(libs.security.crypto)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
