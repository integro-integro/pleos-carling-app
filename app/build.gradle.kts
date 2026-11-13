import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 릴리스 서명 정보 — keystore.properties (커밋하지 않음). 없으면 release는 서명되지 않는다.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "work.integro.carling"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "work.integro.carling"
        // Pleos Connect 요구사항: minSdk 28 이상 (서명 키 로테이션)
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.3.0"
    }

    // AAOS 표준 차량 API (차속: CarPropertyManager)
    useLibrary("android.car")

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // 코드 축소(R8) 끔: 켜면 Kotlin 표준 라이브러리 접근이 깨져 실행 즉시 종료됨
            // (IllegalAccessError, 2026-10-31 에뮬레이터 확인). 공모전 제출에는 용량 차이가 문제되지 않는다.
            optimization {
                enable = false
            }
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

android {
    // 도메인 단위 테스트에서 android.util.Log 호출이 실패하지 않게
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.fusedlocation)
    implementation(libs.pleos.texttospeech)
    // 운행일지 QR(폰으로 찍으면 메일 쓰기 화면이 열린다)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}

// 공모전 제출용: apk/팀명_앱이름_release.apk 로 복사 (기본 TeamCarling_MyCarling_release.apk).
// 다른 이름: ./gradlew submissionApk -PteamName=팀명 -PappName=앱이름
tasks.register<Copy>("submissionApk") {
    val team = providers.gradleProperty("teamName").orElse("TeamCarling")
    val app = providers.gradleProperty("appName").orElse("MyCarling")
    val fileName = team.zip(app) { t, a -> "${t}_${a}_release.apk" }
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("apk"))
    rename { fileName.get() }
}

// 카링 목소리 샘플을 들어보려면: ./gradlew testDebugUnitTest -Pbabble.out=폴더
tasks.withType<Test>().configureEach {
    systemProperty("babble.out", (project.findProperty("babble.out") ?: "").toString())
}
