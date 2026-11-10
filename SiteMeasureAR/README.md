# 현장 포토 물량 AR — Android 시제품 ver 0.1

포스코이앤씨 건축BIM섹션. 웹 시제품(현장 포토 물량 ver 0.9)의 화면·계산 흐름을 현장 운영용 설치형 앱으로 옮기는 첫 단계입니다.

## 1차 개발 범위

| 모드 | 이번 버전에서 되는 것 | 기술 |
|---|---|---|
| AR 실측 | 기준 치수 없이 2점 거리 측정, 측정 사진 저장 | ARCore + Depth API |
| 강재 개수 검토 | 사진을 열어 눌러서 번호 붙이기·빼기, 되돌리기, 근거 사진 저장 | 수동 검토 (자동 검출 자리 마련) |
| 산출 내역 | 저장한 측정을 최신순으로 보기, 근거 사진 열기, CSV 내보내기 | 기기 내부 저장 |

### AR 실측 동작
1. 화면 가운데 조준점이 가리키는 표면을 **깊이 점 → 평면 → 방향 있는 특징점** 순서로 고릅니다. 조준점 아래에 어떤 근거로 잡았는지(깊이·평면·특징점)가 표시됩니다.
2. ‘점 찍기’를 두 번 누르면 두 앵커 사이 3차원 거리를 mm 단위까지 표시합니다. 첫 점을 찍은 뒤에는 조준점까지의 거리가 실시간으로 보입니다.
3. 대상까지 거리가 0.5~5 m를 벗어나면 조준점이 주황색으로 바뀌고 경고가 뜹니다. Google은 깊이 정확도가 대체로 이 범위에서 가장 좋다고 안내합니다.
4. ‘저장’을 누르면 카메라 화면과 측정 표시를 합친 사진, 두 점의 좌표, 카메라 거리, 깊이 사용 여부를 산출 내역에 남깁니다.

### 강재 개수 자동 검출에 대해
기본 YOLO 모델은 겹쳐 쌓인 H형강·각관·철근 단면을 구분하도록 학습되지 않아 이 목적에 맞지 않습니다. 이번 버전은 수동 검토 화면과 `SteelDetector` 연결 지점만 넣었습니다. 현장 사진으로 학습·검증한 TFLite 모델을 `Detectors.current`에 연결하면 ‘자동 검출’ 버튼이 켜지고, 검출 결과 위에서 누락·오탐을 눌러 고치는 흐름이 그대로 이어집니다.

## 원클릭 설치 (Windows PC → 휴대폰)
1. 압축을 푼 폴더의 **원클릭설치.bat**을 더블클릭합니다. ‘Windows의 PC 보호’ 창이 뜨면 [추가 정보] → [실행]을 누릅니다.
2. 스크립트가 JDK 17·Android SDK를 자동으로 받아 앱을 빌드합니다. Android Studio는 없어도 되고, 처음 한 번은 약 1GB를 내려받습니다.
3. 바탕화면에 **현장포토물량AR.apk** 설치 파일이 생깁니다.
4. USB로 연결된 휴대폰이 있으면 바로 설치하고 앱을 엽니다. 휴대폰은 처음 한 번 ‘USB 디버깅’을 켜야 합니다(창에 안내가 나옵니다).

## APK 파일로 설치 (휴대폰만으로)
1. 현장포토물량AR.apk를 메일·메신저·USB로 휴대폰에 옮깁니다.
2. ‘내 파일’에서 APK를 누릅니다.
3. ‘출처를 알 수 없는 앱’ 허용을 물으면, 파일을 연 앱에 대해 허용합니다.
4. Play 프로텍트 경고가 뜨면 [세부정보] → [무시하고 설치]를 누릅니다.
5. 업무폰에 보안 관리(MDM)가 걸려 있으면 설치가 막힐 수 있으니 IT 담당 부서와 배포 방법을 정하세요.

## Android Studio로 빌드하기
1. Android Studio (Koala 2024.1 이상)에서 이 폴더를 엽니다.
2. Gradle 동기화가 끝나면 ARCore 지원 Android 기기를 USB로 연결하고 실행합니다. 에뮬레이터에서는 AR 실측이 동작하지 않습니다.
3. 첫 실행 때 카메라 권한과 Google Play AR 서비스 설치를 요청합니다.

- 최소 Android 7.0 (API 24), 대상 API 34
- ARCore 1.44.0, Kotlin 1.9.24, Android Gradle Plugin 8.5.2, Gradle 8.7
- 지원 기기 목록: https://developers.google.com/ar/devices
- GitHub Actions에서 자동 빌드됩니다. 최신 APK: 저장소 Releases의 `site-measure-ar-build-*` 항목

## 현장 검증 방법 (권장)
1. 기기별로 길이를 아는 구간 5곳(0.5 m, 1 m, 2 m, 3 m, 5 m)을 줄자로 표시합니다.
2. 각 구간을 AR 실측으로 3회씩 재서 저장합니다.
3. 산출 내역 CSV를 내보내 줄자 값과 비교하고, 기기·거리·표면(깊이/평면/특징점)별 오차를 기록합니다.
4. 허용 오차를 넘는 조건(먼 거리, 무늬 없는 면, 어두운 곳)은 현장 사용 지침에 넣습니다.

## 저장 위치
- CSV: `Android/data/com.poscoenc.bim.sitemeasure/files/산출내역.csv`
- 근거 사진: `Android/data/com.poscoenc.bim.sitemeasure/files/Pictures/records/`
- 인터넷 연결 없이 기기 안에만 저장됩니다. 앱을 지우면 함께 지워지므로 CSV 내보내기로 옮겨 두세요.

## 파일 구성
```
app/src/main/java/com/poscoenc/bim/sitemeasure/
  MainActivity.kt        첫 화면, 세 모드 진입, ARCore 지원 여부 표시
  MeasureActivity.kt     AR 실측 (세션·깊이 설정, 표면 고르기, 2점 거리, 저장)
  BackgroundRenderer.kt  카메라 영상 그리기 (OpenGL ES 2.0)
  OverlayView.kt         조준점·측정점·측정선·거리 라벨
  CountReviewActivity.kt 강재 개수 검토
  CountView.kt           사진 위 번호 표시, 누르기 추가·빼기, 되돌리기
  SteelDetector.kt       현장 학습 모델 연결 지점
  Records.kt             산출 내역 저장·읽기·CSV 공유
  RecordsActivity.kt     산출 내역 화면
```

## 다음 단계 제안
1. 기기 2~3종으로 위 검증을 돌려 거리 오차표 작성
2. 강재 사진 수집·라벨링 (겹쳐 쌓인 사진 포함), YOLO 계열 모델 학습 후 TFLite 변환
3. AR 면적 측정 (경계 꼭지점 지정 → 면적), OpenCV 모서리·직선 스냅
4. iPhone·iPad 지원이 꼭 필요해지면 Unity AR Foundation(ARCore/ARKit) 전환 검토
