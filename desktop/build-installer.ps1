# ==============================================================================
# build-installer.ps1 — Recetas Familiares v1.1 — Instalador Windows
# ==============================================================================
# Requisitos:
#   - JDK 21 LTS recomendado para empaquetar el runtime embebido
#   - Maven accesible (NetBeans, IntelliJ o PATH)
#   - Inno Setup 6 para generar el .exe final (opcional — sin él se crea el app-image)
#
# Uso:
#   .\build-installer.ps1
#   .\build-installer.ps1 -ApiUrl "http://192.168.1.100:8080/"
# ==============================================================================

param(
    [string]$ApiUrl = "https://recetas.167.233.213.242.sslip.io/",
    [string]$JdkPath = "",
    [switch]$AllowNonLtsJdk
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── Configuración ──────────────────────────────────────────────────────────────
$AppName        = "RecetasFamiliares"
$AppDisplayName = "Recetas Familiares"
$AppVersion     = "1.2"
$MainJar        = "RecetasFamiliares.jar"
$MainClass      = "org.gipsybuho.recetasfamiliares.Launcher"
$JavaFxModules  = "javafx.controls,javafx.fxml,javafx.base,javafx.graphics"
$ProjectDir     = $PSScriptRoot
$TargetDir      = "$ProjectDir\target"
$InputDir       = "$TargetDir\jpackage-input"
$ModsDir        = "$InputDir\mods"
$OutputDir      = "$ProjectDir\output"
$IcoPath        = "$ProjectDir\installer\recetas.ico"
$PngPath        = "$ProjectDir\src\main\resources\brand\gipsy-buho-logo.png"
# Derivado de $AppVersion: debe coincidir con <version> del pom.xml.
$ShadedJar      = "$TargetDir\recetas-familiares-desktop-$AppVersion.jar"

# ── Rutas de herramientas ─────────────────────────────────────────────────────
$NSIS = "C:\Program Files (x86)\NSIS\makensis.exe"

$InnoSetupCandidates = @(
    "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
    "C:\Program Files\Inno Setup 6\ISCC.exe",
    "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe"
)
function Find-InnoSetup {
    foreach ($candidate in $InnoSetupCandidates) {
        if (Test-Path $candidate) { return $candidate }
    }
    $iscc = Get-Command ISCC.exe -ErrorAction SilentlyContinue
    if ($iscc) { return $iscc.Source }
    return $null
}
$InnoSetup = Find-InnoSetup

$MvnCandidates = @(
    "C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd",
    "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3\bin\mvn.cmd",
    "C:\Program Files\JetBrains\IntelliJ IDEA 2025.1\plugins\maven\lib\maven3\bin\mvn.cmd",
    "mvn.cmd",
    "mvn"
)

# ── Helpers ────────────────────────────────────────────────────────────────────
function Write-Step { param($msg) Write-Host "`n  $msg" -ForegroundColor Cyan }
function Write-OK   { param($msg) Write-Host "    [OK]  $msg" -ForegroundColor Green }
function Write-Warn { param($msg) Write-Host "    [!]   $msg" -ForegroundColor Yellow }
function Write-Fail { param($msg) Write-Host "    [ERR] $msg" -ForegroundColor Red }

function Find-Maven {
    foreach ($candidate in $MvnCandidates) {
        if (Test-Path $candidate)                                    { return $candidate }
        if (Get-Command $candidate -ErrorAction SilentlyContinue)   { return $candidate }
    }
    return $null
}

function Get-JdkMajorVersion {
    param([string]$Path)
    if (-not (Test-Path "$Path\bin\java.exe")) { return $null }
    $versionLine = & "$Path\bin\java.exe" -version 2>&1 | Select-Object -First 1
    if ($versionLine -match '"(\d+)') { return [int]$Matches[1] }
    return $null
}

function Find-PackagingJdk {
    if ($JdkPath -and (Test-Path "$JdkPath\bin\jpackage.exe")) {
        return $JdkPath
    }

    $candidates = @()
    if ($env:RECETAS_JDK21) { $candidates += $env:RECETAS_JDK21 }
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += @(
        "C:\Program Files\Eclipse Adoptium\jdk-21",
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Java\jdk-21.0.9",
        "C:\Program Files\Java\jdk-21.0.8",
        "C:\Program Files\Java\jdk-21.0.7",
        "C:\Program Files\Java\jdk-21.0.6",
        "C:\Program Files\Java\jdk-21.0.5"
    )

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if ((Test-Path "$candidate\bin\jpackage.exe") -and (Get-JdkMajorVersion $candidate) -eq 21) {
            return $candidate
        }
    }

    $jpackage = Get-Command jpackage.exe -ErrorAction SilentlyContinue
    if ($jpackage) {
        return Split-Path (Split-Path $jpackage.Source -Parent) -Parent
    }

    return $null
}

# Crea un fichero .ico a partir de un .png usando System.Drawing (siempre disponible en Windows).
# El ICO contendrá la imagen en 256x256 formato PNG (soportado desde Windows Vista).
function New-IcoFromPng {
    param([string]$Png, [string]$Ico)

    # 1) Intentar Python PIL (mejor calidad, multi-resolución)
    $python = Get-Command python -ErrorAction SilentlyContinue
    if ($python) {
        $script = "from PIL import Image; img=Image.open(r'$Png').convert('RGBA'); img.save(r'$Ico',format='ICO',sizes=[(256,256),(128,128),(64,64),(48,48),(32,32),(16,16)]); print('OK')"
        $result = python -c $script 2>$null
        if ($LASTEXITCODE -eq 0 -and $result -eq 'OK') { return $true }
    }

    # 2) Intentar ImageMagick
    $magick = Get-Command magick -ErrorAction SilentlyContinue
    if ($magick) {
        magick convert "$Png" -define icon:auto-resize=256,128,64,48,32,16 "$Ico" 2>$null
        if ($LASTEXITCODE -eq 0) { return $true }
    }

    # 3) Fallback: System.Drawing — ICO con una sola entrada 256×256 en formato PNG
    try {
        Add-Type -AssemblyName System.Drawing
        $src = [System.Drawing.Image]::FromFile($Png)
        $bmp = New-Object System.Drawing.Bitmap 256, 256
        $g   = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.DrawImage($src, 0, 0, 256, 256)
        $g.Dispose()

        $pngMs = New-Object System.IO.MemoryStream
        $bmp.Save($pngMs, [System.Drawing.Imaging.ImageFormat]::Png)
        $pngBytes = $pngMs.ToArray()
        $pngMs.Dispose(); $bmp.Dispose(); $src.Dispose()

        $fs = [System.IO.File]::Create($Ico)
        $bw = New-Object System.IO.BinaryWriter $fs
        $bw.Write([uint16]0)                       # reserved
        $bw.Write([uint16]1)                       # type: ICO
        $bw.Write([uint16]1)                       # num images: 1
        $bw.Write([byte]0)                         # width  (0 = 256)
        $bw.Write([byte]0)                         # height (0 = 256)
        $bw.Write([byte]0)                         # color count
        $bw.Write([byte]0)                         # reserved
        $bw.Write([uint16]1)                       # planes
        $bw.Write([uint16]32)                      # bpp
        $bw.Write([uint32]$pngBytes.Length)        # data size
        $bw.Write([uint32]22)                      # data offset (6 header + 16 entry)
        $bw.Write($pngBytes)
        $bw.Flush(); $bw.Dispose(); $fs.Dispose()
        return $true
    } catch {
        return $false
    }
}

# Genera los dos BMP requeridos por NSIS MUI2 usando System.Drawing (siempre disponible)
function New-NsisBitmaps {
    param([string]$InstallerDir)
    Add-Type -AssemblyName System.Drawing
    $brown  = [System.Drawing.Color]::FromArgb(61,  43, 31)   # #3D2B1F sidebar
    $terra  = [System.Drawing.Color]::FromArgb(193, 125, 82)  # #C17D52 accent
    $white  = [System.Drawing.Brushes]::White
    $lgray  = [System.Drawing.Brushes]::LightGray

    # ── Welcome panel 164x314 ─────────────────────────────────
    $bmp = New-Object System.Drawing.Bitmap 164, 314
    $g   = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear($brown)

    # Franja superior con gradiente
    $topRect  = New-Object System.Drawing.Rectangle 0, 0, 164, 100
    $gradient = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $topRect, $terra, $brown,
        [System.Drawing.Drawing2D.LinearGradientMode]::Vertical)
    $g.FillRectangle($gradient, $topRect)
    $gradient.Dispose()

    # Textos
    $fBig  = New-Object System.Drawing.Font "Segoe UI", 15, ([System.Drawing.FontStyle]::Bold)
    $fMed  = New-Object System.Drawing.Font "Segoe UI", 10
    $fSm   = New-Object System.Drawing.Font "Segoe UI", 8
    $sf    = New-Object System.Drawing.StringFormat
    $sf.Alignment = [System.Drawing.StringAlignment]::Center

    $g.DrawString("Recetas",     $fBig, $white, 82, 28, $sf)
    $g.DrawString("Familiares",  $fBig, $white, 82, 52, $sf)
    $g.DrawString("v$AppVersion", $fMed, $lgray, 82, 180, $sf)
    $g.DrawString("Tu recetario", $fSm, $lgray, 82, 260, $sf)
    $g.DrawString("familiar",     $fSm, $lgray, 82, 276, $sf)
    $g.DrawString("Gipsybuho",    $fSm, $lgray, 82, 296, $sf)

    foreach ($obj in @($fBig,$fMed,$fSm,$sf,$g)) { $obj.Dispose() }
    $bmp.Save("$InstallerDir\nsis-welcome.bmp", [System.Drawing.Imaging.ImageFormat]::Bmp)
    $bmp.Dispose()

    # ── Header 150x57 ─────────────────────────────────────────
    $hdr = New-Object System.Drawing.Bitmap 150, 57
    $g   = [System.Drawing.Graphics]::FromImage($hdr)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $topR = New-Object System.Drawing.Rectangle 0, 0, 150, 57
    $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $topR, $terra, $brown,
        [System.Drawing.Drawing2D.LinearGradientMode]::Horizontal)
    $g.FillRectangle($grad, $topR)
    $grad.Dispose()

    $fH  = New-Object System.Drawing.Font "Segoe UI", 9, ([System.Drawing.FontStyle]::Bold)
    $sfH = New-Object System.Drawing.StringFormat
    $sfH.Alignment     = [System.Drawing.StringAlignment]::Center
    $sfH.LineAlignment = [System.Drawing.StringAlignment]::Center
    $topRf = New-Object System.Drawing.RectangleF 0, 0, 150, 57
    $g.DrawString("Recetas Familiares", $fH, $white, $topRf, $sfH)

    foreach ($obj in @($fH,$sfH,$g)) { $obj.Dispose() }
    $hdr.Save("$InstallerDir\nsis-header.bmp", [System.Drawing.Imaging.ImageFormat]::Bmp)
    $hdr.Dispose()
}

# Genera el BMP pequeno 55x58 requerido por Inno Setup (WizardSmallImageFile)
function New-InnoSmallImage {
    param([string]$InstallerDir)
    Add-Type -AssemblyName System.Drawing
    $brown = [System.Drawing.Color]::FromArgb(61, 43, 31)   # #3D2B1F
    $terra = [System.Drawing.Color]::FromArgb(193, 125, 82) # #C17D52
    $white = [System.Drawing.Brushes]::White

    $bmp = New-Object System.Drawing.Bitmap 55, 58
    $g   = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $rect = New-Object System.Drawing.Rectangle 0, 0, 55, 58
    $gradient = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $rect, $terra, $brown, [System.Drawing.Drawing2D.LinearGradientMode]::ForwardDiagonal)
    $g.FillRectangle($gradient, $rect)
    $gradient.Dispose()

    $f  = New-Object System.Drawing.Font "Segoe UI", 16, ([System.Drawing.FontStyle]::Bold)
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment     = [System.Drawing.StringAlignment]::Center
    $sf.LineAlignment = [System.Drawing.StringAlignment]::Center
    $g.DrawString("RF", $f, $white, (New-Object System.Drawing.RectangleF 0, 0, 55, 58), $sf)

    foreach ($obj in @($f,$sf,$g)) { $obj.Dispose() }
    $bmp.Save("$InstallerDir\recetas-small.bmp", [System.Drawing.Imaging.ImageFormat]::Bmp)
    $bmp.Dispose()
}

# ── INICIO ─────────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "  +--------------------------------------------------+" -ForegroundColor Magenta
Write-Host "  |   Recetas Familiares $AppVersion — Build Windows     |" -ForegroundColor Magenta
Write-Host "  +--------------------------------------------------+" -ForegroundColor Magenta

# ── PASO 1: Validar herramientas ───────────────────────────────────────────────
Write-Step "1/7  Verificando herramientas..."

$JDK = Find-PackagingJdk
if (-not $JDK) {
    Write-Fail "JDK con jpackage no encontrado."
    Write-Host "     Instala JDK 21 LTS desde https://adoptium.net/ o ejecuta:" -ForegroundColor Yellow
    Write-Host "     .\build-installer.ps1 -JdkPath 'C:\Ruta\Al\jdk-21'" -ForegroundColor Yellow
    exit 1
}
if (-not (Test-Path "$JDK\bin\java.exe")) {
    Write-Fail "JDK no encontrado: $JDK"
    Write-Host "     Instala JDK 21 LTS desde https://adoptium.net/" -ForegroundColor Yellow
    exit 1
}
if (-not (Test-Path "$JDK\bin\jpackage.exe")) {
    Write-Fail "jpackage no encontrado en $JDK\bin\ (requiere JDK 14+)"
    exit 1
}
$javaVersion = & "$JDK\bin\java.exe" -version 2>&1 | Select-Object -First 1
Write-OK "JDK: $javaVersion"
$jdkMajor = Get-JdkMajorVersion $JDK
if ($jdkMajor -ne 21) {
    if ($AllowNonLtsJdk) {
        Write-Warn "JDK 21 LTS no encontrado. Se usara $JDK para empaquetar (permitido por -AllowNonLtsJdk)."
    } else {
        Write-Fail "El runtime empaquetado debe ser JDK 21 LTS y se encontro JDK $jdkMajor ($JDK)."
        Write-Host "     Usa -JdkPath 'C:\Ruta\Al\jdk-21' o -AllowNonLtsJdk para forzar." -ForegroundColor Yellow
        exit 1
    }
}

$MVN = Find-Maven
if (-not $MVN) {
    Write-Fail "Maven no encontrado. Verifica el PATH o las rutas en este script."
    exit 1
}
Write-OK "Maven: $MVN"

$hasNsis = Test-Path $NSIS
$hasInno = [bool]$InnoSetup -and (Test-Path $InnoSetup)
if ($hasInno) {
    Write-OK "Inno Setup 6: $InnoSetup"
} elseif ($hasNsis) {
    Write-OK "NSIS: $NSIS"
} else {
    Write-Warn "Ni Inno Setup ni NSIS encontrados."
    Write-Warn "Se creara solo el app-image (sin .exe instalador)."
    Write-Warn "Inno Setup: https://jrsoftware.org/isdl.php"
}

# ── PASO 2: Preparar directorios (solo los que Maven no borrará) ───────────────
Write-Step "2/7  Preparando directorios..."

@("$ProjectDir\installer", $OutputDir) | ForEach-Object {
    if (-not (Test-Path $_)) {
        New-Item -ItemType Directory -Path $_ -Force | Out-Null
    }
}
Write-OK "Directorios listos"

# ── PASO 3: Crear icono .ico ───────────────────────────────────────────────────
Write-Step "3/7  Preparando icono de aplicacion..."

if (Test-Path $IcoPath) {
    Write-OK "ICO ya existe: $IcoPath"
} elseif (Test-Path $PngPath) {
    $ok = New-IcoFromPng -Png $PngPath -Ico $IcoPath
    if ($ok) {
        Write-OK "ICO generado: $IcoPath"
    } else {
        Write-Warn "No se pudo crear el ICO. El instalador usara icono por defecto."
        $IcoPath = $null
    }
} else {
    Write-Warn "Logo PNG no encontrado en $PngPath — icono por defecto."
    $IcoPath = $null
}

# ── PASO 4: Maven package ──────────────────────────────────────────────────────
Write-Step "4/7  Compilando con Maven (mvn package -Ppackage-windows)..."

Push-Location $ProjectDir
try {
    & $MVN clean package -Ppackage-windows -DskipTests --no-transfer-progress
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "Maven fallo con codigo $LASTEXITCODE"
        exit 1
    }
} finally {
    Pop-Location
}

if (-not (Test-Path $ShadedJar)) {
    Write-Fail "JAR no encontrado tras compilacion: $ShadedJar"
    exit 1
}
$jarMB = '{0:N1}' -f ((Get-Item $ShadedJar).Length / 1MB)
Write-OK "Maven BUILD SUCCESS — JAR: $jarMB MB"

# ── PASO 5: Organizar input para jpackage ──────────────────────────────────────
Write-Step "5/7  Organizando archivos para jpackage..."

# Crear directorios DESPUÉS de mvn clean (que borra target/ completo)
@($InputDir, $ModsDir) | ForEach-Object {
    if (-not (Test-Path $_)) { New-Item -ItemType Directory -Path $_ -Force | Out-Null }
}

# JAR principal (fat JAR con todas las dependencias)
Copy-Item $ShadedJar "$InputDir\$MainJar"
Write-OK "Main JAR copiado: $MainJar"

# JavaFX -win JARs (modulos nativos con DLLs de Windows) → mods/
$winJars = Get-ChildItem "$TargetDir\lib\*-win.jar" -ErrorAction SilentlyContinue
if (-not $winJars -or $winJars.Count -eq 0) {
    Write-Fail "No se encontraron JavaFX *-win.jar en target/lib/"
    Write-Fail "Asegurate de ejecutar con el perfil -Ppackage-windows"
    exit 1
}
foreach ($jar in $winJars) {
    Copy-Item $jar.FullName "$ModsDir\"
}
Write-OK "Modulos JavaFX nativos: $($winJars.Count) JARs copiados a mods/"

# ── PASO 6: jpackage — app-image ──────────────────────────────────────────────
Write-Step "6/7  Ejecutando jpackage..."

$AppImageDir = "$OutputDir\$AppName"
if (Test-Path $AppImageDir) {
    Remove-Item $AppImageDir -Recurse -Force
}

$jpackageArgs = [System.Collections.Generic.List[string]]@(
    "--type",        "app-image",
    "--name",        $AppName,
    "--app-version", $AppVersion,
    "--vendor",      "Gipsybuho",
    "--description", "Recetas Familiares - Gestion familiar de recetas y menus",
    "--input",       $InputDir,
    "--main-jar",    $MainJar,
    "--main-class",  $MainClass,
    "--dest",        $OutputDir,
    "--java-options", "-Djavax.net.ssl.trustStoreType=Windows-ROOT",
    "--java-options", "-Djavax.net.ssl.trustStore=NUL",
    "--java-options", "-Dapi.base.url=$ApiUrl",
    "--java-options", '--module-path=$APPDIR/mods',
    "--java-options", "--add-modules=$JavaFxModules",
    "--java-options", "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.net=ALL-UNNAMED",
    "--java-options", "-Xmx512m"
)

if ($IcoPath -and (Test-Path $IcoPath)) {
    $jpackageArgs.Add("--icon")
    $jpackageArgs.Add($IcoPath)
}

& "$JDK\bin\jpackage.exe" @jpackageArgs
if ($LASTEXITCODE -ne 0) {
    Write-Fail "jpackage fallo con codigo $LASTEXITCODE"
    exit 1
}

if (-not (Test-Path "$AppImageDir\$AppName.exe")) {
    Write-Fail "App-image no encontrado en $AppImageDir tras jpackage"
    exit 1
}
$imageMB = '{0:N0}' -f ((Get-ChildItem $AppImageDir -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB)
Write-OK "App-image creado: $AppImageDir ($imageMB MB)"

# ── PASO 7: Crear instalador .exe (Inno Setup → NSIS → aviso) ────────────────
Write-Step "7/7  Creando instalador .exe..."

$exePath = "$OutputDir\RecetasFamiliares-Instalador-v$AppVersion.exe"

if ($hasInno) {
    # Generar graficas BMP del wizard (reutiliza el welcome de NSIS + una pequena dedicada)
    Write-OK "Generando graficas del instalador..."
    New-NsisBitmaps -InstallerDir "$ProjectDir\installer"
    New-InnoSmallImage -InstallerDir "$ProjectDir\installer"

    & $InnoSetup "/DMyAppVersion=$AppVersion" "$ProjectDir\installer.iss"
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "Inno Setup fallo con codigo $LASTEXITCODE"
        exit 1
    }
    if (Test-Path $exePath) {
        $exeMB = '{0:N1}' -f ((Get-Item $exePath).Length / 1MB)
        Write-OK "Instalador .exe: $exePath ($exeMB MB)"
    }
} elseif ($hasNsis) {
    # Generar graficas BMP para NSIS MUI2
    Write-OK "Generando graficas del instalador..."
    New-NsisBitmaps -InstallerDir "$ProjectDir\installer"

    # Compilar con NSIS (la ruta del .nsi debe ser relativa a donde esta el .nsi)
    Push-Location $ProjectDir
    try {
        & $NSIS "/DAPP_VERSION=$AppVersion" "$ProjectDir\installer.nsi"
        if ($LASTEXITCODE -ne 0) {
            Write-Fail "NSIS fallo con codigo $LASTEXITCODE"
            exit 1
        }
    } finally {
        Pop-Location
    }

    if (Test-Path $exePath) {
        $exeMB = '{0:N1}' -f ((Get-Item $exePath).Length / 1MB)
        Write-OK "Instalador .exe: $exePath ($exeMB MB)"
    }
} else {
    Write-Warn "Sin herramienta de instalador. Instala Inno Setup desde https://jrsoftware.org/isdl.php"
    Write-Warn "App-image portable disponible en: $AppImageDir"
}

# ── RESULTADO ─────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "  +--------------------------------------------------+" -ForegroundColor Green
Write-Host "  |   BUILD COMPLETADO - $AppDisplayName v$AppVersion        |" -ForegroundColor Green
Write-Host "  +--------------------------------------------------+" -ForegroundColor Green
Write-Host ""

if (Test-Path "$OutputDir\RecetasFamiliares-Instalador-v$AppVersion.exe") {
    Write-Host "  Instalador:  $OutputDir\RecetasFamiliares-Instalador-v$AppVersion.exe" -ForegroundColor White
}
Write-Host "  App-image:   $AppImageDir\$AppName.exe" -ForegroundColor White
Write-Host "  API URL:     $ApiUrl (configurable con -ApiUrl)" -ForegroundColor Gray
Write-Host ""
Write-Host "  Para cambiar la URL del backend al empaquetar:" -ForegroundColor Gray
Write-Host "    .\build-installer.ps1 -ApiUrl 'http://192.168.1.100:8080/'" -ForegroundColor Gray
Write-Host ""
