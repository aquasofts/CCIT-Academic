import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.wire)
}

wire {
    sourcePath {
        // Only the six read-only roots and their transitive schema dependencies are generated.
        srcDir(rootProject.file("TiebaLite/app/src/main/protos"))
    }
    root(
        "tieba.frsPage.FrsPageRequest",
        "tieba.frsPage.FrsPageResponse",
        "tieba.pbPage.PbPageRequest",
        "tieba.pbPage.PbPageResponse",
        "tieba.pbFloor.PbFloorRequest",
        "tieba.pbFloor.PbFloorResponse",
        "tieba.profile.ProfileRequest",
        "tieba.profile.ProfileResponse",
        "tieba.userPost.UserPostRequest",
        "tieba.userPost.UserPostResponse",
        "tieba.forumRuleDetail.ForumRuleDetailRequest",
        "tieba.forumRuleDetail.ForumRuleDetailResponse",
        "tieba.addPost.AddPostRequest",
        "tieba.addPost.AddPostResponse",
    )
    kotlin {
        android = true
    }
}

android {
    namespace = "edu.ccit.webvpn.feature.tieba"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets["main"].java.srcDir(
        rootProject.file("TiebaLite/app/src/main/java/com/huanchengfly/tieba/post/utils/helios"),
    )
    sourceSets["main"].assets.srcDir(rootProject.file("TiebaLite/app/src/main/assets"))

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.retrofit.wire)
    implementation(libs.gson)
    implementation(libs.wire.runtime)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
}
