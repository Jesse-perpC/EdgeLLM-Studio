@echo off
title EdgeLLM Studio - Windows Desktop Installer
cls
echo ======================================================
echo    EdgeLLM Studio - Windows Desktop Installer
echo ======================================================
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-EdgeLLM-Windows.ps1"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [!] Installation encountered an issue. Press any key to exit.
    pause >nul
    exit /b %ERRORLEVEL%
)

echo.
echo [✓] Done! You can close this window.
pause >nul
