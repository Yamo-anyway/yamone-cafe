# Yamone Cafe · 개인용 안드로이드 브라우저

네이버 카페를 앱 안에서 읽으면서, 별도 브라우저가 지정한 카페 주소를 주기적으로 새로 불러오는 개인용 앱입니다. 앱 표기는 **Yamone Cafe**, 실제 GitHub 저장소 이름은 `Yamo-anyway/yamne-cafe`입니다.

## 기능

- 앱 내 WebView에서 직접 네이버 로그인, 카페 및 게시글 열람.
- 화면의 브라우저와 자동 실행용 WebView를 분리. 자동 실행은 현재 글과 스크롤을 건드리지 않습니다.
- 최초 자동 로딩은 즉시 실행. 기본 **31분 00초~35분 00초** 중 정수 초를 매번 선택. 최소·최대 분/초 직접 설정, 같은 값은 고정 간격.
- 일정 기준은 자동 로딩 **시작 시각**. 수동 열람·현재 화면 새로고침은 자동 일정에 영향을 주지 않습니다.
- 시작/일시정지/재개/중지. 재개는 일시정지 당시 남은 시간부터 계속합니다.
- 사용자 시작 Foreground Service, 시스템 알람, 로딩 중에만 최대 75초 partial wake lock. 외부 앱 실행·접근성·화면 덮기 권한은 사용하지 않습니다.
- 마지막 페이지 주소 및 가능한 범위의 스크롤 복원. 브라우저 프로세스가 유지되면 읽던 화면 그대로 돌아옵니다.
- 최근 100건 실행 기록·기록 복사·삭제. 기기 안에만 저장.
- 민트/차콜 UI, 시스템 다크 모드. 브라우저 / 자동 실행 / 기록 3개 탭.

## 처음 사용

1. 개인용 APK를 안드로이드 휴대전화에 설치합니다. 최소 Android 8.0(API 26).
2. **브라우저 → 로그인**에서 직접 로그인하고 원하는 카페를 엽니다.
3. **자동 실행 → 현재 카페 주소 가져오기**, 간격 확인 후 저장합니다.
4. 같은 화면의 **알림**, **정확한 알람**, **배터리 제한 해제** 설정을 확인합니다.
5. **자동 실행 시작**. 첫 로딩 기록 확인 후 홈 화면으로 나가거나 다른 앱을 사용합니다.
6. 화면을 다시 열어도 마지막 페이지를 유지하며 자동 일정은 초기화되지 않습니다.

설정 변경은 자동 실행을 **중지**한 뒤 가능합니다. 재부팅 자동 재개는 선택 사항이며 기본값은 꺼짐입니다.

## 꼭 구분할 사항

- `로드`는 WebView의 페이지 완료 콜백입니다. 로그인 유효성, 서버의 처리 결과, 방문횟수를 검증한 수치가 아닙니다.
- 네이버가 임베디드 로그인이나 백그라운드 페이지 동작을 제한하면 동작하지 않을 수 있습니다. 캡차·접근 제한·인증서 오류를 우회하지 않습니다.
- OS 강제 종료, 제조사 절전, 네트워크 차단 상태에서 연속 실행·초 단위 정확도를 보장하지 않습니다. 정확한 알람 권한이 없으면 지연 가능한 알람을 사용합니다.
- 로그인 페이지로 이동하거나 HTTP 401/403/429를 받으면 자동 실행을 일시정지합니다. 브라우저에서 직접 확인 후 재개하세요.
- 앱에서 **중지**하거나 Android 설정에서 **강제 중지**하면 자동 동작을 계속하지 않습니다. 최근 앱에서 화면을 치우는 것과 강제 중지는 다릅니다.
- 페이지의 입력 중인 글, 동적 상태, 정확한 스크롤 위치는 프로세스 재생성 뒤 완벽히 복원되지 않을 수 있습니다.

## 개인정보·배포

앱은 ID/비밀번호를 자체 저장하거나 서버로 수집하지 않습니다. 로그인은 네이버 HTTPS 페이지에서 직접 입력하며 WebView 쿠키는 Android 앱 전용 저장소에 남습니다. 분석 SDK·광고·별도 서버가 없습니다. 시스템 WebView가 네이버 및 페이지의 외부 리소스와 통신합니다.

이 저장소는 공개 상태입니다. 로그인 쿠키, ID/비밀번호, 개인 서명키는 커밋하지 마세요. `ci/build-recipient.pem`은 빌드 결과 전송용 **공개 인증서**이며 APK 서명키가 아닙니다.

Play Store 배포 및 GitHub Release는 만들지 않습니다. CI 설치 파일은 공개 저장소에 평문으로 올리지 않으며 수신자의 공개 인증서로 암호화합니다. 복호화 개인키는 저장소에 없으므로 소스만 복제한 사람은 개인 빌드 파일을 열 수 없습니다.

## 개발·빌드

고정 도구: JDK 17, Gradle 8.11.1, AGP 8.9.2, Kotlin 2.0.21, compile/target SDK 35.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

최초 GitHub 빌드는 공식 Gradle Wrapper를 생성·체크섬 설정 후 저장소에 커밋합니다. 이후 Android Studio에서 바로 열 수 있습니다. Wrapper가 아직 없는 최초 커밋은 `gradle wrapper --gradle-version 8.11.1`로 준비할 수 있습니다.

GitHub Actions `Android APK`는 main 소스 변경 또는 수동 실행으로 빌드합니다. 설치 파일과 서명키 백업은 `YamoneCafe-encrypted-personal-build`의 p7m 안에만 들어 있습니다. 수신자는 별도 보관한 개인키로 복호화합니다.

```sh
openssl cms -decrypt -binary -inform DER \
  -in YamoneCafe-personal-build.p7m \
  -inkey build-decrypt-key.pem -out personal-build.tar.gz
tar -xzf personal-build.tar.gz
```

디버그 APK는 개인 테스트용입니다. CI마다 디버그 서명키가 달라질 수 있어 새 APK를 기존 앱 위에 설치하려면 동일한 키로 다시 서명해야 합니다. 첫 빌드의 키는 암호화된 결과 안에 백업됩니다. 해당 키와 복호화 개인키는 공개 저장소에 올리지 마세요. 지속 업데이트용 서명은 별도 개인 키/저장소 Secrets 설정이 필요합니다.

## 검증

자동 검사: URL 검증·정수 초 간격 관련 12개 JVM 테스트, Android Lint, APK 컴파일·서명 검증. 실제 결과는 GitHub Actions 실행을 확인하세요. 물리적 단말의 네이버 로그인·장시간 화면 꺼짐·제조사 절전 동작은 자동 검사와 별개입니다. 실기기 확인표는 [docs/TESTING.md](docs/TESTING.md).

## 공식 근거

- https://developer.android.com/develop/background-work/services/alarms
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/training/monitoring-device-state/doze-standby
- https://developer.android.com/reference/android/webkit/WebView
