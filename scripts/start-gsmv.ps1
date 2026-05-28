param(
  [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
  [AllowEmptyString()]
  [string]$BailianApiKey = '',
  [AllowEmptyString()]
  [string]$DeepSeekApiKey = '',
  [AllowEmptyString()]
  [string]$IucnApiToken = $(if ($env:IUCN_API_TOKEN) { $env:IUCN_API_TOKEN } else { [Environment]::GetEnvironmentVariable('IUCN_API_TOKEN', 'User') })
)

$ErrorActionPreference = 'Stop'

$rootCandidate = $Root.Trim('"')
$rootPath = [System.IO.Path]::GetFullPath($rootCandidate)
$frontendPath = Join-Path $rootPath 'frontend'
$runDir = Join-Path $rootPath '.gsmv-runtime'
$logDir = Join-Path $runDir 'logs'

New-Item -ItemType Directory -Force -Path $runDir, $logDir | Out-Null

function Stop-ExistingProcessTree {
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
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($process) {
      Write-Host "Stopping previous $Name process tree: PID $processId"
      & taskkill.exe /PID $processId /T /F | Out-Null
    }
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
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($process) {
      Write-Host "Stopping existing listener on port ${Port}: PID $processId"
      & taskkill.exe /PID $processId /T /F | Out-Null
    }
  }
}

function Wait-HttpReady {
  param(
    [string]$Url,
    [int]$TimeoutSeconds = 90
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    try {
      $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
        return $true
      }
    } catch {
      Start-Sleep -Milliseconds 800
      continue
    }
    Start-Sleep -Milliseconds 500
  }

  return $false
}

function ConvertTo-CmdSetValue {
  param([AllowNull()][string]$Value)

  if ($null -eq $Value) {
    return ''
  }

  $escaped = $Value.Replace('^', '^^')
  $escaped = $escaped.Replace('&', '^&')
  $escaped = $escaped.Replace('|', '^|')
  $escaped = $escaped.Replace('<', '^<')
  $escaped = $escaped.Replace('>', '^>')
  $escaped = $escaped.Replace('"', '^"')
  return $escaped
}

function Start-QdrantIfAvailable {
  $docker = Get-Command docker.exe -ErrorAction SilentlyContinue
  if (-not $docker) {
    Write-Host 'Docker was not found. Qdrant will be skipped and RAG will fall back to MySQL vectors.'
    return $false
  }

  $containerName = 'gsmv-qdrant'
  try {
    $existing = docker.exe ps -a --filter "name=^/${containerName}$" --format "{{.Names}}" 2>$null
    if ($LASTEXITCODE -ne 0) {
      Write-Host 'Docker is installed but not ready. Qdrant will be skipped for this launch.'
      return $false
    }

    if ($existing -contains $containerName) {
      $running = docker.exe ps --filter "name=^/${containerName}$" --format "{{.Names}}" 2>$null
      if ($running -contains $containerName) {
        Write-Host 'Qdrant container is already running.'
      } else {
        Write-Host 'Starting existing Qdrant container.'
        docker.exe start $containerName | Out-Null
      }
    } else {
      Write-Host 'Creating Qdrant container gsmv-qdrant on port 6333.'
      docker.exe run -d --name $containerName -p 6333:6333 -v "${containerName}_data:/qdrant/storage" qdrant/qdrant | Out-Null
    }

    $ready = Wait-HttpReady -Url 'http://localhost:6333/collections' -TimeoutSeconds 25
    if ($ready) {
      $containerName | Set-Content -Path (Join-Path $runDir 'qdrant.container') -Encoding ASCII
      return $true
    }
    Write-Host 'Qdrant container started but is not ready yet. The app will still start and can fall back to MySQL vectors.'
    return $false
  } catch {
    Write-Host "Qdrant startup skipped: $($_.Exception.Message)"
    return $false
  }
}

$backendPidPath = Join-Path $runDir 'backend.pid'
$frontendPidPath = Join-Path $runDir 'frontend.pid'

Stop-ExistingProcessTree -Name 'backend' -PidPath $backendPidPath
Stop-ExistingProcessTree -Name 'frontend' -PidPath $frontendPidPath
Stop-ListenerByPort -Port 8080
Stop-ListenerByPort -Port 5173

$qdrantStarted = Start-QdrantIfAvailable

$backendOut = Join-Path $logDir 'backend.out.log'
$backendErr = Join-Path $logDir 'backend.err.log'
$frontendOut = Join-Path $logDir 'frontend.out.log'
$frontendErr = Join-Path $logDir 'frontend.err.log'

foreach ($logFile in @($backendOut, $backendErr, $frontendOut, $frontendErr)) {
  Set-Content -Path $logFile -Value $null
}

$bailianKeyForRun = ConvertTo-CmdSetValue $BailianApiKey
$deepSeekKeyForRun = ConvertTo-CmdSetValue $DeepSeekApiKey
$iucnTokenForRun = ConvertTo-CmdSetValue $IucnApiToken
$backendEnvCommand = @(
  "set `"BAILIAN_API_KEY=$bailianKeyForRun`"",
  "set `"DASHSCOPE_API_KEY=$bailianKeyForRun`"",
  "set `"DEEPSEEK_API_KEY=$deepSeekKeyForRun`"",
  "set `"IUCN_API_TOKEN=$iucnTokenForRun`""
) -join ' && '

$backendCommand = "cd /d `"$rootPath`" && $backendEnvCommand && call mvnw.cmd spring-boot:run 1>> `"$backendOut`" 2>> `"$backendErr`""
$frontendRun = "call npm.cmd run dev -- --host localhost 1>> `"$frontendOut`" 2>> `"$frontendErr`""
$frontendInstallAndRun = "call npm.cmd install 1>> `"$frontendOut`" 2>> `"$frontendErr`" && $frontendRun"
$frontendCommand = "cd /d `"$frontendPath`" && if exist node_modules ($frontendRun) else ($frontendInstallAndRun)"

$backend = Start-Process `
  -FilePath 'cmd.exe' `
  -ArgumentList @('/d', '/s', '/c', $backendCommand) `
  -WorkingDirectory $rootPath `
  -WindowStyle Minimized `
  -PassThru

$frontend = Start-Process `
  -FilePath 'cmd.exe' `
  -ArgumentList @('/d', '/s', '/c', $frontendCommand) `
  -WorkingDirectory $frontendPath `
  -WindowStyle Minimized `
  -PassThru

$backend.Id | Set-Content -Path $backendPidPath -Encoding ASCII
$frontend.Id | Set-Content -Path $frontendPidPath -Encoding ASCII

$backendReady = Wait-HttpReady -Url 'http://localhost:8080/actuator/health'
$frontendReady = Wait-HttpReady -Url 'http://localhost:5173/login'
$launchUrl = 'http://localhost:5173/login?fresh=1'

Write-Host ''
Write-Host 'GSMV is starting.'
Write-Host "Backend  PID: $($backend.Id)  URL: http://localhost:8080"
Write-Host "Frontend PID: $($frontend.Id) URL: http://localhost:5173"
if ($qdrantStarted) {
  Write-Host 'Qdrant  URL: http://localhost:6333'
}
Write-Host "Logs: $logDir"
Write-Host ''
if ($backendReady -and $frontendReady) {
  Write-Host 'Backend and frontend are ready. Opening the login page.'
} else {
  Write-Host 'Startup is still warming up. Open the login page again in a few seconds if needed.'
}

if ($env:GSMV_NO_BROWSER -ne '1') {
  Start-Process $launchUrl
}
