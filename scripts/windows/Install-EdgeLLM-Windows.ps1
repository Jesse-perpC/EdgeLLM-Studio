# ==============================================================================
# EdgeLLM Studio - Windows Desktop & WSA 1-Click Installer
# Compatible with Windows 11 (Windows Subsystem for Android - WSA), 
# Android Studio Emulator, BlueStacks, LDPlayer, and ADB over USB/Wi-Fi
# ==============================================================================

[CmdletBinding()]
param (
    [string]$ApkPath = "",
    [switch]$LaunchAfterInstall = $true
)

$ErrorActionPreference = "Stop"

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "   EdgeLLM Studio - Windows Desktop Installer         " -ForegroundColor Green
Write-Host "======================================================" -ForegroundColor Cyan

# 1. Resolve APK Path
if (-not $ApkPath) {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    $candidateApks = Get-ChildItem -Path $scriptDir -Filter "*.apk" -Recurse
    if (-not $candidateApks) {
        $parentDir = Split-Path -Parent $scriptDir
        $candidateApks = Get-ChildItem -Path $parentDir -Filter "*.apk" -Recurse
    }
    if ($candidateApks) {
        $ApkPath = $candidateApks[0].FullName
    } else {
        Write-Error "Could not find EdgeLLM Studio APK in script directory. Please specify -ApkPath <path_to_apk>"
        exit 1
    }
}

Write-Host "[+] Target APK: $ApkPath" -ForegroundColor Yellow

# 2. Check for ADB
$adbCmd = Get-Command "adb" -ErrorAction SilentlyContinue
if (-not $adbCmd) {
    # Check default Android SDK locations
    $defaultAdbPaths = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ProgramFiles\Android\platform-tools\adb.exe",
        "$env:USERPROFILE\platform-tools\adb.exe"
    )
    foreach ($path in $defaultAdbPaths) {
        if (Test-Path $path) {
            $adbCmd = $path
            break
        }
    }
}

if (-not $adbCmd) {
    Write-Host "[!] ADB was not found in PATH." -ForegroundColor Yellow
    Write-Host "[*] If you are using WSA (Windows Subsystem for Android), ensure Developer Mode is enabled in WSA Settings." -ForegroundColor White
    Write-Host "[*] Attempting to connect via WSA default IP: 127.0.0.1:58526" -ForegroundColor Gray
    Write-Error "Please install Android Platform-Tools (adb) or run inside Android Studio SDK command prompt."
    exit 1
} else {
    $adb = if ($adbCmd.Source) { $adbCmd.Source } else { $adbCmd }
    Write-Host "[+] Found ADB: $adb" -ForegroundColor Green
}

# 3. Connect to WSA if running
Write-Host "[*] Checking for connected devices or WSA..." -ForegroundColor White
& $adb connect 127.0.0.1:58526 2>$null | Out-Null

$devicesOutput = & $adb devices
Write-Host $devicesOutput

$deviceLines = ($devicesOutput -split "`n") | Where-Object { $_ -match "\bdevice$" }

if ($deviceLines.Count -eq 0) {
    Write-Host "[!] No active Android device or WSA instance detected." -ForegroundColor Yellow
    Write-Host "    Tips for Windows 11 WSA users:" -ForegroundColor Gray
    Write-Host "    1. Open 'Windows Subsystem for Android Settings'" -ForegroundColor Gray
    Write-Host "    2. Turn ON 'Developer Mode'" -ForegroundColor Gray
    Write-Host "    3. Click 'Manage Developer Settings' to boot WSA" -ForegroundColor Gray
    Write-Host "    4. Re-run this installer" -ForegroundColor Gray
    exit 1
}

Write-Host "[+] Found $($deviceLines.Count) connected device(s)/emulator(s)." -ForegroundColor Green

# 4. Install APK
Write-Host "[*] Installing EdgeLLM Studio..." -ForegroundColor Cyan
$installResult = & $adb install -r -d $ApkPath

if ($LASTEXITCODE -eq 0) {
    Write-Host "[✓] EdgeLLM Studio successfully installed on Windows!" -ForegroundColor Green
    
    if ($LaunchAfterInstall) {
        Write-Host "[*] Launching EdgeLLM Studio..." -ForegroundColor Cyan
        & $adb shell monkey -p com.aistudio.edgellmstudio.v7k9z 1 2>$null | Out-Null
        Write-Host "[✓] App launched." -ForegroundColor Green
    }
} else {
    Write-Error "Failed to install APK. ADB output: $installResult"
    exit 1
}
