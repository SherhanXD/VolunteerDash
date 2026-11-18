param([string]$name)
# Captures an emulator screenshot into screenshots/<name>.png (avoids PowerShell binary redirection issues).
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$out = Join-Path $PSScriptRoot "..\screenshots"
New-Item -ItemType Directory -Force $out | Out-Null
& $adb shell screencap -p /sdcard/vd_shot.png
& $adb pull /sdcard/vd_shot.png (Join-Path $out "$name.png") | Out-Null
