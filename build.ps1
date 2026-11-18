$ErrorActionPreference = "Continue"

$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$buildTools = "$sdk\build-tools\35.0.0"
$platformJar = "$sdk\platforms\android-36\android.jar"
$jdkBin = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin"

$aapt2 = "$buildTools\aapt2.exe"
$d8 = "$buildTools\d8.bat"
$zipalign = "$buildTools\zipalign.exe"
$apksigner = "$buildTools\apksigner.bat"
$javac = "$jdkBin\javac.exe"
$keytool = "$jdkBin\keytool.exe"

$projectRoot = $PSScriptRoot
if (!$projectRoot) { $projectRoot = Get-Location }

$buildDir = "$projectRoot\build"
$compiledResDir = "$buildDir\compiled_res"
$genDir = "$buildDir\gen"
$classesDir = "$buildDir\classes"
$dexDir = "$buildDir\dex"

Write-Host "=== Building DroidX Launcher ===" -ForegroundColor Cyan

# 1. Clean build directory
if (Test-Path $buildDir) { Remove-Item -Recurse -Force $buildDir }
New-Item -ItemType Directory -Path $compiledResDir | Out-Null
New-Item -ItemType Directory -Path $genDir | Out-Null
New-Item -ItemType Directory -Path $classesDir | Out-Null
New-Item -ItemType Directory -Path $dexDir | Out-Null

# 2. Compile resources with aapt2
Write-Host "-> Compiling resources..." -ForegroundColor Yellow
$resDir = "$projectRoot\app\src\main\res"
& $aapt2 compile --dir $resDir -o $compiledResDir
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

# 3. Link resources with aapt2
Write-Host "-> Linking resources..." -ForegroundColor Yellow
$manifest = "$projectRoot\app\src\main\AndroidManifest.xml"
$unalignedApk = "$buildDir\unaligned.apk"
$flatFiles = Get-ChildItem -Path $compiledResDir -Filter "*.flat" | ForEach-Object { $_.FullName }

& $aapt2 link -I $platformJar `
    --manifest $manifest `
    --min-sdk-version 24 `
    --target-sdk-version 31 `
    --java $genDir `
    -o $unalignedApk `
    --auto-add-overlay `
    $flatFiles
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

# 4. Compile Java sources
Write-Host "-> Compiling Java sources..." -ForegroundColor Yellow
$javaSources = Get-ChildItem -Path "$projectRoot\app\src\main\java", "$genDir" -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }

& $javac -encoding UTF-8 `
    -cp $platformJar `
    -d $classesDir `
    -source 1.8 `
    -target 1.8 `
    $javaSources
if ($LASTEXITCODE -ne 0) { throw "javac compilation failed" }

# 5. D8 Dexing
Write-Host "-> Converting bytecode to DEX..." -ForegroundColor Yellow
$classFiles = Get-ChildItem -Path $classesDir -Filter "*.class" -Recurse | ForEach-Object { $_.FullName }

& cmd /c $d8 --min-api 24 --lib $platformJar --output $dexDir $classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

# 6. Add classes.dex into unaligned APK
Write-Host "-> Packaging DEX into APK..." -ForegroundColor Yellow
# Using jar tool or aapt2 to add classes.dex
$jar = "$jdkBin\jar.exe"
Push-Location $dexDir
& $jar uf $unalignedApk classes.dex
Pop-Location

# 7. Zipalign
Write-Host "-> Aligning APK..." -ForegroundColor Yellow
$alignedApk = "$buildDir\aligned.apk"
& $zipalign -f -p 4 $unalignedApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# 8. Create debug keystore if not exists
$keystore = "$projectRoot\debug.keystore"
if (!(Test-Path $keystore)) {
    Write-Host "-> Generating debug keystore..." -ForegroundColor Yellow
    & $keytool -genkeypair -v -keystore $keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=DroidX, OU=DroidX, O=DroidX, L=Hanoi, S=Hanoi, C=VN"
}

# 9. Sign APK
Write-Host "-> Signing APK..." -ForegroundColor Yellow
$finalApk = "$projectRoot\DroidX_Launcher.apk"
& cmd /c $apksigner sign --ks $keystore --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out $finalApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Host "=== Build SUCCESS! Generated: $finalApk ===" -ForegroundColor Green

# 10. Install via adb if device is connected
$adb = "$sdk\platform-tools\adb.exe"
if (Test-Path $adb) {
    Write-Host "-> Installing onto connected device..." -ForegroundColor Cyan
    & $adb shell "settings put global verifier_verify_adb_installs 0" 2>$null
    & $adb shell "settings put global package_verifier_enable 0" 2>$null
    & $adb install -r -d -t $finalApk
    & $adb shell "am start -n com.droidx.launcher/.MainActivity"
}
