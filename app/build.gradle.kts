plugins {

    alias(libs.plugins.hilt) // libs.versions.toml에 정의되어 있다면
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.application)
    kotlin("kapt")

}

android {

    namespace = "com.twobecome.phonephoto"
    compileSdk = 36

    defaultConfig {

        applicationId = "com.twobecome.phonephoto"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        if (project.hasProperty("android.injected.invoked.from.ide"))   manifestPlaceholders["targetSdkVersion"] = "33"

        buildFeatures {
            viewBinding = true
        }

    }

    buildTypes {

        release {

            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro"
            )

        }

    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    hilt {
        enableAggregatingTask = false
    }
}

dependencies {

    implementation(libs.dagger.hilt)
    kapt(libs.dagger.hilt.compiler)
    implementation(libs.javapoet)
    implementation(libs.androidx.work.runtime.ktx)


    // Hilt for WorkManager
    implementation("androidx.hilt:hilt-work:1.2.0")

    // kapt "androidx.hilt:hilt-compiler:1.2.0" // 이 줄을 삭제하거나 주석 처리합니다.
    testImplementation(libs.junit)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.graphics)
    debugImplementation(libs.androidx.ui.tooling)
    androidTestImplementation(libs.androidx.junit)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

}