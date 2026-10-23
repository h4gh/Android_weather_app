import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release podpis: hesla a cesta ke keystoru v keystore.properties (není v gitu).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "cz.kisiala.pocasi"
    compileSdk = 37

    defaultConfig {
        applicationId = "cz.kisiala.pocasi"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) create("release") {
            storeFile = file(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Bez keystore.properties vznikne nepodepsané APK (nepodepisujeme potichu debug klíčem).
            signingConfig = signingConfigs.findByName("release")
            // MapLibre má nativní knihovnu ~13 MB pro každou architekturu – release jen pro 64bit ARM
            // (prakticky všechny dnešní telefony); x86 zbytečně jen pro emulátory, debug je má dál všechny.
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Nativní knihovny (MapLibre) v APK komprimovaně – menší soubor ke stažení/poslání,
    // za cenu rozbalení při instalaci (v telefonu zabere o pár MB víc).
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    // Aplikace je česky a anglicky – bez překladů knihoven (AndroidX, Material) do desítek dalších jazyků.
    // Seznam jazyků pro systémovou volbu jazyka aplikace (Android 13+) se generuje z res/ (výchozí = cs).
    androidResources {
        localeFilters += listOf("cs", "en")
        generateLocaleConfig = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.maplibre.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    // Glance s sebou tahá starý WorkManager; novější má pravidla pro R8 (full mode).
    implementation(libs.androidx.work.runtime)
}

// APK k rozeslání (kamarádům na test) se srozumitelným názvem: app/build/outputs/share/app-pocasi_release.apk
// (do složky release kopírovat nejde – patří úlohám AGP).
val copyShareableApk by tasks.registering(Copy::class) {
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(layout.buildDirectory.dir("outputs/share"))
    rename { "app-pocasi_release.apk" }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(copyShareableApk) }
