# Debloat ROM gốc Vivo/BBK (không cần root) — giữ máy mát + hết lag cho DroidX Launcher
# Cách dùng: cắm cáp, bật USB Debugging, chạy: powershell -ExecutionPolicy Bypass -File .\debloat-stock-rom.ps1
$ErrorActionPreference = "Continue"
$sdk = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (!(Test-Path $sdk)) { $sdk = "adb" }

Write-Host "=== DroidX Debloat: ROM gốc Vivo ===" -ForegroundColor Cyan
& $sdk devices

# List package an toàn để disable (chỉ disable cho user 0, không xóa, cần thì enable lại được)
$safeToDisable = @(
  "com.vivo.appstore",        # Vivo App Store - ngốn RAM/background
  "com.vivo.website",         # Alias hiện icon "vivo Store" trong drawer
  "com.bbk.cloud",            # Vivo Cloud
  "com.vivo.imanager",        # iManager (giữ lại nếu bạn dùng dọn rác, nhưng thường gây lag)
  "com.vivo.browser",         # Vivo Browser
  "com.vivo.video",           # Vivo Video
  "com.vivo.music",           # Vivo Music (nếu không dùng)
  "com.bbk.appstore",
  "com.vivo.game",
  "com.vivo.wallet",
  "com.vivo.vivotips",
  "com.vivo.globalanimation",
  "com.facebook.system",
  "com.facebook.appmanager",
  "com.facebook.services",
  "com.google.android.apps.docs",      # Drive (tùy chọn)
  "com.google.android.apps.photos",    # Photos (tùy chọn)
  "com.google.android.videos",         # Play Movies
  "com.google.android.music"           # Play Music cũ
)

foreach ($pkg in $safeToDisable) {
  Write-Host "Disabling $pkg ..." -ForegroundColor Yellow
  & $sdk shell "pm disable-user --user 0 $pkg" 2>$null
}

Write-Host ""
Write-Host "=== Tắt animation hệ thống để hết lag cảm giác ===" -ForegroundColor Cyan
& $sdk shell "settings put global window_animation_scale 0.0"
& $sdk shell "settings put global transition_animation_scale 0.0"
& $sdk shell "settings put global animator_duration_scale 0.0"

Write-Host ""
Write-Host "=== Giới hạn background để máy cũ đỡ lag ===" -ForegroundColor Cyan
# Không giết DroidX launcher, chỉ hạn chế chung
& $sdk shell "settings put global activity_manager_constants max_cached_processes=12"

Write-Host ""
Write-Host "Xong! Reboot máy 1 lần để RAM sạch hoàn toàn." -ForegroundColor Green
Write-Host "Muốn khôi phục 1 app: adb shell pm enable <tên_package>" -ForegroundColor Gray
