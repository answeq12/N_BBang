plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.bergi.nbang_v1"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bergi.nbang_v1"
        minSdk = 24
        targetSdk = 36
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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))

    // Firebase 및 Google Play Services 라이브러리들
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx") // 'FirebaseFirestore' 오류 해결
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0") // 'GoogleSignIn' 오류 해결

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // 코루틴: 비동기 작업을 쉽게 처리하기 위해 사용
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
// Gson: JSON 문자열을 Kotlin 객체로 쉽게 변환하기 위해 사용
    implementation("com.google.code.gson:gson:2.10.1")
}