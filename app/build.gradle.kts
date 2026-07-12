plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val ciVersionName = providers.gradleProperty("ciVersionName").orNull
val ciVersionCode = providers.gradleProperty("ciVersionCode").orNull?.toIntOrNull()
val releaseStoreFile = providers.environmentVariable("ANDROID_SIGNING_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)

require(releaseSigningValues.none { it != null } || releaseSigningValues.all { !it.isNullOrBlank() }) {
    "Release signing is only partially configured. Set all ANDROID_SIGNING_* environment variables."
}

android {
    namespace = "edu.ccit.webvpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "edu.ccit.webvpn"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode ?: 5
        versionName = ciVersionName ?: "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        if (releaseSigningValues.all { !it.isNullOrBlank() }) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        create("performance") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            isDebuggable = false
        }
    }

    flavorDimensions += "captcha"
    productFlavors {
        create("autoCaptcha") {
            dimension = "captcha"
            versionNameSuffix = "-auto-captcha"
        }
        create("manualCaptcha") {
            dimension = "captcha"
            versionNameSuffix = "-manual-captcha"
        }
    }
}

dependencies {
    implementation(project(":core:academic"))
    implementation(project(":core:captcha"))
    implementation(project(":core:ui"))
    implementation(project(":core:webvpn"))
    add("autoCaptchaImplementation", project(":feature:captcha-autofill"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Keep the repository-wide verification entry points after adding the captcha flavors.
tasks.register("compileDebugKotlin") {
    group = "verification"
    dependsOn(
        "compileAutoCaptchaDebugKotlin",
        "compileManualCaptchaDebugKotlin",
    )
}

tasks.register("lintDebug") {
    group = "verification"
    dependsOn(
        "lintAutoCaptchaDebug",
        "lintManualCaptchaDebug",
    )
}
