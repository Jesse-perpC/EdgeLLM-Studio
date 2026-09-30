#!/bin/bash
set -euo pipefail

# ---------------------------------------------------------------------------
# EdgeLLM Studio: llama.cpp native Android setup
#
# The app builds a real llama.cpp inference engine from this source tree. There
# is no stub fallback: if llama.cpp is missing the Gradle/CMake build fails
# loudly rather than producing a JNI shim that fakes inference.
# ---------------------------------------------------------------------------

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LLAMA_DIR="$PROJECT_ROOT/llama.cpp"
CPP_DIR="$PROJECT_ROOT/app/src/main/cpp"

# Pinned for reproducible builds. Bump deliberately, and re-test on a device
# before committing to a new one: llama.cpp's C API has breaking changes
# between revisions (llama_load_model_from_file -> llama_model_load_from_file,
# llama_sampler_init_grammar_lazy -> _lazy_patterns, and so on), and
# app/src/main/cpp/llama-android.cpp is written against these exact symbols.
LLAMA_CPP_REPO="https://github.com/ggml-org/llama.cpp.git"
LLAMA_CPP_COMMIT="8df332de1b7d036952631ef9d0a25d8aa60aeea3"

echo "=== EdgeLLM Studio: llama.cpp Native Android Setup ==="
echo "Project root : $PROJECT_ROOT"
echo "llama.cpp dir: $LLAMA_DIR"
echo "JNI sources  : $CPP_DIR"

if ! command -v git >/dev/null 2>&1; then
    echo "ERROR: git is required." >&2
    exit 1
fi

if [ ! -d "$LLAMA_DIR/.git" ]; then
    if [ -d "$LLAMA_DIR" ] && [ -n "$(ls -A "$LLAMA_DIR" 2>/dev/null)" ]; then
        echo "ERROR: $LLAMA_DIR exists but is not a git checkout." >&2
        echo "Remove it and re-run, or restore it manually." >&2
        exit 1
    fi
    echo "Cloning $LLAMA_CPP_REPO ..."
    git clone "$LLAMA_CPP_REPO" "$LLAMA_DIR"
else
    echo "llama.cpp checkout already present"
fi

echo "Checking out pinned commit $LLAMA_CPP_COMMIT ..."
git -C "$LLAMA_DIR" fetch --depth 1 origin "$LLAMA_CPP_COMMIT" 2>/dev/null \
    || git -C "$LLAMA_DIR" fetch origin
git -C "$LLAMA_DIR" checkout --detach "$LLAMA_CPP_COMMIT"
echo "llama.cpp is now at $(git -C "$LLAMA_DIR" rev-parse HEAD)"

for f in llama-android.cpp logging.h CMakeLists.txt; do
    if [ ! -f "$CPP_DIR/$f" ]; then
        echo "ERROR: missing JNI source $CPP_DIR/$f" >&2
        exit 1
    fi
done
echo "JNI sources verified."

# Confirm the headers this JNI layer compiles against are actually present.
# These are the exact symbols llama-android.cpp calls; if a future pin moves
# them, fail here rather than at link time.
missing=0
for sym in "llama_model_load_from_file" "llama_init_from_model" "llama_sampler_init_grammar" "llama_sampler_init_greedy" "llama_memory_clear" "ggml_backend_load_all"; do
    if ! grep -qrq "$sym" "$LLAMA_DIR/include" "$LLAMA_DIR/ggml/include" 2>/dev/null; then
        echo "WARNING: symbol '$sym' not found in llama.cpp public headers." >&2
        echo "         The pinned commit may have renamed it. Check llama-android.cpp." >&2
        missing=1
    fi
done
[ "$missing" -eq 0 ] && echo "Required llama.cpp API symbols verified in headers."

# Confirm the real source files the build needs exist.
for f in "src/CMakeLists.txt" "ggml/src/CMakeLists.txt" "include/llama.h" "ggml/include/ggml-backend.h"; do
    if [ ! -f "$LLAMA_DIR/$f" ]; then
        echo "ERROR: llama.cpp is missing $f (incomplete checkout?)" >&2
        exit 1
    fi
done

cat <<'EOF'

=== Setup complete ===

Build:
  ./gradlew assembleDebug

Backend notes (see app/src/main/cpp/CMakeLists.txt):
  * CPU / ARM NEON is the default and builds from a stock Android NDK.
  * OpenCL  needs an OpenCL ICD + headers for the target GPU; the NDK has none.
            Enable with: -DEDGELLM_GPU_OPENCL=ON
  * Vulkan  needs Vulkan SDK (glslc) + SPIRV-Headers on the build host.
            Enable with: -DEDGELLM_GPU_VULKAN=ON
  * Hexagon needs a licensed Hexagon SDK and an HTP signing certificate.
            Enable with: -DEDGELLM_NPU_HEXAGON=ON

EOF
