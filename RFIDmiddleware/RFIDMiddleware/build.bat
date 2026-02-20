@echo off
chcp 65001 >nul
echo ========================================
echo  RFID Middleware Build (Fat JAR)
echo ========================================

set BASE_DIR=%~dp0
set SRC_DIR=%BASE_DIR%src
set LIB_DIR=%BASE_DIR%libs
set OUT_DIR=%BASE_DIR%out
set JAR_NAME=RFIDMiddleware.jar

REM Korean path workaround: copy to temp build dir
set BUILD_TMP=%TEMP%\air3x_build_%RANDOM%
echo [1/5] Temp build dir: %BUILD_TMP%
if exist "%BUILD_TMP%" rmdir /s /q "%BUILD_TMP%"
mkdir "%BUILD_TMP%"
mkdir "%BUILD_TMP%\libs"
mkdir "%BUILD_TMP%\src"

echo [2/5] Copying files...
xcopy /s /q /y "%LIB_DIR%\*" "%BUILD_TMP%\libs\" >nul
xcopy /s /q /y "%SRC_DIR%\*" "%BUILD_TMP%\src\" >nul

echo [3/5] Compiling...
if not exist "%BUILD_TMP%\out" mkdir "%BUILD_TMP%\out"
javac -encoding UTF-8 -cp "%BUILD_TMP%\libs\FixedReaderLib.jar;%BUILD_TMP%\libs\ReaderFinderLib.jar;%BUILD_TMP%\libs\mariadb-java-client-3.5.1.jar;%BUILD_TMP%\libs\caffeine-2.9.3.jar" -d "%BUILD_TMP%\out" ^
  "%BUILD_TMP%\src\com\apulse\middleware\util\HexUtils.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\util\AppLogger.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\config\LogConfig.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\config\ReaderConfig.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\config\DatabaseConfig.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\reader\ReaderStatus.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\reader\TagData.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\reader\ReaderConnection.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\reader\ReaderManager.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\db\DatabaseManager.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\db\TagRepository.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\db\AssetRepository.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\reader\WarningLightController.java" ^
  "%BUILD_TMP%\src\com\apulse\middleware\api\ApiServer.java" ^
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

echo [4/5] Extracting libraries...
pushd "%BUILD_TMP%\out"
jar xf "%BUILD_TMP%\libs\FixedReaderLib.jar" >nul 2>&1
jar xf "%BUILD_TMP%\libs\ReaderFinderLib.jar" >nul 2>&1
jar xf "%BUILD_TMP%\libs\mariadb-java-client-3.5.1.jar" >nul 2>&1
jar xf "%BUILD_TMP%\libs\caffeine-2.9.3.jar" >nul 2>&1
REM Remove META-INF from libraries (avoid signature conflicts)
if exist META-INF rmdir /s /q META-INF
popd

echo [5/5] Creating fat JAR...
REM Create manifest
echo Main-Class: com.apulse.middleware.Main> "%BUILD_TMP%\MANIFEST.MF"

jar cfm "%BUILD_TMP%\%JAR_NAME%" "%BUILD_TMP%\MANIFEST.MF" -C "%BUILD_TMP%\out" .

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] JAR creation failed!
    rmdir /s /q "%BUILD_TMP%"
    pause
    exit /b 1
)

REM Copy JAR and class files to output
if exist "%OUT_DIR%" rmdir /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%"
copy /y "%BUILD_TMP%\%JAR_NAME%" "%BASE_DIR%%JAR_NAME%" >nul

REM Also keep class output for backward compatibility
xcopy /s /q /y "%BUILD_TMP%\out\com\*" "%OUT_DIR%\com\" >nul

rmdir /s /q "%BUILD_TMP%"

echo.
echo [DONE] Build successful!
echo   JAR: %BASE_DIR%%JAR_NAME%
echo.
pause
