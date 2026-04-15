#!/usr/bin/env bash
set -euo pipefail

FULL_MODE=false
for arg in "$@"; do
    case "$arg" in
        --full) FULL_MODE=true ;;
    esac
done

echo "============================================"
echo "  RoadRunner Dispatch — Test Suite"
echo "============================================"

JVM_STATUS="NOT RUN"
LINT_STATUS="NOT RUN"
INSTRUMENTED_STATUS="NOT RUN"

# If running on the host, delegate to Docker for JVM tests,
# then run instrumented tests locally if a device/emulator is available.
if [ ! -f /.dockerenv ]; then
    echo ""
    echo "--- JVM Tests + Lint (via Docker) ---"
    if docker compose run --rm --build test-runner; then
        JVM_STATUS="PASSED"
        LINT_STATUS="PASSED"
    else
        JVM_STATUS="FAILED"
        LINT_STATUS="FAILED"
    fi

    echo ""
    echo "--- Instrumented Tests (requires device/emulator) ---"
    if adb devices 2>/dev/null | grep -q "device$"; then
        echo "Device detected — running connectedDebugAndroidTest..."
        if ./gradlew connectedDebugAndroidTest --no-daemon --stacktrace; then
            INSTRUMENTED_STATUS="PASSED"
        else
            INSTRUMENTED_STATUS="FAILED"
        fi
    else
        if [ "$FULL_MODE" = true ]; then
            echo "ERROR: --full mode requires a connected device or emulator."
            echo "  Connect a device or start an emulator and retry."
            INSTRUMENTED_STATUS="FAILED (no device)"
        else
            echo "SKIPPED: No device/emulator detected."
            echo "  Connect a device or start an emulator, then run:"
            echo "    ./gradlew connectedDebugAndroidTest"
            INSTRUMENTED_STATUS="SKIPPED (no device)"
        fi
    fi

    echo ""
    echo "============================================"
    echo "  TEST SUMMARY"
    echo "============================================"
    echo "  JVM unit tests:       $JVM_STATUS"
    echo "  Lint:                 $LINT_STATUS"
    echo "  Instrumented tests:   $INSTRUMENTED_STATUS"
    echo "============================================"

    # Determine exit code
    if [ "$JVM_STATUS" = "FAILED" ] || [ "$LINT_STATUS" = "FAILED" ] || [ "$INSTRUMENTED_STATUS" = "FAILED (no device)" ] || [ "$INSTRUMENTED_STATUS" = "FAILED" ]; then
        exit 1
    fi
    exit 0
fi

# Inside Docker: run JVM tests + lint only
echo ""
echo "--- Unit Tests (JVM) ---"
./gradlew test --no-daemon --stacktrace

echo ""
echo "--- Lint Checks ---"
./gradlew lint --no-daemon

echo ""
echo "============================================"
echo "  JVM TESTS + LINT PASSED"
echo "============================================"
exit 0
