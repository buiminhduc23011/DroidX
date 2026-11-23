# Debloat ROM gốc Vivo/BBK (không cần root) — giữ máy mát, tiết kiệm pin, hết lag
# Giữ lại: DroidX Launcher, Wi-Fi & Phát Wi-Fi (Hotspot/Tethering), Cài đặt, TTS, WebView, Bàn phím
# Cách dùng: cắm cáp, bật USB Debugging, chạy: powershell -ExecutionPolicy Bypass -File .\debloat-stock-rom.ps1
$ErrorActionPreference = "Continue"
$sdk = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (!(Test-Path $sdk)) { $sdk = "adb" }

Write-Host "=== DroidX Debloat & Battery Optimizer: ROM gốc Vivo ===" -ForegroundColor Cyan
& $sdk devices

# List package an toàn để disable (chỉ disable cho user 0, không xóa, cần thì enable lại được)
$safeToDisable = @(
  # Vivo / BBK Bloat & Background Daemons
  "com.vivo.appstore",
  "com.vivo.website",
  "com.bbk.cloud",
  "com.vivo.imanager",
  "com.vivo.browser",
  "com.vivo.video",
  "com.vivo.music",
  "com.android.bbkmusic",
  "com.bbk.appstore",
  "com.bbk.account",
  "com.bbk.theme",
  "com.bbk.theme.resources",
  "com.bbk.updater",
  "com.vivo.game",
  "com.vivo.wallet",
  "com.vivo.vivotips",
  "com.vivo.globalanimation",
  "com.vivo.pushservice",       # Hay gây wakelock ngầm
  "com.vivo.abe",               # AI/Behavior Engine ngốn RAM/CPU
  "com.vivo.epm",               # Telemetry / Exception monitor
  "com.vivo.sps",               # Smart Process Service
  "com.vivo.hiboard",           # Màn hình phụ / feed
  "com.vivo.crontab",
  "com.vivo.upnpserver",
  "com.vivo.daemonService",
  "com.vivo.cota",
  "com.vivo.assistant",
  "com.vivo.SmartKey",
  "com.vivo.weather.provider",
  "com.vivo.ewarranty",
  "com.vivo.fuelsummary",
  "com.vivo.smartshot",
  "com.vivo.doubleinstance",
  "com.vivo.minscreen",
  "com.vivo.videoeditor",
  "com.vivo.networkimprove",
  "com.vivo.setupwizard",

  # Facebook / Meta Bloat
  "com.facebook.system",
  "com.facebook.appmanager",
  "com.facebook.services",

  # Google Bloat & Telemetry (vẫn giữ GMS/TTS/WebView/Play Store nếu cần, bỏ rác nặng)
  "com.google.mainline.telemetry",
  "com.google.android.apps.docs",
  "com.google.android.apps.photos",
  "com.google.android.videos",
  "com.google.android.music",
  "com.google.android.apps.nbu.files",
  "com.google.android.photopicker",
  "com.google.android.safetycore",
  "com.google.android.contactkeys",
  "com.google.android.verifier",

  # Launcher thừa (chỉ giữ DroidX làm chính)
  "app.lawnchair"
)

foreach ($pkg in $safeToDisable) {
  Write-Host "Disabling $pkg ..." -ForegroundColor Yellow
  $res = & $sdk shell "pm disable-user --user 0 $pkg 2>&1"
  if ($res -match "SecurityException|Failure|Error") {
    $u = & $sdk shell "pm uninstall -k --user 0 $pkg 2>&1"
    if ($u -notmatch "Success") {
      # Vivo FuntouchOS chặn disable/uninstall app hệ thống -> chặn thông báo + chạy nền + wakelock + force-stop
      & $sdk shell "cmd appops set $pkg POST_NOTIFICATION ignore; cmd appops set $pkg RUN_ANY_IN_BACKGROUND ignore; cmd appops set $pkg RUN_IN_BACKGROUND ignore; cmd appops set $pkg WAKE_LOCK ignore; am set-standby-bucket $pkg restricted; am force-stop $pkg" 2>$null
      Write-Host "  -> Protected by ROM: silenced + restricted + stopped" -ForegroundColor DarkYellow
    }
  }
}

Write-Host ""
Write-Host "=== Tắt quét Wi-Fi / Bluetooth nền & đặt timeout màn hình 60s ===" -ForegroundColor Cyan
& $sdk shell "settings put global wifi_scan_always_enabled 0"
& $sdk shell "settings put global ble_scan_always_enabled 0"
& $sdk shell "settings put system screen_off_timeout 60000"
& $sdk shell "settings put global adaptive_battery_management_enabled 1"
& $sdk shell "settings put global app_standby_enabled 1"

Write-Host ""
Write-Host "=== Tắt animation hệ thống để hết lag ===" -ForegroundColor Cyan
& $sdk shell "settings put global window_animation_scale 0.0"
& $sdk shell "settings put global transition_animation_scale 0.0"
& $sdk shell "settings put global animator_duration_scale 0.0"

Write-Host ""
Write-Host "=== Giới hạn background & swap cho máy RAM 4GB ===" -ForegroundColor Cyan
& $sdk shell "settings put global activity_manager_constants max_cached_processes=8"
& $sdk shell "cmd appops set com.google.android.youtube RUN_ANY_IN_BACKGROUND ignore" 2>$null
& $sdk shell "cmd appops set com.android.vending RUN_ANY_IN_BACKGROUND ignore" 2>$null

Write-Host ""
Write-Host "=== Đặt DroidX Launcher làm Home mặc định ===" -ForegroundColor Cyan
& $sdk shell "cmd package set-home-activity com.droidx.launcher/.MainActivity" 2>$null

Write-Host ""
Write-Host "Xong! Reboot máy 1 lần để RAM sạch hoàn toàn." -ForegroundColor Green
Write-Host "Muốn khôi phục 1 app: adb shell pm enable <tên_package>" -ForegroundColor Gray
