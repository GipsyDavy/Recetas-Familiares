#Requires -Version 7.0
<#
.SYNOPSIS
    Escaneo de seguridad de sprint: Semgrep (SAST) + TruffleHog (secretos).

.DESCRIPTION
    Ejecuta ambas herramientas sobre el repositorio y escribe los informes en
    .security-reports/<timestamp>/ (directorio ignorado por git).

    Modos:
      quick   Semgrep solo sobre archivos modificados vs BaseRef + TruffleHog worktree.
      sprint  Semgrep sobre todo el repo + TruffleHog worktree + historial desde BaseRef. (por defecto)
      full    Igual que sprint pero con historial git completo.

    Codigos de salida:
      0  sin hallazgos bloqueantes
      1  hallazgos bloqueantes (Semgrep ERROR o secretos verificados)
      2  error de configuracion o de herramienta

.EXAMPLE
    pwsh -NoProfile -File scripts/security/run-security-scan.ps1
.EXAMPLE
    pwsh -NoProfile -File scripts/security/run-security-scan.ps1 -Mode quick
.EXAMPLE
    pwsh -NoProfile -File scripts/security/run-security-scan.ps1 -Mode full -NoVerify
#>
[CmdletBinding()]
param(
    [ValidateSet('quick', 'sprint', 'full')]
    [string]$Mode = 'sprint',

    [string]$BaseRef = 'origin/main',

    [switch]$SkipSemgrep,
    [switch]$SkipTruffleHog,

    # Desactiva la verificacion online de TruffleHog (no envia credenciales candidatas
    # al proveedor emisor). Mas ruido de falsos positivos a cambio de cero egreso.
    [switch]$NoVerify,

    [string]$OutputDir
)

$ErrorActionPreference = 'Stop'

# --- Contexto -------------------------------------------------------------

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
$toolsRoot = Join-Path (Split-Path $repoRoot -Parent) 'tools\security'

if (-not $OutputDir) {
    $OutputDir = Join-Path $repoRoot '.security-reports'
}
$runDir = Join-Path $OutputDir (Get-Date -Format 'yyyyMMdd-HHmmss')
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$summary = [System.Collections.Generic.List[string]]::new()
function Add-Summary([string]$Line) {
    $summary.Add($Line)
    Write-Host $Line
}

function Resolve-Tool {
    param([string]$Name, [string[]]$Candidates)
    foreach ($c in $Candidates) {
        if ($c -and (Test-Path $c)) { return (Resolve-Path $c).Path }
    }
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

$blocking = 0
$toolErrors = 0

Add-Summary "# Escaneo de seguridad - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Add-Summary "Modo: $Mode | Repo: $repoRoot"
Add-Summary "Informes: $runDir"
Add-Summary ''

# --- Semgrep --------------------------------------------------------------

if ($SkipSemgrep) {
    Add-Summary '## Semgrep: OMITIDO (-SkipSemgrep)'
}
else {
    $semgrep = Resolve-Tool -Name 'semgrep' -Candidates @($env:SEMGREP_BIN)
    $rulesDir = if ($env:SEMGREP_RULES_DIR) { $env:SEMGREP_RULES_DIR } else { Join-Path $toolsRoot 'semgrep-rules' }

    if (-not $semgrep) {
        Add-Summary '## Semgrep: NO DISPONIBLE (binario no encontrado)'
        $toolErrors++
    }
    elseif (-not (Test-Path $rulesDir)) {
        Add-Summary "## Semgrep: NO DISPONIBLE (reglas locales ausentes en $rulesDir)"
        Add-Summary '   Ejecutar scripts/security/update-semgrep-rules.ps1'
        $toolErrors++
    }
    else {
        # El registry de semgrep.dev no es accesible desde el CLI (MITM TLS de Avast),
        # por eso se usan snapshots locales de los packs oficiales.
        $ruleFiles = @(Get-ChildItem -Path $rulesDir -Filter *.yaml -File)
        $configArgs = @()
        foreach ($f in $ruleFiles) { $configArgs += @('--config', $f.FullName) }

        $targets = @()
        if ($Mode -eq 'quick') {
            $base = (& git -C $repoRoot merge-base $BaseRef HEAD 2>$null)
            if (-not $base) { $base = 'HEAD~1' }
            $targets += @(& git -C $repoRoot diff --name-only --diff-filter=ACMR $base HEAD 2>$null)
            $targets += @(& git -C $repoRoot status --porcelain 2>$null |
                    Where-Object { $_.Length -gt 3 } |
                    ForEach-Object { $_.Substring(3).Trim().Trim('"') })
            $targets = @($targets | Sort-Object -Unique |
                    ForEach-Object { Join-Path $repoRoot $_ } |
                    Where-Object { Test-Path $_ -PathType Leaf })
            if ($targets.Count -eq 0) {
                Add-Summary '## Semgrep: sin archivos modificados que analizar'
            }
            elseif ($targets.Count -gt 200) {
                Add-Summary "## Semgrep: $($targets.Count) archivos modificados, se analiza el repo completo"
                $targets = @($repoRoot)
            }
        }
        else {
            $targets = @($repoRoot)
        }

        if ($targets.Count -gt 0) {
            $env:SEMGREP_SEND_METRICS = 'off'
            $env:SEMGREP_ENABLE_VERSION_CHECK = '0'
            $jsonPath = Join-Path $runDir 'semgrep.json'

            Push-Location $repoRoot
            try {
                & $semgrep scan --metrics off --disable-version-check --quiet `
                    @configArgs --json --output $jsonPath @targets 2>&1 |
                    Out-File -FilePath (Join-Path $runDir 'semgrep.log') -Encoding utf8
                $semgrepExit = $LASTEXITCODE
            }
            finally {
                Pop-Location
            }

            if (-not (Test-Path $jsonPath)) {
                Add-Summary "## Semgrep: ERROR de ejecucion (exit $semgrepExit), ver semgrep.log"
                $toolErrors++
            }
            else {
                $sg = Get-Content $jsonPath -Raw | ConvertFrom-Json
                $results = @($sg.results)

                # Semgrep usa dos escalas segun la regla: ERROR/WARNING/INFO y
                # CRITICAL/HIGH/MEDIUM/LOW. Se cuentan todas y bloquean las altas.
                $blockingSeverities = @('ERROR', 'CRITICAL', 'HIGH')
                $bySeverity = [ordered]@{}
                foreach ($r in $results) {
                    $sev = "$($r.extra.severity)".ToUpperInvariant()
                    if ($bySeverity.Contains($sev)) { $bySeverity[$sev]++ } else { $bySeverity[$sev] = 1 }
                }
                $counts = if ($bySeverity.Count -gt 0) {
                    ($bySeverity.Keys | Sort-Object | ForEach-Object { "$_=$($bySeverity[$_])" }) -join ' '
                }
                else { 'sin hallazgos' }
                $blockingCount = 0
                foreach ($sev in $blockingSeverities) {
                    if ($bySeverity.Contains($sev)) { $blockingCount += $bySeverity[$sev] }
                }

                Add-Summary "## Semgrep: $($results.Count) hallazgos (reglas: $($ruleFiles.Count) packs locales)"
                Add-Summary "   $counts | bloqueantes ($($blockingSeverities -join '/')): $blockingCount"

                foreach ($r in ($results | Sort-Object { $_.extra.severity } | Select-Object -First 40)) {
                    $rel = "$($r.path)".Replace("$repoRoot\", '')
                    # El check_id local arrastra la ruta del archivo de reglas; se recorta.
                    $rule = "$($r.check_id)" -replace '^.*semgrep-rules\.', ''
                    Add-Summary "   [$($r.extra.severity)] $rel`:$($r.start.line) - $rule"
                }
                if ($results.Count -gt 40) {
                    Add-Summary "   ... $($results.Count - 40) hallazgos mas en semgrep.json"
                }
                if ($blockingCount -gt 0) { $blocking++ }
            }
        }
    }
}

Add-Summary ''

# --- TruffleHog -----------------------------------------------------------

function Get-TruffleFindings {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return @() }
    return @(Get-Content $Path | Where-Object { $_ -match '"DetectorName"' } | ForEach-Object { $_ | ConvertFrom-Json })
}

function Format-TruffleFinding {
    param($Finding, [string]$Root)
    $data = $Finding.SourceMetadata.Data
    $loc = if ($data.Git) {
        "$($data.Git.file):$($data.Git.line) @$("$($data.Git.commit)".Substring(0, [Math]::Min(8, "$($data.Git.commit)".Length)))"
    }
    elseif ($data.Filesystem) {
        "$("$($data.Filesystem.file)".Replace("$Root\", '')):$($data.Filesystem.line)"
    }
    else { '(origen desconocido)' }
    $state = if ($Finding.Verified) { 'VERIFICADO' } else { 'no verificado' }
    return "   [$state] $($Finding.DetectorName) - $loc - $($Finding.Redacted)"
}

if ($SkipTruffleHog) {
    Add-Summary '## TruffleHog: OMITIDO (-SkipTruffleHog)'
}
else {
    $truffleCandidates = @($env:TRUFFLEHOG_BIN)
    $truffleCandidates += @(
        Get-ChildItem -Path (Join-Path $toolsRoot 'trufflehog') -Filter 'trufflehog.exe' -Recurse -File -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            ForEach-Object { $_.FullName }
    )
    $trufflehog = Resolve-Tool -Name 'trufflehog' -Candidates $truffleCandidates

    if (-not $trufflehog) {
        Add-Summary '## TruffleHog: NO DISPONIBLE (binario no encontrado)'
        $toolErrors++
    }
    else {
        $excludeFile = Join-Path $PSScriptRoot 'trufflehog-exclude.txt'
        $commonArgs = @('--json', '--no-update', '--results=verified,unknown')
        if (Test-Path $excludeFile) { $commonArgs += @('--exclude-paths', $excludeFile) }
        if ($NoVerify) { $commonArgs += '--no-verification' }

        # 1) Arbol de trabajo (detecta secretos antes de commit)
        $fsPath = Join-Path $runDir 'trufflehog-filesystem.jsonl'
        $fsLog = Join-Path $runDir 'trufflehog-filesystem.log'
        & $trufflehog filesystem $repoRoot @commonArgs 2> $fsLog |
            Set-Content -Path $fsPath -Encoding utf8
        $fsFindings = Get-TruffleFindings -Path $fsPath

        # 2) Historial git
        $gitFindings = @()
        $gitScanned = $false
        if ($Mode -ne 'quick') {
            $gitArgs = @('git', "file://$repoRoot") + $commonArgs
            if ($Mode -eq 'sprint') {
                $baseSha = (& git -C $repoRoot rev-parse --verify "$BaseRef" 2>$null)
                $headSha = (& git -C $repoRoot rev-parse --verify HEAD 2>$null)
                if ($baseSha -and $headSha -and $baseSha -ne $headSha) {
                    $gitArgs += @('--since-commit', $baseSha)
                    $gitScanned = $true
                }
                else {
                    Add-Summary "## TruffleHog: historial omitido ($BaseRef == HEAD); usar -Mode full para el historial completo"
                }
            }
            else {
                $gitScanned = $true
            }

            if ($gitScanned) {
                $gitPath = Join-Path $runDir 'trufflehog-git.jsonl'
                $gitLog = Join-Path $runDir 'trufflehog-git.log'
                & $trufflehog @gitArgs 2> $gitLog |
                    Set-Content -Path $gitPath -Encoding utf8
                $gitFindings = Get-TruffleFindings -Path $gitPath
            }
        }

        $all = @($fsFindings) + @($gitFindings)
        $verified = @($all | Where-Object { $_.Verified })
        Add-Summary "## TruffleHog: $($all.Count) hallazgos (worktree=$($fsFindings.Count), historial=$($gitFindings.Count))"
        Add-Summary "   Verificados: $($verified.Count)"
        foreach ($f in ($all | Sort-Object -Property @{Expression = { -not $_.Verified } } | Select-Object -First 40)) {
            Add-Summary (Format-TruffleFinding -Finding $f -Root $repoRoot)
        }
        if ($all.Count -gt 40) {
            Add-Summary "   ... $($all.Count - 40) hallazgos mas en los .jsonl"
        }
        if ($verified.Count -gt 0) { $blocking++ }
    }
}

# --- Cierre ---------------------------------------------------------------

Add-Summary ''
if ($toolErrors -gt 0) {
    Add-Summary "RESULTADO: INCOMPLETO - $toolErrors herramienta(s) no disponible(s). Documentar riesgo residual."
}
elseif ($blocking -gt 0) {
    Add-Summary 'RESULTADO: BLOQUEANTE - revisar hallazgos antes de cerrar el sprint.'
}
else {
    Add-Summary 'RESULTADO: OK - sin hallazgos bloqueantes.'
}

$summary | Set-Content -Path (Join-Path $runDir 'summary.md') -Encoding utf8

if ($toolErrors -gt 0) { exit 2 }
if ($blocking -gt 0) { exit 1 }
exit 0
