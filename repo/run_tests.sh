#!/usr/bin/env bash
set -euo pipefail

echo "============================================"
echo "  RoadRunner Dispatch — Test Suite"
echo "============================================"

echo ""
echo "--- Unit Tests (JVM) ---"
./gradlew test --no-daemon --stacktrace

echo ""
echo "--- Lint Checks ---"
./gradlew lint --no-daemon

echo ""
echo "============================================"
echo "  ALL TESTS PASSED"
echo "============================================"
exit 0
