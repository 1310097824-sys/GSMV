param(
  [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Continue'

$rootCandidate = $Root.Trim('"')
$rootPath = [System.IO.Path]::GetFullPath($rootCandidate)
$runDir = Join-Path $rootPath '.gsmv-runtime'

function Stop-ProcessTreeByPid {
  param(
    [string]$Name,
    [int]$ProcessId
  )

  $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
  if (-not $process) {
    return $false
  }

  Write-Host "Stopping $Name process tree: PID $ProcessId"
  & taskkill.exe /PID $ProcessId /T /F | Out-Null
  return $true
}

function Stop-ProcessTreeByPidFile {
  param(
    [string]$Name,
    [string]$PidPath
  )

  if (-not (Test-Path $PidPath)) {
    return
  }

  $rawPid = (Get-Content -Path $PidPath -ErrorAction SilentlyContinue | Select-Object -First 1)
  $processId = 0
  if ([int]::TryParse($rawPid, [ref]$processId)) {
    [void](Stop-ProcessTreeByPid -Name $Name -ProcessId $processId)
  }

  Remove-Item -Path $PidPath -Force -ErrorAction SilentlyContinue
}

function Stop-ListenerByPort {
  param([int]$Port)

  $listeners = netstat.exe -ano -p tcp |
    Select-String -Pattern "[:.]$Port\s+.*LISTENING\s+(\d+)" |
    ForEach-Object { [int]$_.Matches[0].Groups[1].Value } |
    Select-Object -Unique

  foreach ($processId in $listeners) {
    [void](Stop-ProcessTreeByPid -Name "port $Port listener" -ProcessId $processId)
  }
}

if (Test-Path $runDir) {
  Stop-ProcessTreeByPidFile -Name 'backend' -PidPath (Join-Path $runDir 'backend.pid')
  Stop-ProcessTreeByPidFile -Name 'frontend' -PidPath (Join-Path $runDir 'frontend.pid')
  $qdrantPath = Join-Path $runDir 'qdrant.container'
  if (Test-Path $qdrantPath) {
    $containerName = (Get-Content -Path $qdrantPath -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($containerName) {
      $docker = Get-Command docker.exe -ErrorAction SilentlyContinue
      if ($docker) {
        Write-Host "Stopping Qdrant container: $containerName"
        docker.exe stop $containerName | Out-Null
      }
    }
    Remove-Item -Path $qdrantPath -Force -ErrorAction SilentlyContinue
  }
}

Stop-ListenerByPort -Port 8080
Stop-ListenerByPort -Port 5173

Write-Host ''
Write-Host 'GSMV stop command finished.'
