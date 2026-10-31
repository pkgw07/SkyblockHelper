# SkyblockHelper

Hypixel SkyBlock 도우미 — **Windows 데스크톱 앱 + Android 앱**, 두 기기가 서로 연동됩니다.

게임 런처(Lunar Client, Modrinth App) 같은 어두운 화면에 마인크래프트 색상·툴팁·픽셀 글꼴을 입혔습니다.

## 주요 기능

| 분류 | 기능 |
|---|---|
| 🔔 알림 | **포지 완료**, 내 경매 판매/종료/만료, 바자 가격 알림, 이벤트 시작 전 알림, **새 패치노트** |
| 🌙 백그라운드 | 창을 닫아도 트레이(PC)/WorkManager(휴대폰)에서 계속 확인 · 설정에서 켜고 끄기 · 컴퓨터 시작 시 자동 실행 |
| 🔄 기기 연동 | 계정, 가격 알림, 관심 목록, 제작 목표, 알림 설정을 PC ↔ 휴대폰 자동 동기화 (연동 코드 / QR) |
| ⬆ 자동 업데이트 | 새 버전이 Releases 에 올라오면 내려받아 설치 (Windows .exe / Android .apk) |
| 🧭 진행 방향 추천 | 위키의 해금 조건과 내 프로필을 비교한 진행 트리 (지역 · 채광/포지 · 던전 · 슬레이어) + 다음 목표 추천 |
| 📰 패치노트 | 공식 위키 Changelog 의 최신 패치노트 목록과 본문, 공식 포럼 원문 링크 |
| 🏠 대시보드 | 스카이블럭 날짜·계절, 포지 진행 상황, 다가오는 이벤트, 지갑/은행/레벨, 현 시장·장관 퍼크 |
| 👤 프로필 | 스킬, 슬레이어, 던전/클래스, HotM, 에센스, 컬렉션 |
| 📈 바자 | 플립 추천(수수료 반영), NPC 플립, 관심 목록, 가격 알림 |
| 🔨 경매장 | 최저 BIN 검색(전체 경매 스캔), 아이템 툴팁 그대로 보기, `/viewauction` 복사 |
| ♲ 아이언맨 | **제작 플래너**(기본 재료까지 분해 + 자루 재고 반영 + 포지 시간), 자루 NPC 판매 가치 |

## 데이터 출처

| 데이터 | 출처 |
|---|---|
| 시세, 프로필, 경매, 선거, 아이템, 스킬 경험치표, 컬렉션 | **Hypixel 공식 API** (`api.hypixel.net`) — 실시간 |
| 닉네임·스킨 | **Mojang 공식 API** |
| 제작법, 포지 레시피·시간, 이벤트 일정, 던전·슬레이어·HotM 경험치표, 진행 조건, 수수료·포지 규칙, 패치노트 | **공식 Hypixel SkyBlock 위키** (`hypixelskyblock.minecraft.wiki`, CC BY-NC-SA 3.0) |

- 위키 게임 데이터는 `tools/wiki_data.py` 가 만들고, GitHub Actions(`data.yml`)가 **매일** 새로 만들어 `data` 브랜치에 올립니다.
  앱은 그 파일을 받아 쓰므로, 게임 패치가 위키에 반영되면 앱 업데이트 없이 따라갑니다.
- 패치노트는 앱이 위키에서 직접 읽습니다.

## 설치

[Releases](https://github.com/pkgw07/SkyblockHelper/releases) 에서 받으세요.

- **Windows**: `SkyblockHelper-x.y.z.exe` 실행 → 설치 (Java 설치 필요 없음). 이후 업데이트는 자동.
- **Android**: `SkyblockHelper-x.y.z.apk` 를 휴대폰에서 열어 설치 ("출처를 알 수 없는 앱" 허용 필요)

### 처음 설정

1. [developer.hypixel.net](https://developer.hypixel.net/) 에서 API 키 발급
2. 앱 → 설정 → **계정 연결**: 마인크래프트 닉네임 + API 키
3. 게임에서 `/profile` → **API Settings** 에서 Skills, Inventory, Collections, Banking 켜기
4. (선택) **기기 연동**: GitHub 에서 `gist` 권한 토큰을 만들어 PC 에 입력 → 휴대폰에서 PC 에 표시된 QR 스캔

## 개발

```
core/        공통 로직 (Hypixel·위키 API, 계산, 알림 엔진, 동기화, 업데이트) — Kotlin Multiplatform
ui/          공통 화면 (Compose Multiplatform) — PC 와 휴대폰이 같은 화면 코드를 씀
desktopApp/  Windows/macOS/Linux 앱 (트레이, 자동 실행, 설치 파일)
androidApp/  Android 앱 (WorkManager 백그라운드, 알림, QR 스캔)
tools/       위키 데이터·아이콘 생성 스크립트
```

```bash
./gradlew :desktopApp:run                 # PC 앱 실행
./gradlew :core:desktopTest               # 테스트
./gradlew :desktopApp:packageExe          # Windows 설치 파일 (Windows 에서)
./gradlew :androidApp:assembleRelease     # Android APK (Android SDK 필요)
python3 tools/wiki_data.py                # 위키에서 게임 데이터 다시 만들기
```

버전은 `gradle.properties` 의 `appVersion`. `git tag v1.2.0 && git push --tags` 하면 GitHub Actions 가
Windows 설치 파일과 APK 를 만들어 Releases 에 올리고, 설치된 앱들이 자동으로 업데이트합니다.

## 라이선스 / 고지

- 게임 정보: [Hypixel SkyBlock Wiki](https://hypixelskyblock.minecraft.wiki/) — CC BY-NC-SA 3.0. 이 프로젝트는 비상업적 팬 도구입니다.
- 글꼴: [Pretendard](https://github.com/orioncactus/pretendard), [Galmuri](https://github.com/quiple/galmuri) — SIL Open Font License (`docs/licenses`)
- Hypixel 및 Mojang 과 관련 없는 비공식 팬 프로젝트입니다. 게임 규칙상 허용되지 않는 자동화(매크로) 기능은 없습니다.
