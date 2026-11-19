@echo off
rem ============================================================================
rem SeatSync Swing Client Launch Script
rem Project: SeatSync - Real-Time Ticket Booking System
rem Main Class: com.seatsync.client.SeatSyncClient
rem Default: localhost:8080
rem ============================================================================

setlocal enabledelayedexpansion

echo ===================================================
echo [SeatSync] Starting Ticket Booking GUI Client...
echo ===================================================

set SERVER_HOST=localhost
set SERVER_PORT=8080

if not "%~1"=="" set SERVER_HOST=%~1
if not "%~2"=="" set SERVER_PORT=%~2

rem 1. Check if compiled target exists
if exist "%~dp0..\target\classes" (
    echo [SeatSync] Found compiled classes in target\classes.
    java -cp "%~dp0..\target\classes;%~dp0..\target\dependency\*" com.seatsync.client.SeatSyncClient %SERVER_HOST% %SERVER_PORT%
    goto end
)

rem 2. Fallback to Maven exec plugin if target is not yet packaged
where mvn >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo [SeatSync] Launching via Maven exec plugin...
    cd /d "%~dp0.."
    mvn exec:java -Dexec.mainClass="com.seatsync.client.SeatSyncClient" -Dexec.args="%SERVER_HOST% %SERVER_PORT%"
    goto end
)

echo [ERROR] Neither compiled target\classes nor 'mvn' command was found.
echo [ERROR] Please build the project first using: mvn clean package
exit /b 1

:end
endlocal
