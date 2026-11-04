# 카링 (CARLING)

차를 탈수록, 나를 닮아 자라는 차 안의 친구 — Pleos Connect 차량용 펫 육성 앱.

- 기능별 개발 가능 여부와 구현 현황: [docs/feature-feasibility.md](docs/feature-feasibility.md)
- 성장 규칙과 완전체 도달 계산: [docs/growth-balance.md](docs/growth-balance.md)
- 처음부터 끝까지 테스트하는 순서: [docs/test-guide.md](docs/test-guide.md)

## 주요 기능 (v0.3.0)

- **첫 만남:** 첫 운전 → 트립카드 #001 → 알을 네 번 눌러 깨우기 → 이름 짓기 → 첫 쓰다듬기 → 안내 → 위치 사용 동의
- **성격:** 고르지 않고 첫 운전 시간대로 정해짐 (새벽·낮은 도도, 노을은 나른, 밤은 루미). 말투와 표정, 움직임 성향만 다름
- **캐릭터:** 단계·계열별 일러스트. 정차 화면의 빈 공간을 돌아다님 (`ui/Wander.kt`, 주행 중에는 움직이지 않음)
  - 행동 8종: 휘어진 길로 걷기, 달리기, 큰 점프, 지그재그, 빙글 돌기, 두리번거리기, 몸 흔들기, 낮잠
  - 누르면 폴짝 뛰며 하트와 한마디 (친밀도 +1, 하루 5회까지) / 빠르게 세 번 누르면 어지러워함
- **주행/정차 모드:** P단이 아니면 터치 없는 작은 위젯과 한 줄만 표시 (`distractionOptimized`). 날짜가 바뀐 뒤 첫 운전에는 날씨 인사
- **주행 기록:** 기어 P→D→P를 주행 1회로 기록하고, 주차하면 새 트립카드를 보여줌. 주행 도중 앱이 종료돼도 이어서 기록
- **자동 시작:** 시동(부팅) 때 앱이 스스로 깨어나 포그라운드 서비스로 대기함. 카링 화면을 열지 않아도 그날 주행이 기록됨 (`trip/BootReceiver.kt`, `trip/TripService.kt`)
- **세로 창 대응:** Pleos에서 앱 두 개를 나란히 띄우면 세로 배치로 바뀜
- **성장:** 180일 동안 성장 6번 + 진화 3번. 단계 조건은 날짜 + 누적 XP + 누적 MP, 진화는 진화카드와 퀘스트가 더 필요함 (`game/Growth.kt`, [자세한 규칙](docs/growth-balance.md))
  - XP: 트립·안전운전·충전·교감으로 하루 최대 50
  - MP: 트립카드 저장으로 획득 (새 길 +3, 익숙한 길 +1, 운행일마다 2장까지)
  - 계열 5종: 성장 2에 도달할 때 운전 습관 점수로 결정 (바람·빛·나무·물·흙)
- **트립카드:** 목적지별 최근 20곳, 저장한 트립, 모든 트립이 남는 Drive Log
- **지도:** 실제 경로를 기록한 길은 선, 기록이 없는 곳은 도착지 불빛으로 표시. 자주 갈수록 밝아짐 (`ui/CarlingMap.kt`, `data/MapRoads.kt`)
- **돌보기:** 쓰다듬기, 밥 주기, 놀아주기, 말 걸기, 충전 반응. 친밀도·포만감은 단계 조건이 아님
- **꾸미기:** 성장 1부터 아이템 10종, 배경 4종, 시각에 따른 낮밤
- **지역 특산물:** 제주도·강원도·부산·대구·전주 방문 시 지역마다 하루 1개. 배고플 때 먹이면 보너스 XP

## 실행 방법

1. Pleos Connect Emulator(v2.1.0, API 34) AVD를 실행한다. 반드시 Cold boot로 켠다.
2. 빌드하고 설치한다. JDK 17 이상이 필요하다 (Android Studio 내장 JDK 사용 가능).
   ```bash
   ./gradlew installDebug
   ```
3. 차량 상태는 Pleos Connect Virtual Console로 조작한다.
   ```bash
   adb forward tcp:8088 tcp:8088
   ```
   브라우저에서 http://127.0.0.1:8088/ 을 열고 Emulation Controls로 들어간다.
   - Motion > Gear Selection: P / D 전환
   - Energy > Charge Port / Charge Switch: 충전
4. 위치 동의 뒤 뜨는 시스템 권한 창을 허용한다 (급가감속 판정에 쓰는 차량 속도가 '위치' 권한으로 함께 표시된다).

### 공모전 제출용 release APK
```bash
./gradlew submissionApk -PteamName=팀명
```
`apk/팀명_CARLING_release.apk`가 만들어진다. 서명하려면 프로젝트 루트에 `keystore.properties`와 `keystore/carling-release.jks`가 있어야 한다. 이 두 파일은 커밋하지 않으며, **잃어버리면 같은 앱으로 업데이트할 수 없으니 따로 백업한다.**

### 데모용 개발자 도구 (debug 빌드 전용)
홈 화면 왼쪽 위의 🛠를 누르면 열린다.
- 계열을 고른 뒤 **+1일 / +7일 / 진화1까지 / 진화2까지 / 완성형까지**를 누르면 가상 주행으로 단계를 쌓는다.
- **다음 날로**는 주행 없이 날짜만 넘긴다. **D1부터**는 이름과 설정을 두고 성장만 되돌리고, **알부터**는 모든 데이터를 초기화한다.
- 외형 미리보기, 낮밤, 변화 장면 다시 보기, 특산물·배고픔, 날씨 인사 준비, 새 길 주행도 있다.

## 구조

| 파일 | 역할 |
|---|---|
| `CarlingApplication.kt` | 차량 신호와 주행 기록을 앱 전체 범위에서 유지 |
| `vehicle/VehicleSignals.kt` | Vehicle SDK: 기어, 충전구, 충전 스위치, SOC, 주행거리 |
| `vehicle/SpeedMonitor.kt` | AAOS `PERF_VEHICLE_SPEED`: 급가속·급감속 횟수 |
| `navi/NaviPlaces.kt` | NaviHelper SDK: 주행 종료 위치를 격자 해시로 변환, 목적지 이름 |
| `trip/TripTracker.kt` | P→D→P를 주행 1회로 묶음, 충전 세션 감지 |
| `game/Growth.kt` | 빛·계열·성장 단계·진화·XP·MP 규칙 (순수 함수, 단위 테스트) |
| `game/GameClock.kt`, `game/DevSimulator.kt` | 시간 건너뛰기, 가상 주행 (데모용) |
| `game/Weather.kt`, `weather/WeatherClient.kt` | 날씨 인사, Open-Meteo 조회 |
| `data/CarlingStore.kt` | 상태 저장(JSON)과 하루 루틴·주행 보상 규칙 |
| `data/MapRoads.kt` | 트립을 지도의 길로 반영, 경로 단순화 |
| `ui/CarlingApp.kt` | 화면 흐름 (첫 만남 → 홈), 주행 중 위젯 |
| `ui/Onboarding.kt` | 알, 이름, 첫 교감, 성장 안내, 위치 동의 |
| `ui/HomeScreen.kt` | 홈, 상태 패널, 도움말, 개발자 도구 |
| `ui/TripCards.kt`, `ui/CarlingMap.kt` | 트립카드, 지도 |
| `ui/Items.kt`, `ui/StageChange.kt` | 아이템·특산물, 성장·진화 연출 |
| `ui/Theme.kt`, `ui/SceneBackground.kt` | 테마, 배경, 좁은 창(세로) 판정 |
| `ui/CarlingCharacter.kt`, `ui/CarlingArt.kt` | 캐릭터 그림, 단계·계열별 일러스트 |
| `voice/Voice.kt` | 음성 한마디 (현재 에뮬레이터에서 TTS 초기화 실패, 무음 동작) |

## 테스트

```bash
./gradlew testDebugUnitTest
```

## 테스트 환경

- Pleos Connect Emulator v2.1.0 (Android 14)
- Vehicle SDK 2.3.2, NaviHelper SDK 3.0.5
- AGP 9.4.1, Gradle 9.6.0, Kotlin 2.2.10, minSdk 28

## 사용 권한

| 권한 | 용도 |
|---|---|
| `pleos.car.permission.CAR_DRIVE` | 기어, 주행거리 |
| `pleos.car.permission.CAR_ENERGY` | 충전구, 충전 스위치, 배터리 |
| `pleos.car.permission.NAVI_ROUTE` | 현재 위치, 목적지 정보 |
| `android.car.permission.CAR_SPEED` | 차속 (런타임 권한) |
| `android.permission.INTERNET` | 날씨 조회 (Open-Meteo) |
| `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` | 자동 시작과 상주 서비스 |
