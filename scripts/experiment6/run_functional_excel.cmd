@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
if defined PYTHON (
  set "PYTHON_EXE=%PYTHON%"
) else if exist "C:\Users\13100\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" (
  set "PYTHON_EXE=C:\Users\13100\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
) else (
  set "PYTHON_EXE=python"
)

set "BASE_URL=http://localhost:8080"
set "FRONTEND_URL=http://localhost:5173"
set "TEST_IMAGE=C:\Users\13100\Pictures\OIP.jpg"
if defined GSMV_BASE_URL set "BASE_URL=%GSMV_BASE_URL%"
if defined GSMV_FRONTEND_URL set "FRONTEND_URL=%GSMV_FRONTEND_URL%"
if defined GSMV_TEST_IMAGE set "TEST_IMAGE=%GSMV_TEST_IMAGE%"

"%PYTHON_EXE%" -X utf8 "%SCRIPT_DIR%experiment6_excel_runner.py" --suite functional --base-url "%BASE_URL%" --frontend-url "%FRONTEND_URL%" --image "%TEST_IMAGE%" %*
exit /b %ERRORLEVEL%
