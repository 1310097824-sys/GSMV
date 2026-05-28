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

"%PYTHON_EXE%" -X utf8 "%SCRIPT_DIR%experiment6_excel_runner.py" --suite whitebox %*
exit /b %ERRORLEVEL%
