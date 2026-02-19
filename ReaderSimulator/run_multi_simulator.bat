@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion
title Multi-Simulator Launcher

set COUNT=%~1
set START_PORT=%~2
if "%COUNT%"=="" set COUNT=3
if "%START_PORT%"=="" set START_PORT=20058

cd /d "%~dp0"

echo Compiling ReaderSimulator.java ...
javac ReaderSimulator.java
if errorlevel 1 (
    echo Compile failed!
    pause
    exit /b 1
)

echo.
set /a END_PORT=%START_PORT%+%COUNT%-1
echo  Launching %COUNT% simulators (TCP %START_PORT% ~ %END_PORT%)
echo.

for /L %%i in (1,1,%COUNT%) do (
    set /a PORT=%START_PORT%+%%i-1
    echo  [!PORT!] Starting ...
    start "Simulator [TCP:!PORT!]" cmd /k "cd /d "%~dp0" && java ReaderSimulator --port !PORT! --udp-port 0"
    timeout /t 1 /nobreak >nul
)

echo.
echo  Done. %COUNT% simulators running on TCP %START_PORT% ~ %END_PORT%
echo.
pause
