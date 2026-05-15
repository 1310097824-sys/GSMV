@echo off
setlocal EnableExtensions

set "GSMV_ROOT=%~dp0"
if "%GSMV_ROOT:~-1%"=="\" set "GSMV_ROOT=%GSMV_ROOT:~0,-1%"
set "BAILIAN_API_KEY="
set "DASHSCOPE_API_KEY="
set "DEEPSEEK_API_KEY="
set "GSMV_KEYS_B64="

if /I not "%GSMV_SKIP_API_PROMPT%"=="1" (
  for /f "usebackq delims=" %%K in (`powershell.exe -STA -NoProfile -ExecutionPolicy Bypass -File "%GSMV_ROOT%\scripts\gsmv-api-key-prompt.ps1"`) do set "GSMV_KEYS_B64=%%K"

  if defined GSMV_KEYS_B64 (
    for /f "usebackq tokens=1,* delims==" %%A in (`powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$json=[System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($env:GSMV_KEYS_B64)); $keys=$json | ConvertFrom-Json; 'BAILIAN_API_KEY=' + [string]$keys.bailian; 'DASHSCOPE_API_KEY=' + [string]$keys.bailian; 'DEEPSEEK_API_KEY=' + [string]$keys.deepseek"`) do set "%%A=%%B"
  ) else (
    echo API key dialog did not return values. Falling back to console input.
    set /p "BAILIAN_API_KEY=Enter Bailian API Key (leave blank allowed): "
    set /p "DEEPSEEK_API_KEY=Enter DeepSeek API Key (leave blank allowed): "
    set "DASHSCOPE_API_KEY=%BAILIAN_API_KEY%"
  )
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%GSMV_ROOT%\scripts\start-gsmv.ps1" -Root "%GSMV_ROOT%" -BailianApiKey "%BAILIAN_API_KEY%" -DeepSeekApiKey "%DEEPSEEK_API_KEY%"
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
  echo.
  echo GSMV start failed. Please check the error message above.
  pause >nul
  exit /b %EXIT_CODE%
)

echo.
echo Press any key to close this launcher window. The system keeps running in the background.
pause >nul
