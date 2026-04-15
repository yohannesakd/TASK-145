#!/usr/bin/env bash
set -euo pipefail

# Auto-generates test inventory counts from source.
# Referenced from README.md — run this to verify or update test counts.

JVM_DIR="app/src/test"
ANDROID_DIR="app/src/androidTest"

jvm_files=$(find "$JVM_DIR" -name '*.java' 2>/dev/null | wc -l | tr -d ' ')
jvm_cases=$(grep -r '@Test' "$JVM_DIR" --include='*.java' 2>/dev/null | wc -l | tr -d ' ')

android_files=$(find "$ANDROID_DIR" -name '*.java' 2>/dev/null | wc -l | tr -d ' ')
android_cases=$(grep -r '@Test' "$ANDROID_DIR" --include='*.java' 2>/dev/null | wc -l | tr -d ' ')

total_files=$((jvm_files + android_files))
total_cases=$((jvm_cases + android_cases))

echo "============================================"
echo "  RoadRunner Dispatch — Test Inventory"
echo "============================================"
echo ""
echo "  JVM unit tests:        $jvm_files files, $jvm_cases test cases"
echo "  Instrumented tests:    $android_files files, $android_cases test cases"
echo "  ─────────────────────────────────────────"
echo "  Total:                 $total_files files, $total_cases test cases"
echo ""
echo "============================================"
