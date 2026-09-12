param(
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Gradle = Join-Path $Root "gradlew.bat"
$InstallerDir = Join-Path $Root "app-ui\build\installer"
$DistributionLib = Join-Path $Root "app-ui\build\install\SisTienda\lib"

Push-Location $Root
try {
    if (-not $SkipTests) {
        & $Gradle clean build --no-daemon
        if ($LASTEXITCODE -ne 0) {
            throw "El build/test de SisTienda falló. No se generará el instalador."
        }
    }

    & $Gradle :app-ui:installDist --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo generar la distribución de SisTienda."
    }

    $Jpackage = if ($env:JAVA_HOME) {
        Join-Path $env:JAVA_HOME "bin\jpackage.exe"
    } else {
        "jpackage.exe"
    }

    if ($env:JAVA_HOME -and -not (Test-Path $Jpackage)) {
        throw "No se encontró jpackage.exe en JAVA_HOME. Instalá/seleccioná JDK 21."
    }

    $MainJar = Get-ChildItem $DistributionLib -Filter "app-ui-*.jar" |
        Where-Object { $_.Name -notmatch "sources|javadoc" } |
        Select-Object -First 1

    if (-not $MainJar) {
        throw "No se encontró el JAR principal de SisTienda en $DistributionLib"
    }

    if (Test-Path $InstallerDir) {
        Remove-Item $InstallerDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $InstallerDir | Out-Null

    & $Jpackage `
        --type exe `
        --name "SisTienda" `
        --app-version "0.9.0" `
        --vendor "SisTienda" `
        --description "Sistema de gestión y punto de venta para comercios" `
        --input $DistributionLib `
        --main-jar $MainJar.Name `
        --main-class "py.sistienda.ui.MainApp" `
        --dest $InstallerDir `
        --java-options "-Dfile.encoding=UTF-8" `
        --win-per-user-install `
        --win-dir-chooser `
        --win-menu `
        --win-menu-group "SisTienda" `
        --win-shortcut

    if ($LASTEXITCODE -ne 0) {
        throw "jpackage no pudo generar el instalador EXE."
    }

    $Installer = Get-ChildItem $InstallerDir -Filter "*.exe" | Select-Object -First 1
    if (-not $Installer) {
        throw "El proceso terminó sin generar un archivo EXE."
    }

    Write-Host ""
    Write-Host "Instalador generado correctamente:" -ForegroundColor Green
    Write-Host $Installer.FullName -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Los datos del cliente NO se guardan dentro del programa instalado." -ForegroundColor Yellow
    Write-Host "En Windows se guardan por defecto en %LOCALAPPDATA%\SisTienda" -ForegroundColor Yellow
}
finally {
    Pop-Location
}
