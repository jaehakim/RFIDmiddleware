@echo off
chcp 65001 >nul
echo ========================================
echo  RFID Middleware
echo ========================================

set BASE_DIR=%~dp0
set JAR_NAME=RFIDMiddleware.jar
set CFG_DIR=%BASE_DIR%config

REM Java path (modify this to match your JRE/JDK installation)
set JAVA_HOME=C:\JAVA\openjdk-21.0.2_windows-x64_bin
set PATH=%JAVA_HOME%\bin;%PATH%

if not exist "%BASE_DIR%%JAR_NAME%" (
    echo [ERROR] %JAR_NAME% not found. Run build.bat first.
    pause
    exit /b 1
)

REM Korean path workaround: copy to temp run dir
set RUN_TMP=%TEMP%\air3x_run
if exist "%RUN_TMP%" rmdir /s /q "%RUN_TMP%"
mkdir "%RUN_TMP%"
mkdir "%RUN_TMP%\config"

copy /y "%BASE_DIR%%JAR_NAME%" "%RUN_TMP%\" >nul
if exist "%BASE_DIR%middleware-flow.html" copy /y "%BASE_DIR%middleware-flow.html" "%RUN_TMP%\" >nul
if exist "%CFG_DIR%\readers.cfg" copy /y "%CFG_DIR%\readers.cfg" "%RUN_TMP%\config\" >nul
if exist "%CFG_DIR%\database.cfg" copy /y "%CFG_DIR%\database.cfg" "%RUN_TMP%\config\" >nul

echo Starting middleware...
pushd "%RUN_TMP%"
java -Dfile.encoding=UTF-8 -jar %JAR_NAME%
popd

REM Copy back config changes
if exist "%RUN_TMP%\config\readers.cfg" copy /y "%RUN_TMP%\config\readers.cfg" "%CFG_DIR%\" >nul
if exist "%RUN_TMP%\config\database.cfg" copy /y "%RUN_TMP%\config\database.cfg" "%CFG_DIR%\" >nul

rmdir /s /q "%RUN_TMP%"
