import com.android.build.api.variant.FilterConfiguration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
    alias(libs.plugins.aboutLibraries)
}

aboutLibraries {
    collect {
        configPath = file("config")
    }
}
// 记得在发布版本时更新此处的值
val baseVersionName = "1.6.0"
val baseVersionCode = 20

val releaseSigningEnv = listOf(
    "LYRICO_KEYSTORE_PATH",
    "LYRICO_KEYSTORE_PASSWORD",
    "LYRICO_KEY_ALIAS",
    "LYRICO_KEY_PASSWORD"
).associateWith { providers.environmentVariable(it).orNull?.takeIf(String::isNotEmpty) }
val hasReleaseSigning = releaseSigningEnv.values.any { it != null }
if (hasReleaseSigning) {
    require(releaseSigningEnv.values.all { it != null }) {
        "Incomplete Release signing configuration. Missing: " +
            releaseSigningEnv.filterValues { it == null }.keys.joinToString()
    }
}

fun gitCommitHash(): Provider<String> = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "unknown" } }
    .orElse("unknown")



android {
    namespace = "com.lonx.lyrico"
    ndkVersion = "29.0.14206865"
    compileSdk {
        version = release(37)
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }
    defaultConfig {
        applicationId = "com.lonx.lyrico"
        minSdk = 28
        targetSdk = 36
        versionCode = baseVersionCode
        versionName = "$baseVersionName-${gitCommitHash().get()}"

        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseSigningEnv.getValue("LYRICO_KEYSTORE_PATH")!!)
                storePassword = releaseSigningEnv.getValue("LYRICO_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningEnv.getValue("LYRICO_KEY_ALIAS")
                keyPassword = releaseSigningEnv.getValue("LYRICO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .singleOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            val abiSuffix = abi?.let { "-$it" }.orEmpty()

            output.outputFileName.set(
                output.versionName.map { versionName ->
                    "Lyrico-$versionName$abiSuffix.apk"
                }
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
dependencies {
    implementation(libs.salt.ui)
    // Project Modules
    implementation(project(":lyrico-audiotag"))

    // network
    implementation(libs.okhttp)
    // JSON 解析
    implementation(libs.kotlinx.serialization.json)
    // Compose & UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.destinations.core)
    implementation(libs.miuix.preference.android)
    implementation(libs.miuix.ui.android)
    implementation(libs.lottie.compose)
    implementation(libs.miuix.blur.android)
    implementation(libs.coil.compose)
    implementation(libs.zoomable)
    implementation(libs.coil.network.okhttp)
    implementation(libs.reorderable)
    implementation(libs.compose.markdown)
    implementation(libs.lazycolumnscrollbar)
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)

    // Dependency Injection
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Utilities
    implementation(libs.devicecompat)
    implementation(libs.tinypinyin)
    implementation(libs.opencc4j)
    implementation(libs.aboutlibraries.compose.core)
    // KSP
    ksp(libs.compose.destinations.ksp)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
