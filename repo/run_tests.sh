#!/usr/bin/env bash
set -euo pipefail

JVM_ONLY=false
for arg in "$@"; do
    case "$arg" in
        --jvm-only) JVM_ONLY=true ;;
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

    if [ "$JVM_ONLY" = true ]; then
        INSTRUMENTED_STATUS="SKIPPED (--jvm-only)"
    else
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
            echo "ERROR: No device/emulator detected."
            echo "  Connect a device or start an emulator and retry, or use"
            echo "  --jvm-only to run only Docker-contained JVM tests and lint."
            INSTRUMENTED_STATUS="FAILED (no device)"
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
    EXIT=0
    if [ "$JVM_STATUS" = "FAILED" ] || [ "$LINT_STATUS" = "FAILED" ]; then
        EXIT=1
    fi
    if [ "$INSTRUMENTED_STATUS" = "FAILED" ] || [ "$INSTRUMENTED_STATUS" = "FAILED (no device)" ]; then
        EXIT=1
    fi
    exit $EXIT
fi

# Inside Docker: run JVM tests + lint only

# Fix sdk.dir — the host's local.properties is bind-mounted and points to a
# macOS path that doesn't exist in the container.  Overwrite with ANDROID_HOME.
if [ -n "${ANDROID_HOME:-}" ]; then
    echo "sdk.dir=${ANDROID_HOME}" > local.properties
fi

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
