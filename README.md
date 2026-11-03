# 카링 (CARLING)

차를 탈수록, 나를 닮아 자라는 차 안의 친구 — Pleos Connect 차량용 펫 육성 앱.

- 기능별 개발 가능 여부와 구현 현황: [docs/feature-feasibility.md](docs/feature-feasibility.md)
- 처음부터 끝까지 테스트하는 순서: [docs/test-guide.md](docs/test-guide.md)

## 주요 기능 (v0.3.0)

- **첫 5분:** 알 깨우기 → 성격(나른/도도/루미) → 짧은 질문 2개 → 이름 짓기 → 자라는 방법 안내 → 위치 사용 동의
- **캐릭터:** 설계서 디자인(토끼 귀, 머리 새싹, 반투명하게 빛나는 몸)을 벡터로 그림. 밤하늘 테마. 정차 화면의 빈 공간을 마음대로 돌아다님 (`ui/Wander.kt`, 주행 중에는 움직이지 않음)
  - 행동 8종: 휘어진 길로 걷기, 달리기, 큰 점프, 지그재그, 빙글 돌기, 두리번거리기, 몸 흔들기, 낮잠
  - 성격별 성향: 도도는 활발(달리기·점프), 나른은 느긋(낮잠·천천히), 루미는 차분(걷기·두리번)
  - 누르면 폴짝 뛰며 하트와 한마디 (친밀도 +1, 하루 5회까지) / 빠르게 세 번 누르면 어지러워함
- **주행/정차 모드:** P단이 아니면 터치 없는 작은 위젯과 한 줄만 표시 (`distractionOptimized`)
- **주행 기록:** 기어 P→D→P를 주행 1회로 기록하고, 주차하면 도착 리포트를 보여줌. 주행 도중 앱이 종료돼도 이어서 기록
- **자동 시작:** 시동(부팅) 때 앱이 스스로 깨어나 포그라운드 서비스로 대기함. 카링 화면을 열지 않아도 그날 주행이 기록됨 (`trip/BootReceiver.kt`, `trip/TripService.kt`)
- **부드러운 운전 칭찬:** 급가속·급감속이 없던 주행에만 칭찬 (감점 없음)
- **세로 창 대응:** Pleos에서 앱 두 개를 나란히 띄우면 세로 배치로 바뀜
- **성장:** 함께한 날 7일이면 성장, 30일이면 첫 진화 (빛 4종 × 길 2종 = 8갈래)
  - 빛: 시동 시각(새벽/햇살/노을/별빛)
  - 길: NaviHelper 위치로 처음 간 곳과 다시 찾은 곳을 판정 (격자 해시만 저장)
- **돌보기:** 친밀도, 포만감, 하루 1회 밥, 충전하면 포만감 회복, 첫 드라이브 모자, 추억 카드
- **보상 이유 설명:** "노을빛 시간에 29번이나 같이 달려서 제 몸에 노을빛이 번졌어요!"

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
4. 첫 실행 때 "차량 속도 읽기" 권한을 허용한다 (부드러운 운전 칭찬용).

### 공모전 제출용 release APK
```bash
./gradlew submissionApk -PteamName=팀명
```
`apk/팀명_CARLING_release.apk`가 만들어진다. 서명하려면 프로젝트 루트에 `keystore.properties`와 `keystore/carling-release.jks`가 있어야 한다. 이 두 파일은 커밋하지 않으며, **잃어버리면 같은 앱으로 업데이트할 수 없으니 따로 백업한다.**

### 데모용 개발자 도구 (debug 빌드 전용)
홈 화면 왼쪽 위의 🛠를 누르면 열린다.
- 빛·길을 고른 뒤 **가상 주행 +1일 / +6일 / 30일까지**를 누르면 진화까지 바로 볼 수 있다.
- **다음 날로**는 주행 없이 날짜만 넘긴다. **처음부터**는 모든 데이터를 초기화한다.

## 구조

| 파일 | 역할 |
|---|---|
| `CarlingApplication.kt` | 차량 신호와 주행 기록을 앱 전체 범위에서 유지 |
| `vehicle/VehicleSignals.kt` | Vehicle SDK: 기어, 충전구, 충전 스위치, SOC, 주행거리 |
| `vehicle/SpeedMonitor.kt` | AAOS `PERF_VEHICLE_SPEED`: 급가속·급감속 횟수 |
| `navi/NaviPlaces.kt` | NaviHelper SDK: 주행 종료 위치를 격자 해시로 변환, 목적지 이름 |
| `trip/TripTracker.kt` | P→D→P를 주행 1회로 묶음, 충전 세션 감지 |
| `game/Growth.kt` | 빛·길·성장 단계·진화 규칙 (순수 함수, 단위 테스트) |
| `game/GameClock.kt`, `game/DevSimulator.kt` | 시간 건너뛰기, 가상 주행 (데모용) |
| `data/CarlingStore.kt` | 상태 저장(JSON)과 하루 루틴·주행 보상 규칙 |
| `ui/CarlingApp.kt` | 화면 흐름 (첫 5분 → 홈), 주행 중 위젯 |
| `ui/Onboarding.kt` | 알, 성격, 질문 2개, 이름, 성장 안내, 위치 동의 |
| `ui/HomeScreen.kt` | 홈, 도착 리포트, 추억 카드, 개발자 도구 |
| `ui/Theme.kt` | 밤하늘 테마, 좁은 창(세로) 판정 |
| `ui/CarlingCharacter.kt` | 설계서 캐릭터 벡터 그림 (정식 일러스트로 교체 가능) |
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
