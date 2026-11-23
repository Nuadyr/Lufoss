plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "io.github.nuadyr.lufoss"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.nuadyr.lufoss"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"
    }

    // Release signing is read from the environment so no secret lives in the repository.
    // CI provides it when the secrets are set.
    // Without these variables the release APK is left unsigned, which is what F-Droid expects (it signs its own).
    val keystorePath = System.getenv("LUFOSS_KEYSTORE_FILE")
    if (!keystorePath.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("LUFOSS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LUFOSS_KEY_ALIAS")
                keyPassword = System.getenv("LUFOSS_KEY_PASSWORD")
            }
        }
    }

    // Keeps the APK free of the encrypted dependency list that Google tooling adds. F-Droid asks for this.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Unit tests run against the release build type, so no debug variant is ever compiled.
    testBuildType = "release"

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ktlint {
    outputToConsole.set(true)
    ignoreFailures.set(false)
    filter {
        exclude("**/build/**")
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
