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
EXPECTED_SHA256="64d38039c8731215635e8b501af9963b3721dfeebfac609ff95b9e0bc7c7309f"

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

# 6. Auto-configure Alias
echo "⚙️  Configuring alias..."

RC_FILE=""
# Prioritize $SHELL environment variable for login shell detection
case "$SHELL" in
    */zsh) RC_FILE="$HOME/.zshrc" ;;
    */bash) RC_FILE="$HOME/.bashrc" ;;
esac

# Fallback detection based on shell version if $SHELL is not conclusive
if [ -z "$RC_FILE" ]; then
    if [ -n "$ZSH_VERSION" ]; then
        RC_FILE="$HOME/.zshrc"
    elif [ -n "$BASH_VERSION" ]; then
        RC_FILE="$HOME/.bashrc"
    fi
fi

if [ -n "$RC_FILE" ]; then
    if [ -f "$RC_FILE" ]; then
        # Check permissions
        if [ ! -w "$RC_FILE" ]; then
            echo "⚠️  Warning: No write permission for $RC_FILE. Please add alias manually."
        else
            if grep -q "alias jfocus=" "$RC_FILE"; then
                echo "ℹ️  Alias 'jfocus' already exists in $RC_FILE"
            else
                # CREATE BACKUP
                cp "$RC_FILE" "$RC_FILE.backup-$(date +%Y%m%d)"
                echo "📦 Created backup: $RC_FILE.backup-$(date +%Y%m%d)"

                echo "" >> "$RC_FILE"
                echo "# JFocus Alias" >> "$RC_FILE"
                echo "alias jfocus='java -jar $INSTALL_DIR/j-focus.jar'" >> "$RC_FILE"
                echo "✅ Added alias to $RC_FILE"
                echo "🔄 Please restart your terminal or run: source $RC_FILE"
            fi
        fi
    else
        # File doesn't exist, create it
        echo "# JFocus Alias" > "$RC_FILE"
        echo "alias jfocus='java -jar $INSTALL_DIR/j-focus.jar'" >> "$RC_FILE"
        echo "✅ Created $RC_FILE and added alias"
        echo "🔄 Please restart your terminal or run: source $RC_FILE"
    fi
else
    echo "⚠️  Could not detect shell configuration file. Please add this manually:"
    echo "   alias jfocus='java -jar $INSTALL_DIR/j-focus.jar'"
fi

echo ""
echo "✅ Installation completed! Try running: jfocus --version"