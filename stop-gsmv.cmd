@echo off
setlocal EnableExtensions

set "GSMV_ROOT=%~dp0"
if "%GSMV_ROOT:~-1%"=="\" set "GSMV_ROOT=%GSMV_ROOT:~0,-1%"

powershell -NoProfile -ExecutionPolicy Bypass -File "%GSMV_ROOT%\scripts\stop-gsmv.ps1" -Root "%GSMV_ROOT%"
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
  echo.
  echo GSMV stop script reported an error. Please check the message above.
  pause >nul
  exit /b %EXIT_CODE%
)

echo.
echo Press any key to close this window.
pause >nul
