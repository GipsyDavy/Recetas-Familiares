#Requires -Version 7.0
<#
.SYNOPSIS
    Descarga/actualiza los packs de reglas de Semgrep para uso offline.

.DESCRIPTION
    El CLI de Semgrep no puede resolver `--config p/<pack>` en esta maquina: Avast
    intercepta TLS y su certificado raiz es rechazado por OpenSSL
    ("Basic Constraints of CA cert not marked critical"). PowerShell si valida contra
    el almacen de Windows, asi que descargamos los packs aqui y Semgrep los consume
    como archivos locales.

    Los packs se guardan en ../tools/security/semgrep-rules (fuera del repositorio).

.EXAMPLE
    pwsh -NoProfile -File scripts/security/update-semgrep-rules.ps1
#>
[CmdletBinding()]
param(
    [string[]]$Packs = @('java', 'kotlin', 'secrets', 'security-audit', 'owasp-top-ten'),
    [string]$RulesDir
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
if (-not $RulesDir) {
    $RulesDir = Join-Path (Split-Path $repoRoot -Parent) 'tools\security\semgrep-rules'
}
New-Item -ItemType Directory -Force -Path $RulesDir | Out-Null

$failed = 0
foreach ($pack in $Packs) {
    $url = "https://semgrep.dev/c/p/$pack"
    $dest = Join-Path $RulesDir "$pack.yaml"
    try {
        $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 60
        if ($resp.StatusCode -ne 200) { throw "HTTP $($resp.StatusCode)" }
        [System.IO.File]::WriteAllText($dest, $resp.Content)
        Write-Host "OK   p/$pack -> $dest ($($resp.RawContentLength) bytes)"
    }
    catch {
        Write-Host "FALLO p/$pack : $($_.Exception.Message)"
        $failed++
    }
}

Write-Host ''
Write-Host "Reglas en: $RulesDir"
Write-Host 'Nota: los packs community pueden traer un contador "missed" con reglas que exigen cuenta Semgrep.'
if ($failed -gt 0) { exit 1 }
exit 0
