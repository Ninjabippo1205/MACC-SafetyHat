plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android) apply true
}

android {
    namespace = "com.safetyhat.macc"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.safetyhat.macc"
        minSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.places.v260)
    implementation(libs.androidx.fragment)
    implementation(libs.okhttp)
    implementation(libs.jbcrypt)
    implementation(libs.code.scanner)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.camera26)
    implementation(libs.androidx.camera.lifecycle.v110)
    implementation(libs.androidx.camera.core)

    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    implementation(libs.places)
    implementation(libs.vision.common)
    implementation(libs.play.services.mlkit.face.detection)
    implementation("com.google.ar:core:1.45.0")
    implementation("io.github.sceneview:sceneview:2.2.1")
    implementation("io.github.sceneview:arsceneview:2.2.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
    implementation("io.github.classgraph:classgraph:4.8.157")

    implementation(platform("androidx.compose:compose-bom:2024.01.00"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")

    // Debugging tools
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

