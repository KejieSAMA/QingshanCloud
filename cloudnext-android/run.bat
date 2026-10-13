@echo off
REM Gradle wrapper - auto JDK 17.
REM Usage:
REM   run                       (default: assembleDebug)
REM   run clean
REM   run :app:assembleRelease
REM
REM JDK 17 path priority:
REM   1) env CLOUDNEXT_JDK17
REM   2) C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot
REM   3) C:\Program Files\Eclipse Adoptium\jdk-17
REM   4) C:\Program Files\Java\jdk-17

setlocal

set "JDK17="

if defined CLOUDNEXT_JDK17 if exist "%CLOUDNEXT_JDK17%\bin\java.exe" set "JDK17=%CLOUDNEXT_JDK17%"
if not defined JDK17 if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot\bin\java.exe" set "JDK17=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
if not defined JDK17 if exist "C:\Program Files\Eclipse Adoptium\jdk-17\bin\java.exe" set "JDK17=C:\Program Files\Eclipse Adoptium\jdk-17"
if not defined JDK17 if exist "C:\Program Files\Java\jdk-17\bin\java.exe" set "JDK17=C:\Program Files\Java\jdk-17"

if not defined JDK17 (
    echo ERROR: JDK 17 not found.
    echo Install JDK 17 or set CLOUDNEXT_JDK17 env var.
    exit /b 1
)

set "JAVA_HOME=%JDK17%"
echo Using JAVA_HOME=%JAVA_HOME%

if "%~1"=="" (
    call "%~dp0gradlew.bat" :app:assembleDebug
) else (
    call "%~dp0gradlew.bat" %*
)

endlocal
