# EdgeLLM Studio on Windows Desktop

EdgeLLM Studio can be run natively on Windows 10 and Windows 11 with full GPU and NPU acceleration through several options:

## Option 1: Windows Subsystem for Android (WSA) on Windows 11 (Recommended)
1. Ensure **Windows Subsystem for Android** is installed on your Windows 11 PC.
2. Open **Windows Subsystem for Android Settings**.
3. Under **Advanced settings**, toggle **Developer mode** to **On**.
4. Double-click `Install-WSA-EdgeLLM.bat` (or run `./Install-EdgeLLM-Windows.ps1` in PowerShell).
5. EdgeLLM Studio will install and open automatically in a resizable desktop window!

## Option 2: Android Studio Emulator (Windows 10 / 11)
1. Start an Android Virtual Device (AVD) with Android 14+ (API 34/35/36).
2. Run `Install-WSA-EdgeLLM.bat` or drag and drop the APK onto the emulator window.

## Option 3: BlueStacks / LDPlayer / MuMu Player
1. Start your chosen Android emulator on Windows.
2. Drag the `app-debug.apk` directly into the emulator window to install.
