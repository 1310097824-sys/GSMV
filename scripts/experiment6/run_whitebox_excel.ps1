$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$Python = if ($env:PYTHON) { $env:PYTHON } else { "python" }
$DefaultOutputDir = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("RDpc6LSo6YeP5rWL6K+V5LiO5L+d6K+B5a6e6aqMXOWunumqjOWFrVzmtYvor5XmiafooYxFeGNlbOaKpeWRig=="))
$OutputDir = if ($env:GSMV_EXPERIMENT6_REPORT_DIR) { $env:GSMV_EXPERIMENT6_REPORT_DIR } else { $DefaultOutputDir }
& $Python (Join-Path $PSScriptRoot "experiment6_excel_runner.py") --suite whitebox --output-dir $OutputDir
