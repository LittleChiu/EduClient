@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "APP_HOME=%~dp0"
set "NATIVE_EXE=%APP_HOME%target\EduClient\EduClient.exe"
set "APP_JAR=%APP_HOME%target\EduClient-1.0-SNAPSHOT.jar"

if exist "%NATIVE_EXE%" (
    pushd "%APP_HOME%"
    "%NATIVE_EXE%" %*
    set "EXIT_CODE=!ERRORLEVEL!"
    popd
    exit /b !EXIT_CODE!
)

if not exist "%APP_JAR%" (
    echo [ERROR] Build output was not found.
    echo Run "mvn clean package" first, then try again.
    pause
    exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java was not found in PATH.
    echo Install Java 17 or newer, then try again.
    pause
    exit /b 1
)

pushd "%APP_HOME%"
java -jar "%APP_JAR%" %*
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
