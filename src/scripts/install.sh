#!/bin/bash
set -e # Exit immediately if a command exits with a non-zero status

# ==========================================
# J-Focus Installation Script for macOS/Linux
# ==========================================

# 1. Define Variables
REPO="jher235/j-focus"
VERSION="1.0.0" # Release version
JAR_NAME="j-focus-${VERSION}-all.jar"
INSTALL_DIR="$HOME/.jfocus"
# Checksum for security (SHA256) - Paste the hash here!
EXPECTED_SHA256="5bddf71bbcc693fec4be8d4085475b68df93c42926002cb973e591ffb229340d"

DOWNLOAD_URL="https://github.com/$REPO/releases/download/v$VERSION/$JAR_NAME"

echo "🚀 Starting J-Focus installation..."

# 2. Create Installation Directory
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
    echo "❌ Error: Neither 'curl' nor 'wget' was found."
    exit 1
fi

# 4. Verify Checksum (Security Step)
echo "🔒 Verifying file integrity..."

if command -v sha256sum >/dev/null 2>&1; then
    # Linux
    ACTUAL_SHA256=$(sha256sum "$INSTALL_DIR/j-focus.jar" | awk '{print $1}')
elif command -v shasum >/dev/null 2>&1; then
    # macOS
    ACTUAL_SHA256=$(shasum -a 256 "$INSTALL_DIR/j-focus.jar" | awk '{print $1}')
else
    echo "⚠️  Warning: SHA256 tools not found. Skipping verification."
    ACTUAL_SHA256=""
fi

# Compare hashes (Case-insensitive comparison)
if [ -n "$ACTUAL_SHA256" ]; then
    if [ "${ACTUAL_SHA256,,}" != "${EXPECTED_SHA256,,}" ]; then
        echo "❌ Error: Security check failed! File hash does not match."
        echo "Expected: $EXPECTED_SHA256"
        echo "Actual:   $ACTUAL_SHA256"
        rm -f "$INSTALL_DIR/j-focus.jar"
        exit 1
    fi
    echo "✅ Security check passed (SHA256 verified)."
fi

# 5. Set Permissions
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