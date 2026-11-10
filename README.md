# 카링월드 (CARLING)

함께 달린 만큼, 우리의 세계가 자란다 — Pleos Connect 차량 전용 힐링 게임.

- 구현 명세와 확인이 필요한 것: [docs/carling-world-spec.md](docs/carling-world-spec.md)
- 테스트 순서: [docs/test-guide.md](docs/test-guide.md)
- SDK 조사 기록: [docs/feature-feasibility.md](docs/feature-feasibility.md)

## 주요 기능

- **자동 트립카드:** 거리 2km 이상 + 실제 이동 5분 이상인 운행이 끝나면 카드·XP·아이템이 자동 저장됨
- **레벨과 진화:** Lv1~99. 최소 레벨·친밀도·퀘스트를 모두 채우면 진화 (젤리 알 → 아기 카링 → 계열 진화 1)
- **자동으로 꾸며지는 공간:** 방 24종 + 거실·주방·화장실·작업실 12종씩, 카링이 있는 공간부터 정해진 자리에 놓이고 처음 놓일 때 친밀도가 오름
- **2층 집과 공간 이동:** 거실이 중심(계단→2층 방·작업실, 아치→주방, 문→화장실). Lv10부터 공간 메뉴, 레벨마다 집 안 공간이 열림. 축소하면 문·계단 이름표가 보이고 눌러서 걸어가 이동
- **가구 교감:** 가구를 누르면 카링이 다가가 앉기·자기·TV 보기·커피 마시기·춤추기 등
- **카링월드 지도:** 집·정원·해변·숲·하늘 전망대·하늘 섬, 열리지 않은 곳은 안개
- **도감과 기록:** 공간별 아이템 도감, 지난 트립카드 전체(오늘·7·14·30일 필터)
- **카링의 생활:** 침대에서 자고, 스탠드를 켜고, 라디오에 춤춤. 말랑 젤리 몸과 옹알이 목소리
- **안전:** 운행 중이거나 주차를 확인할 수 없으면 게임 화면을 그리지 않음. 경로·위치는 저장하지 않음

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
홈 화면 오른쪽 위 🛠. 여기서 만든 카드에는 '시뮬레이션 데이터'가 표시된다.

## 구조

| 파일 | 역할 |
|---|---|
| `game/Growth.kt` | 유효 운행, XP, 레벨, 진화, 퀘스트 규칙 (단위 테스트) |
| `game/RoomItems.kt`, `game/Spaces.kt`, `game/Interactions.kt` | 아이템 72종, 공간·층·출입구·배경 가구·월드 지역, 가구 교감 |
| `data/CarlingStore.kt` | 운행 결과를 한 번에 저장, 진화 |
| `trip/TripTracker.kt` | P→주행→P를 운행 1회로 기록 |
| `vehicle/` | 기어·주행거리, 차속(이동시간·급조작), 주행 제한 |
| `ui/Room.kt`, `ui/Wander.kt`, `ui/Jelly.kt` | 방 장면, 카링 이동·반응, 말랑 몸 |
| `ui/HomeScreen.kt` | 홈, 주차 후 연출, 도움말(`HelpSections`), 개발자 도구 |
| `ui/TripCards.kt`, `ui/GrowthScreen.kt`, `ui/SpacesScreen.kt`, `ui/WorldMap.kt` | 트립카드, 성장·진화·계열 선택, 공간 메뉴·도감·출입구 선택, 카링월드 지도 |
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
