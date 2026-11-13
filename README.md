# My Carling (마이카링)

매일의 운행을 트립카드로 기록하고, 함께한 여정과 운행 패턴에 따라 나만의 캐릭터 '카링'이 성장하는 차량 동반자 앱.

## APK

[`apk/TeamCarling_MyCarling_release.apk`](apk/TeamCarling_MyCarling_release.apk)

## 실행 방법

1. Pleos Connect Emulator AVD를 **Cold boot**로 켠다.
2. APK를 설치한다.
   ```bash
   adb install -r apk/TeamCarling_MyCarling_release.apk
   ```
3. 앱 목록에서 **마이카링**을 연다.
4. Virtual Console로 기어(P / D)를 바꿔 운행을 만든다.
   ```bash
   adb forward tcp:8088 tcp:8088
   ```
   브라우저에서 http://127.0.0.1:8088/ → Gear Selection

소스에서 빌드: `./gradlew installDebug` (JDK 17)

## 테스트 환경

- Pleos Connect Emulator v2.1.0 (Android 14, API 34)
- 화면 규격: 2560 × 1440 px, 160 dpi
- Vehicle SDK 2.3.2, TextToSpeech SDK 3.3.1, FusedLocation SDK 1.1.3
- minSdk 28, Kotlin
- 필요한 설정: AVD Cold boot, 처음 실행 시 권한 허용
