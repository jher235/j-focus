# ==========================================
# J-Focus Installation Script for Windows
# ==========================================

$ErrorActionPreference = 'Stop'

# 1. Define Variables
$Repo = "jher235/j-focus"
$Version = "1.0.0" # Release version
$JarName = "j-focus-$Version-all.jar"
$InstallDir = "$HOME\.jfocus"
# Checksum for security (SHA256) - Paste the hash from Step 1 here!
$ExpectedSha256 = "373a6b1bf44f9c58bd31953368c59ecfd130820b6c8615a887752c329f38f89b"

$DownloadUrl = "https://github.com/$Repo/releases/download/v$Version/$JarName"
$DestPath = "$InstallDir\j-focus.jar"
$BatPath = "$InstallDir\jfocus.bat"

Write-Host "🚀 Starting J-Focus installation..." -ForegroundColor Cyan

# 2. Create Installation Directory
if (!(Test-Path $InstallDir)) {
    Write-Host "📂 Creating installation directory at $InstallDir..."
    New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
}

# 3. Download the JAR file
Write-Host "⬇️  Downloading $JarName from GitHub..."
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $DestPath

    # 4. Verify Checksum (Security Step)
    Write-Host "🔒 Verifying file integrity..."
    $ActualSha256 = (Get-FileHash -Algorithm SHA256 -Path $DestPath).Hash

    if ($ActualSha256 -ne $ExpectedSha256) {
        Write-Host "❌ Error: Security check failed! File hash does not match." -ForegroundColor Red
        Write-Host "Expected: $ExpectedSha256"
        Write-Host "Actual:   $ActualSha256"
        # Delete the suspicious file
        Remove-Item -Path $DestPath -Force
        exit 1
    }
    Write-Host "✅ Security check passed (SHA256 verified)." -ForegroundColor Green
}
catch {
    Write-Host "❌ Error: Failed to download or verify the file. $_" -ForegroundColor Red
    exit 1
}

# 5. Create Wrapper Script (.bat)
Write-Host "⚙️  Generating executable wrapper script..."
$BatContent = "@echo off`njava -jar ""$DestPath"" %*"
Set-Content -Path $BatPath -Value $BatContent

# 6. Auto-configure PATH
Write-Host "⚙️  Configuring PATH..."

$UserPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($UserPath -notlike "*$InstallDir*") {
    [Environment]::SetEnvironmentVariable("Path", "$UserPath;$InstallDir", "User")
    Write-Host "✅ Added $InstallDir to User PATH" -ForegroundColor Green
    Write-Host "🔄 Please restart PowerShell to use the 'jfocus' command." -ForegroundColor Yellow
} else {
    Write-Host "ℹ️  PATH already contains $InstallDir" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "✅ Installation completed! After restart, run: jfocus --version" -ForegroundColor Green