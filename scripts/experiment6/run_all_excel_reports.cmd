@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "ROOT=%%~fI"

if defined PYTHON (
  set "PYTHON_EXE=%PYTHON%"
) else if exist "C:\Users\13100\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" (
  set "PYTHON_EXE=C:\Users\13100\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
) else (
  set "PYTHON_EXE=python"
)
set "PYTHON=%PYTHON_EXE%"

if not defined GSMV_BASE_URL set "GSMV_BASE_URL=http://localhost:8080"
if not defined GSMV_FRONTEND_URL set "GSMV_FRONTEND_URL=http://localhost:5173"
if not defined GSMV_TEST_IMAGE set "GSMV_TEST_IMAGE=C:\Users\13100\Pictures\OIP.jpg"

call "%SCRIPT_DIR%run_whitebox_excel.cmd"
if errorlevel 1 exit /b %ERRORLEVEL%

if not defined BAILIAN_API_KEY (
  echo Missing BAILIAN_API_KEY.
  echo Please run: set BAILIAN_API_KEY=your_key
  exit /b 1
)
if not defined DEEPSEEK_API_KEY (
  echo Missing DEEPSEEK_API_KEY.
  echo Please run: set DEEPSEEK_API_KEY=your_key
  exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\start-gsmv.ps1" -Root "%ROOT%" -BailianApiKey "%BAILIAN_API_KEY%" -DeepSeekApiKey "%DEEPSEEK_API_KEY%"
if errorlevel 1 exit /b %ERRORLEVEL%

call "%SCRIPT_DIR%run_functional_excel.cmd"
set "FUNC_EXIT=%ERRORLEVEL%"
if "%FUNC_EXIT%"=="0" call "%SCRIPT_DIR%run_performance_excel.cmd"
set "PERF_EXIT=%ERRORLEVEL%"
if "%FUNC_EXIT%"=="0" if "%PERF_EXIT%"=="0" call "%SCRIPT_DIR%run_integration_excel.cmd"
set "INT_EXIT=%ERRORLEVEL%"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\scripts\stop-gsmv.ps1" -Root "%ROOT%"

if not "%FUNC_EXIT%"=="0" exit /b %FUNC_EXIT%
if not "%PERF_EXIT%"=="0" exit /b %PERF_EXIT%
exit /b %INT_EXIT%
