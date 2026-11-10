# =====================================================================
#  현장 포토 물량 AR - 원클릭 설치 (Windows)
#  포스코이앤씨 건축BIM섹션
#  하는 일: JDK·Android SDK 준비 → 앱 빌드 → 바탕화면에 APK 복사
#           → USB로 연결된 휴대폰에 설치 → 앱 실행
#  Android Studio가 없어도 됩니다. 처음 한 번은 약 1GB를 내려받습니다.
# =====================================================================
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # 다운로드 속도 저하 방지
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

$Root  = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Tools = Join-Path $env:LOCALAPPDATA 'SiteMeasureAR-tools'
$Pkg   = 'com.poscoenc.bim.sitemeasure'
$ApkName = '현장포토물량AR.apk'

function Step($n, $t) { Write-Host ''; Write-Host ("[{0}/6] {1}" -f $n, $t) -ForegroundColor Cyan }
function Info($t) { Write-Host ('      ' + $t) }
function Fail($t) {
    Write-Host ''
    Write-Host ('설치를 멈췄습니다: ' + $t) -ForegroundColor Red
    Write-Host '이 창의 내용을 캡처해 보내 주시면 원인을 찾아 드립니다.' -ForegroundColor Yellow
    Read-Host 'Enter를 누르면 창이 닫힙니다' | Out-Null
    exit 1
}
function Download($url, $out) {
    Info ('내려받는 중: ' + $url)
    try { Invoke-WebRequest -Uri $url -OutFile $out -UseBasicParsing }
    catch { Fail ('내려받기 실패 (회사망 차단·프록시 확인 필요): ' + $_.Exception.Message) }
}

# 회사 프록시를 Windows 설정 그대로 사용
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $wp = [Net.WebRequest]::GetSystemWebProxy()
    $wp.Credentials = [Net.CredentialCache]::DefaultNetworkCredentials
    [Net.WebRequest]::DefaultWebProxy = $wp
} catch {}

New-Item -ItemType Directory -Force -Path $Tools | Out-Null
Write-Host '현장 포토 물량 AR - 원클릭 설치를 시작합니다.' -ForegroundColor Green
Info ('프로젝트 폴더: ' + $Root)

# ---------------------------------------------------------------------
Step 1 'Java(JDK 17 이상) 확인'
$JavaHome = $null
$cands = @(
    "$env:ProgramFiles\Android\Android Studio\jbr",
    "$env:ProgramFiles\Android\Android Studio\jre",
    $env:JAVA_HOME,
    "$Tools\jdk"
)
foreach ($c in $cands) {
    if ($c -and (Test-Path (Join-Path $c 'bin\java.exe'))) { $JavaHome = $c; break }
}
if (-not $JavaHome) {
    Info 'JDK 17이 없어 내려받습니다 (약 190MB).'
    $zip = Join-Path $Tools 'jdk.zip'
    Download 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' $zip
    $tmp = Join-Path $Tools 'jdk_tmp'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $tmp -Force
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    if (-not $inner) { Fail 'JDK 압축 해제 실패' }
    Move-Item $inner.FullName (Join-Path $Tools 'jdk')
    Remove-Item $tmp -Recurse -Force
    Remove-Item $zip -Force
    $JavaHome = Join-Path $Tools 'jdk'
}
$env:JAVA_HOME = $JavaHome
$env:Path = (Join-Path $JavaHome 'bin') + ';' + $env:Path
Info ('사용하는 Java: ' + $JavaHome)

# ---------------------------------------------------------------------
Step 2 'Android SDK 확인'
$Sdk = $env:ANDROID_HOME
if (-not $Sdk) { $Sdk = $env:ANDROID_SDK_ROOT }
if (-not $Sdk -or -not (Test-Path $Sdk)) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
New-Item -ItemType Directory -Force -Path $Sdk | Out-Null

$sdkm = Get-ChildItem (Join-Path $Sdk 'cmdline-tools') -Recurse -Filter 'sdkmanager.bat' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $sdkm) {
    Info 'Android SDK 명령줄 도구를 내려받습니다 (약 150MB).'
    $zip = Join-Path $Tools 'cmdline-tools.zip'
    Download 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' $zip
    $tmp = Join-Path $Tools 'clt_tmp'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $tmp -Force
    $dest = Join-Path $Sdk 'cmdline-tools\latest'
    if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
    New-Item -ItemType Directory -Force -Path (Join-Path $Sdk 'cmdline-tools') | Out-Null
    Move-Item (Join-Path $tmp 'cmdline-tools') $dest
    Remove-Item $tmp -Recurse -Force
    Remove-Item $zip -Force
    $sdkm = Get-Item (Join-Path $dest 'bin\sdkmanager.bat')
}
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk

$need = @('platform-tools', 'platforms\android-34', 'build-tools\34.0.0')
$missing = $need | Where-Object { -not (Test-Path (Join-Path $Sdk $_)) }
if ($missing) {
    Info '라이선스에 동의하고 빌드 도구를 설치합니다 (처음 한 번, 수 분 걸립니다).'
    $yes = ("y`r`n" * 30)
    $yes | & $sdkm.FullName "--sdk_root=$Sdk" --licenses | Out-Null
    & $sdkm.FullName "--sdk_root=$Sdk" 'platform-tools' 'platforms;android-34' 'build-tools;34.0.0' | Out-Host
    if ($LASTEXITCODE -ne 0) { Fail 'Android SDK 구성요소 설치 실패' }
}
Info ('Android SDK: ' + $Sdk)

# Gradle이 SDK 위치를 알도록 local.properties 작성 (C\:\\Users\\... 형식)
$escaped = $Sdk.Replace('\', '\\').Replace(':', '\:')
Set-Content -Path (Join-Path $Root 'local.properties') -Value ('sdk.dir=' + $escaped) -Encoding ASCII

# ---------------------------------------------------------------------
Step 3 '앱 빌드 (처음은 5~15분, 다음부터는 1분 안팎)'
Push-Location $Root
& (Join-Path $Root 'gradlew.bat') assembleDebug --no-daemon --console=plain
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { Fail '앱 빌드 실패 (위쪽의 FAILURE 아래 내용을 보내 주세요)' }
$apk = Join-Path $Root 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { Fail 'APK 파일을 찾지 못했습니다' }

# ---------------------------------------------------------------------
Step 4 '바탕화면에 설치 파일 복사'
$desk = [Environment]::GetFolderPath('Desktop')
$deskApk = Join-Path $desk $ApkName
Copy-Item $apk $deskApk -Force
Info ('복사함: ' + $deskApk + '  (다른 휴대폰에 보내 설치할 때 쓰세요)')

# ---------------------------------------------------------------------
Step 5 '휴대폰 연결 확인 (USB)'
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
& $adb start-server | Out-Null
$serial = $null
while (-not $serial) {
    $lines = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\S' }
    $ok = $lines | Where-Object { $_ -match '\tdevice$' } | Select-Object -First 1
    $unauth = $lines | Where-Object { $_ -match '\tunauthorized$' }
    if ($ok) { $serial = ($ok -split '\t')[0]; break }
    Write-Host ''
    if ($unauth) {
        Write-Host '  휴대폰 화면에 "USB 디버깅을 허용하시겠습니까?" 창이 떠 있습니다.' -ForegroundColor Yellow
        Write-Host '  "이 컴퓨터에서 항상 허용"을 체크하고 [허용]을 누르세요.' -ForegroundColor Yellow
    } else {
        Write-Host '  연결된 휴대폰이 없습니다. 처음 한 번만 아래를 해 주세요.' -ForegroundColor Yellow
        Write-Host '   1) 설정 > 휴대전화 정보 > 소프트웨어 정보 > 빌드 번호를 7번 누르기'
        Write-Host '   2) 설정 > 개발자 옵션 > USB 디버깅 켜기'
        Write-Host '   3) USB 케이블로 PC에 연결 (충전 전용 케이블은 안 됩니다)'
    }
    $ans = Read-Host '  준비되면 Enter, 휴대폰 설치를 건너뛰려면 S 입력 후 Enter'
    if ($ans -match '^[sS]') {
        Write-Host ''
        Write-Host ('휴대폰 설치를 건너뛰었습니다. 바탕화면의 ' + $ApkName + ' 파일을 휴대폰으로 보내 설치할 수 있습니다.') -ForegroundColor Green
        Read-Host 'Enter를 누르면 창이 닫힙니다' | Out-Null
        exit 0
    }
}
$model = (& $adb -s $serial shell getprop ro.product.model) -join ''
Info ('연결된 휴대폰: ' + $model.Trim() + ' (' + $serial + ')')

# ---------------------------------------------------------------------
Step 6 '휴대폰에 설치하고 실행'
& $adb -s $serial install -r -d $apk | Out-Host
if ($LASTEXITCODE -ne 0) {
    Info '기존 앱과 서명이 달라 다시 설치합니다 (이전 측정 기록은 지워집니다).'
    & $adb -s $serial uninstall $Pkg | Out-Null
    & $adb -s $serial install $apk | Out-Host
    if ($LASTEXITCODE -ne 0) { Fail '휴대폰 설치 실패 (휴대폰 화면에 설치 확인 창이 떴는지 보세요)' }
}
& $adb -s $serial shell monkey -p $Pkg -c android.intent.category.LAUNCHER 1 | Out-Null

Write-Host ''
Write-Host '완료했습니다. 휴대폰에서 "현장 포토 물량 AR"이 열렸습니다.' -ForegroundColor Green
Write-Host '처음 실행 때 카메라 권한과 Google Play AR 서비스 설치를 허용하세요.'
Read-Host 'Enter를 누르면 창이 닫힙니다' | Out-Null
