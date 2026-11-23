plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val releaseSigningVariables = listOf(
    "PACE_RELEASE_STORE_FILE",
    "PACE_RELEASE_STORE_PASSWORD",
    "PACE_RELEASE_KEY_ALIAS",
    "PACE_RELEASE_KEY_PASSWORD",
).associateWith(System::getenv)
val hasAnyReleaseSigningVariable = releaseSigningVariables.values.any { !it.isNullOrBlank() }
val hasCompleteReleaseSigningConfiguration = releaseSigningVariables.values.all { !it.isNullOrBlank() }
val supabaseUrl = providers.gradleProperty("PACE_SUPABASE_URL").orElse("").get()
val supabasePublishableKey = providers.gradleProperty("PACE_SUPABASE_PUBLISHABLE_KEY").orElse("").get()

if (hasAnyReleaseSigningVariable && !hasCompleteReleaseSigningConfiguration) {
    throw GradleException(
        "Release signing is partially configured. Set all four PACE_RELEASE_* variables or clear them all."
    )
}

android {
    namespace = "com.pace.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pace.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabasePublishableKey\"")
    }

    if (hasCompleteReleaseSigningConfiguration) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseSigningVariables.getValue("PACE_RELEASE_STORE_FILE")!!)
                storePassword = releaseSigningVariables.getValue("PACE_RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningVariables.getValue("PACE_RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningVariables.getValue("PACE_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "SAMPLE_DATA_ENABLED", "true")
        }
        release {
            buildConfigField("boolean", "SAMPLE_DATA_ENABLED", "false")
            if (hasCompleteReleaseSigningConfiguration) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

kotlin {
    jvmToolchain(17)
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.android)
    implementation(libs.play.services.location)
    implementation(libs.maplibre.android.sdk)
    implementation(libs.okhttp)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
}
