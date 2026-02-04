#!/bin/bash
set -e # Exit immediately if a command exits with a non-zero status

# ==========================================
# J-Focus Installation Script for macOS/Linux
# ==========================================

# 1. Define Variables
REPO="jher235/j-focus"
VERSION="1.0.0" # Update this version when releasing a new one
JAR_NAME="j-focus-${VERSION}-all.jar"
INSTALL_DIR="$HOME/.jfocus"
DOWNLOAD_URL="https://github.com/$REPO/releases/download/v$VERSION/$JAR_NAME"

echo "🚀 Starting J-Focus installation..."

# 2. Create Installation Directory
# Use -p to avoid error if directory already exists
if [ ! -d "$INSTALL_DIR" ]; then
    echo "📂 Creating installation directory at $INSTALL_DIR..."
    mkdir -p "$INSTALL_DIR"
fi

# 3. Download the JAR file
echo "⬇️  Downloading $JAR_NAME from GitHub..."

if command -v curl >/dev/null 2>&1; then
    curl -L "$DOWNLOAD_URL" -o "$INSTALL_DIR/j-focus.jar" --progress-bar
elif command -v wget >/dev/null 2>&1; then
    wget -O "$INSTALL_DIR/j-focus.jar" "$DOWNLOAD_URL" -q --show-progress
else
    echo "❌ Error: Neither 'curl' nor 'wget' was found. Please install one of them."
    exit 1
fi

# 4. Verify Download
if [ ! -f "$INSTALL_DIR/j-focus.jar" ]; then
    echo "❌ Error: Download failed."
    exit 1
fi

# 5. Set Permissions (Make it executable just in case)
chmod +x "$INSTALL_DIR/j-focus.jar"

# 6. Print Post-Installation Instructions
echo ""
echo "✅ Installation completed successfully!"
echo "📍 Location: $INSTALL_DIR/j-focus.jar"
echo ""
echo "============================================================"
echo " 💡 To use the 'jfocus' command globally, add this alias:"
echo "============================================================"
echo ""
echo "  [Bash Users]"
echo "  echo \"alias jfocus='java -jar $INSTALL_DIR/j-focus.jar'\" >> ~/.bashrc && source ~/.bashrc"
echo ""
echo "  [Zsh Users (macOS Default)]"
echo "  echo \"alias jfocus='java -jar $INSTALL_DIR/j-focus.jar'\" >> ~/.zshrc && source ~/.zshrc"
echo ""