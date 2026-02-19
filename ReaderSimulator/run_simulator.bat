@echo off
chcp 65001 >nul 2>&1
title Reader Simulator

cd /d "%~dp0"

echo Compiling ReaderSimulator.java ...
javac ReaderSimulator.java
if errorlevel 1 (
    echo Compile failed!
    pause
    exit /b 1
)

echo Starting simulator...
echo.
java ReaderSimulator %*
pause
