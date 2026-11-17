plugins {
    alias(libs.plugins.android.application)
}

// Configure JVM toolchain for AGP to use local JDK 24
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}

android {
    namespace = "com.example.watchai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.watchai"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            // org.json is part of the Android framework, so its methods throw
            // "not mocked" in plain JVM tests. AiResponseTest exercises real
            // JSONObject construction, so it needs a real implementation.
            isReturnDefaultValues = false
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.fragment)
    implementation(libs.androidx.core.ktx)

    // Security - EncryptedSharedPreferences
    implementation(libs.androidx.security.crypto)

    // OkHttp
    implementation(libs.okhttp)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime)

    testImplementation(libs.junit)
    // Real org.json implementation for JVM unit tests; the Android stub
    // throws on every method. Matches the android.jar semantics closely
    // enough that AiResponseTest exercises genuine parsing behavior.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
