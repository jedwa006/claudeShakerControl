#!/bin/bash
# Visual Regression Screenshot Testing Script
# Captures screenshots of key screens in different states for visual comparison.
#
# Usage:
#   ./scripts/screenshot-test.sh capture    # Capture new screenshots
#   ./scripts/screenshot-test.sh compare    # Compare with reference images (requires ImageMagick)
#
# Prerequisites:
#   - Device connected via USB with USB debugging enabled
#   - App installed (./gradlew installDebug)
#   - ImageMagick installed for comparison (brew install imagemagick)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
REFERENCE_DIR="$PROJECT_DIR/screenshots/reference"
CURRENT_DIR="$PROJECT_DIR/screenshots/current"
DIFF_DIR="$PROJECT_DIR/screenshots/diff"
APP_PACKAGE="com.shakercontrol.app.debug"

# Ensure adb is available
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"

# Create directories
mkdir -p "$REFERENCE_DIR" "$CURRENT_DIR" "$DIFF_DIR"

# Helper function to capture screenshot
capture_screen() {
    local name="$1"
    local output_dir="$2"
    sleep 1
    adb exec-out screencap -p > "$output_dir/${name}.png"
    echo "Captured: $name"
}

# Helper function to navigate via deep link
navigate() {
    local route="$1"
    adb shell am start -a android.intent.action.VIEW -d "shaker://$route" "$APP_PACKAGE" > /dev/null 2>&1 || true
    sleep 2
}

# Helper function to enable service mode
enable_service_mode() {
    navigate "action/service-mode/enable"
    sleep 1
}

# Helper function to disable service mode
disable_service_mode() {
    navigate "action/service-mode/disable"
    sleep 1
}

# Capture all screenshots
capture_all() {
    local output_dir="${1:-$CURRENT_DIR}"
    echo "Capturing screenshots to: $output_dir"

    # Force stop and start fresh
    adb shell am force-stop "$APP_PACKAGE"
    sleep 1

    # Wake device
    adb shell input keyevent KEYCODE_WAKEUP
    sleep 1

    # === HOME SCREEN ===
    navigate "home"
    capture_screen "home_disconnected" "$output_dir"

    # === RUN SCREEN (Normal Mode) ===
    navigate "run"
    capture_screen "run_normal_disconnected" "$output_dir"

    # === RUN SCREEN (Service Mode) ===
    enable_service_mode
    navigate "run"
    capture_screen "run_service_mode" "$output_dir"

    # === DEVICES SCREEN ===
    navigate "devices"
    capture_screen "devices" "$output_dir"

    # === SETTINGS SCREEN ===
    navigate "settings"
    capture_screen "settings" "$output_dir"

    # === I/O SCREEN ===
    navigate "io"
    capture_screen "io_service_mode" "$output_dir"

    # === DIAGNOSTICS SCREEN ===
    navigate "diagnostics"
    capture_screen "diagnostics" "$output_dir"

    # === ALARMS SCREEN ===
    navigate "alarms"
    capture_screen "alarms" "$output_dir"

    # Disable service mode for clean state
    disable_service_mode

    echo "Done! Screenshots saved to: $output_dir"
}

# Compare current screenshots with reference images
compare_all() {
    echo "Comparing screenshots..."

    local has_diff=false

    for ref_file in "$REFERENCE_DIR"/*.png; do
        if [ -f "$ref_file" ]; then
            local filename=$(basename "$ref_file")
            local current_file="$CURRENT_DIR/$filename"
            local diff_file="$DIFF_DIR/$filename"

            if [ -f "$current_file" ]; then
                # Use ImageMagick to compare images
                # Extract just the pixel count (first number before any parentheses)
                local result=$(compare -metric AE "$ref_file" "$current_file" "$diff_file" 2>&1)
                local pixel_count=$(echo "$result" | sed 's/ .*//' | sed 's/\..*//')
                if [ "$pixel_count" != "0" ]; then
                    echo "DIFF: $filename (${pixel_count} pixels different)"
                    has_diff=true
                else
                    echo "OK: $filename"
                    rm -f "$diff_file"  # Remove diff file if identical
                fi
            else
                echo "MISSING: $filename (no current screenshot)"
                has_diff=true
            fi
        fi
    done

    if [ "$has_diff" = true ]; then
        echo ""
        echo "Visual differences detected! Check $DIFF_DIR for diff images."
        exit 1
    else
        echo ""
        echo "All screenshots match reference images."
    fi
}

# Update reference images from current
update_reference() {
    echo "Updating reference images..."
    cp "$CURRENT_DIR"/*.png "$REFERENCE_DIR/" 2>/dev/null || true
    echo "Reference images updated from current screenshots."
}

# Main
case "${1:-capture}" in
    capture)
        capture_all "$CURRENT_DIR"
        ;;
    reference)
        capture_all "$REFERENCE_DIR"
        ;;
    compare)
        compare_all
        ;;
    update)
        update_reference
        ;;
    *)
        echo "Usage: $0 {capture|reference|compare|update}"
        echo ""
        echo "Commands:"
        echo "  capture   - Capture screenshots to screenshots/current/"
        echo "  reference - Capture screenshots to screenshots/reference/ (baseline)"
        echo "  compare   - Compare current screenshots with reference images"
        echo "  update    - Copy current screenshots to reference"
        exit 1
        ;;
esac
