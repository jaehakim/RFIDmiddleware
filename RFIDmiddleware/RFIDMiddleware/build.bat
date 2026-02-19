@echo off
chcp 65001 >nul
echo ========================================
echo  RFID Middleware Build
echo ========================================

set BASE_DIR=%~dp0
set SRC_DIR=%BASE_DIR%src
set LIB_DIR=%BASE_DIR%libs
set OUT_DIR=%BASE_DIR%out

REM Korean path workaround: copy to temp build dir
set BUILD_TMP=%TEMP%\air3x_build_%RANDOM%
echo [1/4] Temp build dir: %BUILD_TMP%
if exist "%BUILD_TMP%" rmdir /s /q "%BUILD_TMP%"
mkdir "%BUILD_TMP%"
mkdir "%BUILD_TMP%\libs"
mkdir "%BUILD_TMP%\src"

echo [2/4] Copying files...
xcopy /s /q /y "%LIB_DIR%\*" "%BUILD_TMP%\libs\" >nul
xcopy /s /q /y "%SRC_DIR%\*" "%BUILD_TMP%\src\" >nul

echo [3/4] Compiling...
if not exist "%BUILD_TMP%\out" mkdir "%BUILD_TMP%\out"
javac -encoding UTF-8 -cp "%BUILD_TMP%\libs\FixedReaderLib.jar;%BUILD_TMP%\libs\ReaderFinderLib.jar" -d "%BUILD_TMP%\out" ^
  "%BUILD_TMP%\src\com\apulse\middleware\util\HexUtils.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\config\ReaderConfig.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\reader\ReaderStatus.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\reader\TagData.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\reader\ReaderConnection.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\reader\ReaderManager.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\gui\ReaderIconComponent.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\gui\ReaderStatusPanel.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\gui\TagDataPanel.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\gui\LogPanel.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\gui\ConfigDialog.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\gui\MainFrame.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\Main.java"

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Compile failed!
    rmdir /s /q "%BUILD_TMP%"
    pause
    exit /b 1
)

echo [4/4] Copying output...
if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
xcopy /s /q /y "%BUILD_TMP%\out\*" "%OUT_DIR%\" >nul

rmdir /s /q "%BUILD_TMP%"

echo.
echo [DONE] Build successful! (output: %OUT_DIR%)
echo.
pause
