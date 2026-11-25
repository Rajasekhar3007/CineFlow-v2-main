@echo off
title CineFlow - Movie Ticket Reservation System
cd /d "%~dp0"
where mvn >nul 2>&1
if errorlevel 1 (
    echo Maven is not on PATH. Follow TEAM_SETUP.txt, then reopen the terminal.
    pause
    exit /b 1
)
if not exist "db.properties" (
    echo [CineFlow] Initializing 'db.properties' from 'db.properties.example'...
    copy "db.properties.example" "db.properties" >nul
    echo Please set your MySQL password in 'db.properties', then save and close Notepad.
    start /wait notepad.exe db.properties
)
echo ========================================================
echo   Launching CineFlow - Multiplex Ticket System...
echo ========================================================
call mvn compile javafx:run
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Application exited with an error.
    pause
)
