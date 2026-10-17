import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.walktape.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.walktape.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // W12-T05: local-only signing (local/signing.env, git-ignored). No secrets in VCS:
            // the committed file only reads an optional local properties file.
            val signingEnv = rootProject.file("local/signing.env")
            if (signingEnv.exists()) {
                val props = Properties().apply {
                    signingEnv.inputStream().use { load(it) }
                }
                val storePath = props.getProperty("WALKTAPE_KEYSTORE")
                if (!storePath.isNullOrBlank() && rootProject.file(storePath).exists()) {
                    signingConfig = signingConfigs.create("localRelease") {
                        storeFile = rootProject.file(storePath)
                        storePassword = props.getProperty("WALKTAPE_KEYSTORE_PASSWORD")
                        keyAlias = props.getProperty("WALKTAPE_KEY_ALIAS")
                        keyPassword = props.getProperty("WALKTAPE_KEY_PASSWORD")
                    }
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.mockwebserver)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Opt-in only: the test reads secrets itself; Gradle receives only a local file path.
tasks.withType<Test>().configureEach {
    if (providers.gradleProperty("realServerTest").orNull == "true") {
        systemProperty("walktape.testServerFile", rootProject.file("local/test-server.env").absolutePath)
    }
}
