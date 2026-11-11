@echo off
setlocal
title GetJobs - Job Application Assistant

rem ===== 1. Set JDK 21 =====
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem ===== 2. Go to project directory =====
cd /d "%~dp0"

rem ===== 3. Kill old instance holding port 9527 (avoid 'port already in use') =====
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":9527" ^| findstr "LISTENING"') do (
    echo Stopping old instance PID %%p ...
    taskkill /F /PID %%p >nul 2>&1
)

rem ===== 4. Clear injected port override (WorkBuddy env) =====
set "SERVER__PORT="

echo ================================================
echo   Starting GetJobs ...
echo   Web UI : http://localhost:6866
echo   API    : http://localhost:9527
echo   (Keep this window OPEN. Closing it stops the app.)
echo ================================================
call gradlew.bat bootRun

echo.
echo App stopped.
pause
