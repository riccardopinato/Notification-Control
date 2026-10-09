import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
}

val rcPropertiesFile = rootProject.file("release/RC.properties")
val rcProperties = Properties().apply {
    require(rcPropertiesFile.isFile) { "Missing canonical RC identity: $rcPropertiesFile" }
    rcPropertiesFile.inputStream().use { load(it) }
}
val rcVersionName = requireNotNull(rcProperties.getProperty("VERSION_NAME")) {
    "VERSION_NAME missing from release/RC.properties"
}
val rcVersionCode = requireNotNull(rcProperties.getProperty("VERSION_CODE")).toIntOrNull()
    ?: error("VERSION_CODE must be an integer in release/RC.properties")
val rcId = requireNotNull(rcProperties.getProperty("RC_ID")) {
    "RC_ID missing from release/RC.properties"
}

val releaseStoreFile = System.getenv("SIGNING_STORE_FILE")
val releaseStorePassword = System.getenv("SIGNING_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("SIGNING_KEY_ALIAS")
val releaseKeyPassword = System.getenv("SIGNING_KEY_PASSWORD")
val releaseSigningReady = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

val defaultGoogleWebClientId =
    "72691779013-hci9cr313a5ado5skdghs6h7a5pg84md.apps.googleusercontent.com"

val googleWebClientId =
    System.getenv("GOOGLE_WEB_CLIENT_ID")
        ?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("GOOGLE_WEB_CLIENT_ID")
            .orNull
            ?.takeIf { it.isNotBlank() }
        ?: defaultGoogleWebClientId

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.riccardopinato.notificationcontrol"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.riccardopinato.notificationcontrol"
        minSdk = 24
        targetSdk = 36
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: rcVersionCode
        versionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() } ?: rcVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            googleWebClientId.asBuildConfigString()
        )
        buildConfigField("boolean", "QA_PREMIUM_UNLOCKED", "false")
        buildConfigField("boolean", "MEDIASTORE_RECOVERY_ENABLED", "true")
        buildConfigField("boolean", "PERFORMANCE_DIAGNOSTICS_ENABLED", "false")
        buildConfigField("String", "RELEASE_CANDIDATE_ID", rcId.asBuildConfigString())
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "PERFORMANCE_DIAGNOSTICS_ENABLED", "true")
        }
        release {
            isMinifyEnabled = true
            buildConfigField("boolean", "MEDIASTORE_RECOVERY_ENABLED", "false")
            isShrinkResources = true
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("perfTest") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            buildConfigField("boolean", "PERFORMANCE_DIAGNOSTICS_ENABLED", "true")
        }
        create("qaPremium") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            versionNameSuffix = "-qa-premium"
            buildConfigField("boolean", "QA_PREMIUM_UNLOCKED", "true")
            buildConfigField("boolean", "MEDIASTORE_RECOVERY_ENABLED", "true")
            buildConfigField("boolean", "PERFORMANCE_DIAGNOSTICS_ENABLED", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.billing.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.profileinstaller)

    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.ktx)
    testImplementation(libs.androidx.room.runtime)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
