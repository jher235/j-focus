# ==========================================
# J-Focus Installation Script for Windows
# ==========================================

# Stop execution if any error occurs
$ErrorActionPreference = 'Stop'

# 1. Define Variables
$Repo = "jher235/j-focus"
$Version = "1.0.0" # Update this version when releasing a new one
$JarName = "j-focus-$Version-all.jar"
$InstallDir = "$HOME\.jfocus"
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
    # Using specific TLS version just in case
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $DestPath
}
catch {
    Write-Host "❌ Error: Failed to download the file. $_" -ForegroundColor Red
    exit 1
}

# 4. Create Wrapper Script (.bat)
# This allows users to run 'jfocus' instead of 'java -jar ...'
Write-Host "⚙️  Generating executable wrapper script..."
$BatContent = "@echo off`njava -jar ""$DestPath"" %*"
Set-Content -Path $BatPath -Value $BatContent

# 5. Print Post-Installation Instructions
Write-Host ""
Write-Host "✅ Installation completed successfully!" -ForegroundColor Green
Write-Host "📍 Location: $DestPath"
Write-Host ""
Write-Host "============================================================" -ForegroundColor Yellow
Write-Host " 💡 To use the 'jfocus' command globally, add this path:" -ForegroundColor Yellow
Write-Host "============================================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "  Add the following directory to your User PATH environment variable:"
Write-Host "  $InstallDir"
Write-Host ""
Write-Host "  (You may need to restart your terminal after adding the PATH)"
Write-Host ""