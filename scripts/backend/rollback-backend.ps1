param(
    [string]$HostName = "167.233.213.242",
    [string]$User = "recetas-deploy",
    [int]$Port = 22,
    [string]$KeyPath = "herztner\recetas-backend-deploy-ed25519",
    [string]$HealthUrl = "https://recetas.167.233.213.242.sslip.io/api/v1/health"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $KeyPath)) {
    throw "No existe la clave SSH de deploy: $KeyPath"
}

ssh -i $KeyPath -p $Port -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o BatchMode=yes "$User@$HostName" rollback

if ($HealthUrl) {
    curl.exe --ssl-no-revoke --fail --silent --show-error --max-time 10 $HealthUrl | Out-Null
}
