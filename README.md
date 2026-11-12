# 마이카링 (My Carling)

매일의 운행이 트립카드로 남고, 완성한 카드가 카링의 성장·개성과 운행 리포트로 돌아오는 Pleos Connect 차량 동반자 앱.

- 구현 명세와 확인이 필요한 것: [docs/carling-world-spec.md](docs/carling-world-spec.md)
- 테스트 순서: [docs/test-guide.md](docs/test-guide.md)
- SDK 조사 기록: [docs/feature-feasibility.md](docs/feature-feasibility.md)

## 주요 기능

- **트립카드:** 1km·3분 이상 달리고 P로 바꾸면 임시 카드가 바로 뜬다. 운행 목적 하나를 고르면 완성. 5분 안에 다시 출발하면 같은 여정, P 5분 유지면 확정
- **보상 1회:** 운행 확정 + 카드 완성 시 성장 포인트(10+km+분, 최대 150)와 럭키박스. 같은 운행에는 한 번만
- **카링 성장:** 젤리알 → 깨어난 알 → 아기카링 → 산들카링 → 마이카링, 단계마다 카링과 차가 함께 자란다
- **개성:** 최근 카드 10장으로 몸 색(시간대)·장식(목적)·심장빛(운전점수), 바뀐 근거를 보여준다
- **럭키박스:** 보너스 포인트, 수집 배지 6종, 자동차 꾸미기 아이템 8종
- **운행 리포트:** 핵심 발견, 운행 현황과 증감, 날마다 달린 거리, 목적별 운행, 생활 패턴, 성장 내역
- **안전:** 주행 중엔 달리는 카링과 거리·시간만. 경로·위치는 저장하지 않음

## 실행 방법

1. Pleos Connect Emulator(v2.1.0, API 34) AVD를 Cold boot로 켠다.
2. JDK 17로 빌드하고 설치한다.
   ```bash
   JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew installDebug
   ```
3. 차량 상태는 Virtual Console로 조작한다.
   ```bash
   adb forward tcp:8088 tcp:8088
   ```
   브라우저에서 http://127.0.0.1:8088/ 을 열고 Gear Selection에서 P / D를 바꾼다.

### 공모전 제출용 release APK
```bash
./gradlew submissionApk -PteamName=팀명
```
`apk/팀명_CARLING_release.apk`가 만들어진다. 서명하려면 프로젝트 루트에 `keystore.properties`와 `keystore/carling-release.jks`가 있어야 한다. 이 두 파일은 커밋하지 않으며, **잃어버리면 같은 앱으로 업데이트할 수 없으니 따로 백업한다.**

### 데모용 개발자 도구 (debug 빌드 전용)
홈 화면 오른쪽 위 🛠. 여기서 만든 운행에는 '시뮬레이션'이 표시된다.

## 구조

| 파일 | 역할 |
|---|---|
| `game/Rules.kt` | 수치 상수, 목적·시간대·단계·배지·자동차 아이템, 개성·럭키박스 규칙 |
| `game/TripCardRules.kt` | 운행·카드 상태, 미완성 카드 재안내·만료, 기본 목적 제안 |
| `game/Report.kt` | 리포트 집계와 핵심 발견 문장 |
| `data/CarlingStore.kt` | 운행 기록·병합·확정, 카드 완성, 보상 원장(1회 지급), 단계·개성 갱신 |
| `trip/TripTracker.kt` | 기어 신호로 기록·주차·재출발·확정, D 화면 실시간 수치 |
| `vehicle/` | 기어·주행거리, 차속(이동시간·급조작), 주행 제한 |
| `ui/HomeScreen.kt` | P 홈(카링 탭), 카드 시트·재안내, 단계 연출, 동의, 도움말(`HelpSections`), 시뮬레이터 |
| `ui/TripCards.kt`, `ui/ReportScreen.kt`, `ui/LuckyBox.kt` | 승차권 카드·시트·목록, 리포트, 럭키박스 |
| `ui/KaringArt.kt`, `ui/Backdrop.kt`, `ui/CarlingApp.kt` | 카링 그림과 개성 층, 배경, P/D 화면 전환 |
| `voice/` | 음성 한마디, 옹알이 목소리 |

## 테스트

```bash
./gradlew testDebugUnitTest
```

## 테스트 환경

- Pleos Connect Emulator v2.1.0 (Android 14)
- Vehicle SDK 2.3.2, TextToSpeech SDK 3.3.1
- AGP 9.4.1, Gradle 9.6.0, Kotlin 2.2.10, minSdk 28

## 사용 권한

| 권한 | 용도 |
|---|---|
| `pleos.car.permission.CAR_DRIVE`, `CAR_INFO` | 기어, 주행거리 |
| `pleos.car.permission.TTS_SERVICE` | 음성 한마디 |
| `android.car.permission.CAR_SPEED` | 실제 이동시간, 급조작 (런타임 권한) |
| `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` | 자동 시작과 상주 서비스 |
