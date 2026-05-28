$ErrorActionPreference = "Stop"
$Python = if ($env:PYTHON) { $env:PYTHON } else { "python" }
$DefaultOutputDir = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("RDpc6LSo6YeP5rWL6K+V5LiO5L+d6K+B5a6e6aqMXOWunumqjOWFrVzmtYvor5XmiafooYxFeGNlbOaKpeWRig=="))
$OutputDir = if ($env:GSMV_EXPERIMENT6_REPORT_DIR) { $env:GSMV_EXPERIMENT6_REPORT_DIR } else { $DefaultOutputDir }
$BaseUrl = if ($env:GSMV_BASE_URL) { $env:GSMV_BASE_URL } else { "http://localhost:8080" }
$FrontendUrl = if ($env:GSMV_FRONTEND_URL) { $env:GSMV_FRONTEND_URL } else { "http://localhost:5173" }
$Image = if ($env:GSMV_TEST_IMAGE) { $env:GSMV_TEST_IMAGE } else { "C:\Users\13100\Pictures\OIP.jpg" }
& $Python (Join-Path $PSScriptRoot "experiment6_excel_runner.py") --suite integration --output-dir $OutputDir --base-url $BaseUrl --frontend-url $FrontendUrl --image $Image
