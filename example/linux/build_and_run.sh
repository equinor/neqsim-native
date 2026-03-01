#!/usr/bin/env bash
# -------------------------------------------------------------------
# build_and_run.sh — Build the native library and compile+run examples
#
# Usage (from any directory inside the container / Codespace):
#   ./example/linux/build_and_run.sh              # default build
#   ./example/linux/build_and_run.sh --with-python # include Python models
#
# Prerequisites:
#   - GraalVM 25+ with native-image
#   - g++ (provided by build-essential in the devcontainer)
# -------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
JAVA_GRAAL="$REPO_ROOT/java_graal"
TARGET="$JAVA_GRAAL/target"

# --- Parse arguments ---
PROFILE="native-linux-lean"
if [[ "${1:-}" == "--with-python" ]]; then
    PROFILE="native-linux,with-python"
    echo "==> Building WITH Python support (profile: $PROFILE)"
else
    echo "==> Building default (no Python) (profile: $PROFILE)"
fi

# --- Step 1: Build the native shared library ---
echo ""
echo "=== Step 1/3: Building neqsim.so ==="
cd "$JAVA_GRAAL"
./mvnw -P"$PROFILE" package -ntp

if [[ ! -f "$TARGET/neqsim.so" ]]; then
    echo "ERROR: neqsim.so not found in $TARGET" >&2
    exit 1
fi
echo "==> neqsim.so built successfully: $TARGET/neqsim.so"

# --- Step 2: Compile examples ---
echo ""
echo "=== Step 2/3: Compiling C++ examples ==="
cd "$SCRIPT_DIR"

echo "  Compiling water_dew_point..."
g++ -o water_dew_point water_dew_point.cpp \
    -I"$TARGET" -L"$TARGET" -Wl,-rpath,"$TARGET" \
    -l:neqsim.so -ldl -lpthread

echo "  Compiling py_dewpoint_calc..."
g++ -o py_dewpoint_calc py_dewpoint_calc.cpp \
    -I"$TARGET" -L"$TARGET" -Wl,-rpath,"$TARGET" \
    -l:neqsim.so -ldl -lpthread

echo "==> Examples compiled successfully."

# --- Step 3: Run examples ---
echo ""
echo "=== Step 3/3: Running examples ==="

echo ""
echo "--- water_dew_point ---"
./water_dew_point

echo ""
echo "--- py_dewpoint_calc ---"
./py_dewpoint_calc

echo ""
echo "=== All examples completed ==="
