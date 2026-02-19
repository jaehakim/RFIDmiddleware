@echo off
chcp 65001 >nul
echo ========================================
echo  RFID Middleware
echo ========================================

set BASE_DIR=%~dp0
set LIB_DIR=%BASE_DIR%libs
set OUT_DIR=%BASE_DIR%out
set CFG_DIR=%BASE_DIR%config

if not exist "%OUT_DIR%\com\apulse\middleware\Main.class" (
    echo [ERROR] Build required. Run build.bat first.
    pause
    exit /b 1
)

REM Korean path workaround: copy to temp run dir
set RUN_TMP=%TEMP%\air3x_run
if exist "%RUN_TMP%" rmdir /s /q "%RUN_TMP%"
mkdir "%RUN_TMP%"
mkdir "%RUN_TMP%\libs"
mkdir "%RUN_TMP%\out"
mkdir "%RUN_TMP%\config"

xcopy /s /q /y "%LIB_DIR%\*" "%RUN_TMP%\libs\" >nul
xcopy /s /q /y "%OUT_DIR%\*" "%RUN_TMP%\out\" >nul
if exist "%CFG_DIR%\readers.cfg" copy /y "%CFG_DIR%\readers.cfg" "%RUN_TMP%\config\" >nul

echo Starting middleware...
pushd "%RUN_TMP%"
java -cp "out;libs\FixedReaderLib.jar;libs\ReaderFinderLib.jar" com.apulse.middleware.Main
popd

REM Copy back config changes
if exist "%RUN_TMP%\config\readers.cfg" copy /y "%RUN_TMP%\config\readers.cfg" "%CFG_DIR%\" >nul

rmdir /s /q "%RUN_TMP%"
