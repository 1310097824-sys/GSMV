$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$Python = if ($env:PYTHON) { $env:PYTHON } else { "python" }
$env:PYTHON = $Python
$DefaultOutputDir = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("RDpc6LSo6YeP5rWL6K+V5LiO5L+d6K+B5a6e6aqMXOWunumqjOWFrVzmtYvor5XmiafooYxFeGNlbOaKpeWRig=="))
$env:GSMV_EXPERIMENT6_REPORT_DIR = if ($env:GSMV_EXPERIMENT6_REPORT_DIR) { $env:GSMV_EXPERIMENT6_REPORT_DIR } else { $DefaultOutputDir }
$env:GSMV_BASE_URL = if ($env:GSMV_BASE_URL) { $env:GSMV_BASE_URL } else { "http://localhost:8080" }
$env:GSMV_FRONTEND_URL = if ($env:GSMV_FRONTEND_URL) { $env:GSMV_FRONTEND_URL } else { "http://localhost:5173" }
$env:GSMV_TEST_IMAGE = if ($env:GSMV_TEST_IMAGE) { $env:GSMV_TEST_IMAGE } else { "C:\Users\13100\Pictures\OIP.jpg" }

& (Join-Path $PSScriptRoot "run_whitebox_excel.ps1")

if (-not $env:BAILIAN_API_KEY -or -not $env:DEEPSEEK_API_KEY) {
  throw "Please set BAILIAN_API_KEY and DEEPSEEK_API_KEY before running real system tests."
}

try {
  $StartScript = Join-Path $Root "scripts\start-gsmv.ps1"
  $StopScript = Join-Path $Root "scripts\stop-gsmv.ps1"
  & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $StartScript -Root $Root -BailianApiKey $env:BAILIAN_API_KEY -DeepSeekApiKey $env:DEEPSEEK_API_KEY
  & (Join-Path $PSScriptRoot "run_functional_excel.ps1")
  & (Join-Path $PSScriptRoot "run_performance_excel.ps1")
  & (Join-Path $PSScriptRoot "run_integration_excel.ps1")
} finally {
  if (-not $StopScript) {
    $StopScript = Join-Path $Root "scripts\stop-gsmv.ps1"
  }
  & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $StopScript -Root $Root
}
