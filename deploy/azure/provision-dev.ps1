# RepairDesk — Azure dev provisioning (Free-tier / minimal SKUs)
# Region: centralindia | Env: dev | RG: repair-desk-test
#
# Prerequisites:
#   1. az login   (or: az login --use-device-code)
#   2. Docker running (for local build) OR uses az acr build (cloud build)
#
# Usage:
#   cd C:\Codebase
#   .\deploy\azure\provision-dev.ps1
#
# Secrets are written to deploy/azure/.secrets.dev (gitignored).

$ErrorActionPreference = "Continue"

# Suppress az CLI stderr from triggering PowerShell errors
function Test-AzResource {
    param([scriptblock]$Command)
    & $Command 2>$null | Out-Null
    return ($LASTEXITCODE -eq 0)
}

function Invoke-Az {
    param([scriptblock]$Command)
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "Azure CLI failed (exit $LASTEXITCODE): $Command" }
}

# ── Config (India / dev / minimal cost) ─────────────────────────────────────
$LOCATION         = "centralindia"
$RESOURCE_GROUP   = "repair-desk-test"
$ACA_ENV          = "cae-repairdesk-dev"
$ACA_APP          = "repairdesk-api-dev"
$ACR_NAME         = "acrrepairdeskdev"   # alphanumeric, globally unique — change if taken
$PG_SERVER        = "psql-repairdesk-dev"
$PG_DB            = "repairdesk"
$PG_USER          = "repairdesk"
$API_IMAGE_TAG    = "1.0.0-dev"
$SKIP_FILES       = $true                # no Azure Files (attachments ephemeral)

$SecretsFile = Join-Path $PSScriptRoot ".secrets.dev"
$RepoRoot    = Resolve-Path (Join-Path $PSScriptRoot "..\..")

function Write-SecretFile($jwt, $dbPass, $adminPass) {
    @"
# Generated $(Get-Date -Format o) — DO NOT COMMIT
JWT_SECRET=$jwt
DB_PASSWORD=$dbPass
APP_ADMIN_DEFAULT_PASSWORD=$adminPass
"@ | Set-Content -Path $SecretsFile -Encoding UTF8
    Write-Host "Secrets saved to: $SecretsFile"
}

function Get-Or-GenerateSecrets {
    if (Test-Path $SecretsFile) {
        Write-Host "Using existing secrets file: $SecretsFile"
        $lines = Get-Content $SecretsFile
        $h = @{}
        foreach ($line in $lines) {
            if ($line -match '^([^#=]+)=(.+)$') { $h[$matches[1].Trim()] = $matches[2].Trim() }
        }
        return $h
    }
    Write-Host "Generating new secrets..."
    $jwt = -join ((48..57) + (97..102) | Get-Random -Count 64 | ForEach-Object { [char]$_ })
    # Better random via .NET
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $jwt = [BitConverter]::ToString($bytes).Replace("-", "").ToLower()
    $bytes2 = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes2)
    $dbPass = [Convert]::ToBase64String($bytes2)
    $bytes3 = New-Object byte[] 16
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes3)
    $adminPass = [Convert]::ToBase64String($bytes3) -replace '[^a-zA-Z0-9@#]', 'x'
    Write-SecretFile $jwt $dbPass $adminPass
    return @{ JWT_SECRET = $jwt; DB_PASSWORD = $dbPass; APP_ADMIN_DEFAULT_PASSWORD = $adminPass }
}

Write-Host "=== RepairDesk Azure Dev Provisioning ===" -ForegroundColor Cyan
Write-Host "Region: $LOCATION | RG: $RESOURCE_GROUP"

# Verify login
az account show -o table | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Not logged in. Run: az login --use-device-code" -ForegroundColor Red
    exit 1
}

# Container Apps CLI extension (preview)
$hasContainerApp = az extension list --query "[?name=='containerapp'].name" -o tsv
if (-not $hasContainerApp) {
    Write-Host "Installing az containerapp extension..."
    az extension add --name containerapp --upgrade -y | Out-Null
}

$secrets = Get-Or-GenerateSecrets
$JWT_SECRET = $secrets["JWT_SECRET"]
$DB_PASSWORD = $secrets["DB_PASSWORD"]
$ADMIN_PASSWORD = $secrets["APP_ADMIN_DEFAULT_PASSWORD"]

# ── 0. Register resource providers (first-time subscriptions) ───────────────
Write-Host "`n[0/7] Ensuring Azure resource providers..." -ForegroundColor Yellow
foreach ($ns in @(
    "Microsoft.ContainerRegistry",
    "Microsoft.App",
    "Microsoft.DBforPostgreSQL",
    "Microsoft.OperationalInsights",
    "Microsoft.Insights"
)) {
    $state = az provider show --namespace $ns --query registrationState -o tsv 2>$null
    if ($state -ne "Registered") {
        Write-Host "  Registering $ns..."
        az provider register --namespace $ns --wait | Out-Null
    }
}

# ── 1. Resource group ───────────────────────────────────────────────────────
Write-Host "`n[1/7] Resource group..." -ForegroundColor Yellow
az group create --name $RESOURCE_GROUP --location $LOCATION -o none

# ── 2. ACR (Basic — uses free credit; needed for Container Apps image) ───────
Write-Host "[2/7] Azure Container Registry..." -ForegroundColor Yellow
if (-not (Test-AzResource { az acr show --name $ACR_NAME })) {
    Invoke-Az { az acr create --resource-group $RESOURCE_GROUP --name $ACR_NAME --sku Basic --admin-enabled false -o none }
}
$ACR_LOGIN = az acr show --name $ACR_NAME --query loginServer -o tsv
$IMAGE = "${ACR_LOGIN}/repairdesk-api:${API_IMAGE_TAG}"

# ── 3. Build image (ACR cloud build, fallback to local Docker) ───────────────
Write-Host "[3/7] Building API image..." -ForegroundColor Yellow
$backendPath = Join-Path $RepoRoot "backend"
$buildOk = $false
az acr build --registry $ACR_NAME --image "repairdesk-api:${API_IMAGE_TAG}" $backendPath --no-logs 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
    $buildOk = $true
    Write-Host "  Built via ACR cloud build."
}
if (-not $buildOk) {
    Write-Host "  ACR cloud build unavailable; building locally with Docker..." -ForegroundColor DarkYellow
    Invoke-Az { az acr login --name $ACR_NAME }
    Invoke-Az { docker build -t $IMAGE $backendPath }
    Invoke-Az { docker push $IMAGE }
}

# ── 4. PostgreSQL Flexible Server (B1ms — smallest) ─────────────────────────
Write-Host "[4/7] PostgreSQL Flexible Server (B1ms)..." -ForegroundColor Yellow
if (-not (Test-AzResource { az postgres flexible-server show --resource-group $RESOURCE_GROUP --name $PG_SERVER })) {
    Invoke-Az {
        az postgres flexible-server create `
            --resource-group $RESOURCE_GROUP `
            --name $PG_SERVER `
            --location $LOCATION `
            --admin-user $PG_USER `
            --admin-password $DB_PASSWORD `
            --sku-name Standard_B1ms `
            --tier Burstable `
            --storage-size 32 `
            --version 16 `
            --public-access 0.0.0.0 `
            -o none
    }
}
if (-not (Test-AzResource { az postgres flexible-server db show --resource-group $RESOURCE_GROUP --server-name $PG_SERVER --database-name $PG_DB })) {
    Invoke-Az {
        az postgres flexible-server db create `
            --resource-group $RESOURCE_GROUP `
            --server-name $PG_SERVER `
            --name $PG_DB `
            -o none
    }
}
$DB_HOST = "${PG_SERVER}.postgres.database.azure.com"
$DB_URL = "jdbc:postgresql://${DB_HOST}:5432/${PG_DB}?sslmode=require"

# ── 5. Container Apps environment ───────────────────────────────────────────
Write-Host "[5/7] Container Apps environment..." -ForegroundColor Yellow
if (-not (Test-AzResource { az containerapp env show --name $ACA_ENV --resource-group $RESOURCE_GROUP })) {
    Invoke-Az {
        az containerapp env create `
            --resource-group $RESOURCE_GROUP `
            --name $ACA_ENV `
            --location $LOCATION `
            -o none
    }
}

# ── 6. Container App ────────────────────────────────────────────────────────
Write-Host "[6/7] Container App (API)..." -ForegroundColor Yellow
$envVars = @(
    "SPRING_PROFILES_ACTIVE=prod",
    "DB_URL=$DB_URL",
    "DB_USERNAME=$PG_USER",
    "DB_PASSWORD=secretref:db-password",
    "JWT_SECRET=secretref:jwt-secret",
    "STORAGE_LOCATION=/app/uploads",
    "CORS_ALLOWED_ORIGINS=https://localhost",
    "APP_ADMIN_DEFAULT_USERNAME=admin",
    "APP_ADMIN_DEFAULT_PASSWORD=secretref:admin-password",
    "APP_ADMIN_DEFAULT_MOBILE=0000000000"
)

az containerapp show --name $ACA_APP --resource-group $RESOURCE_GROUP 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Invoke-Az {
        az containerapp create `
            --resource-group $RESOURCE_GROUP `
            --name $ACA_APP `
            --environment $ACA_ENV `
            --image $IMAGE `
            --registry-server $ACR_LOGIN `
            --registry-identity system `
            --system-assigned `
            --target-port 8080 `
            --ingress external `
            --min-replicas 0 `
            --max-replicas 1 `
            --cpu 0.25 `
            --memory 0.5Gi `
            --secrets "db-password=$DB_PASSWORD" "jwt-secret=$JWT_SECRET" "admin-password=$ADMIN_PASSWORD" `
            --env-vars $envVars `
            -o none
    }
} else {
    Invoke-Az {
        az containerapp update `
            --resource-group $RESOURCE_GROUP `
            --name $ACA_APP `
            --image $IMAGE `
            --set-env-vars $envVars `
            -o none
    }
}

# ACR pull role for managed identity
$PRINCIPAL = az containerapp show -g $RESOURCE_GROUP -n $ACA_APP --query identity.principalId -o tsv
$ACR_ID = az acr show -g $RESOURCE_GROUP -n $ACR_NAME --query id -o tsv
if ($PRINCIPAL -and $PRINCIPAL -ne "None") {
    az role assignment create --assignee $PRINCIPAL --role AcrPull --scope $ACR_ID 2>$null | Out-Null
}

# Allow Azure services to reach PostgreSQL (dev only)
if (-not (Test-AzResource { az postgres flexible-server firewall-rule show --resource-group $RESOURCE_GROUP --server-name $PG_SERVER --name AllowAllAzureIPs })) {
    Invoke-Az {
        az postgres flexible-server firewall-rule create `
            --resource-group $RESOURCE_GROUP `
            --server-name $PG_SERVER `
            --name AllowAllAzureIPs `
            --start-ip-address 0.0.0.0 `
            --end-ip-address 0.0.0.0 `
            -o none
    }
}

# ── 7. Output ───────────────────────────────────────────────────────────────
Write-Host "`n[7/7] Done!" -ForegroundColor Green
$FQDN = az containerapp show -g $RESOURCE_GROUP -n $ACA_APP --query properties.configuration.ingress.fqdn -o tsv
$API_BASE = "https://${FQDN}/api/v1"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " API URL:  $API_BASE"
Write-Host " Health:   ${API_BASE}/actuator/health"
Write-Host " Ping:     ${API_BASE}/ping"
Write-Host " Secrets:  $SecretsFile"
Write-Host " Admin:    admin / (see secrets file)"
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Build Flutter APK:" -ForegroundColor Yellow
Write-Host "  cd frontend"
Write-Host "  flutter build apk --release --dart-define=API_BASE_URL=$API_BASE"
Write-Host ""
Write-Host "Cost note: B1ms PostgreSQL + Basic ACR use free-account credit (~`$15-20/mo after trial)." -ForegroundColor DarkYellow
