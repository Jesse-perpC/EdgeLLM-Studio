#include <jni.h>
#include <string>
#include <android/log.h>

#define TAG "EdgeLLMNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_perpcorp_edgellm_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "EdgeLLM Studio Native Engine (ARM/Vulkan)";
    return env->NewStringUTF(hello.c_str());
}
