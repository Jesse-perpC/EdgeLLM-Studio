#!/bin/bash
set -e

echo "=== EdgeLLM Studio: llama.cpp Native Android Setup ==="

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LLAMA_DIR="$PROJECT_ROOT/llama.cpp"
CPP_DIR="$PROJECT_ROOT/app/src/main/cpp"

echo "Project Root: $PROJECT_ROOT"
echo "Llama Directory: $LLAMA_DIR"
echo "JNI CPP Directory: $CPP_DIR"

if [ ! -d "$LLAMA_DIR" ]; then
    echo "Cloning official ggerganov/llama.cpp..."
    git clone --depth 1 https://github.com/ggerganov/llama.cpp.git "$LLAMA_DIR"
else
    echo "llama.cpp repository already present at $LLAMA_DIR"
fi

mkdir -p "$CPP_DIR"

echo "Checking native JNI bridge files..."
if [ -f "$CPP_DIR/llama-android.cpp" ] && [ -f "$CPP_DIR/logging.h" ] && [ -f "$CPP_DIR/CMakeLists.txt" ]; then
    echo "llama-android JNI C++ files verified."
else
    echo "Warning: JNI files missing in $CPP_DIR"
fi

echo "=== llama.cpp Native Android Setup Complete ==="
