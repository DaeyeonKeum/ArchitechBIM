plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.poscoenc.bim.sitemeasure"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.poscoenc.bim.sitemeasure"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "0.9"
    }

    // 시험용 고정 서명: 새 빌드를 받아도 지우지 않고 덮어 설치할 수 있게 한다 (정식 배포용 키 아님)
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // ARCore (Depth API 포함)
    implementation("com.google.ar:core:1.44.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("com.google.android.material:material:1.12.0")
    // 사진 물량 화면(웹 시제품 ver 0.9)을 앱 안에서 연다
    implementation("androidx.webkit:webkit:1.11.0")
}
