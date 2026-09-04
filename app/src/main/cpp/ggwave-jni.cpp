#include <jni.h>
#include <android/log.h>
#include <cstring>
#include <ggwave/ggwave.h>

namespace {
    JavaVM* g_jvm = nullptr;
    jobject g_receiverObject = nullptr;
    ggwave_Instance g_ggwave = 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_poorgrammera_bydsubai_audio_GGWaveReceiver_initNative(JNIEnv * env, jobject obj) {
    __android_log_print(ANDROID_LOG_DEBUG, "ggwave (native)", "Initializing native module");

    // Clean up previous instance if any
    if (g_ggwave != 0) {
        ggwave_free(g_ggwave);
        g_ggwave = 0;
    }
    if (g_receiverObject != nullptr) {
        env->DeleteGlobalRef(g_receiverObject);
        g_receiverObject = nullptr;
    }

    ggwave_Parameters parameters = ggwave_getDefaultParameters();
    parameters.sampleFormatInp = GGWAVE_SAMPLE_FORMAT_I16;
    parameters.sampleFormatOut = GGWAVE_SAMPLE_FORMAT_I16;
    parameters.sampleRateInp = 48000;
    parameters.operatingMode = GGWAVE_OPERATING_MODE_RX; // RX-only to optimize memory
    g_ggwave = ggwave_init(parameters);

    env->GetJavaVM(&g_jvm);
    g_receiverObject = env->NewGlobalRef(obj);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_poorgrammera_bydsubai_audio_GGWaveReceiver_cleanupNative(JNIEnv * env, jobject obj) {
    __android_log_print(ANDROID_LOG_DEBUG, "ggwave (native)", "Cleaning up native module");

    if (g_ggwave != 0) {
        ggwave_free(g_ggwave);
        g_ggwave = 0;
    }
    if (g_receiverObject != nullptr) {
        env->DeleteGlobalRef(g_receiverObject);
        g_receiverObject = nullptr;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_poorgrammera_bydsubai_audio_GGWaveReceiver_processCaptureData(JNIEnv *env, jobject thiz, jshortArray data) {
    if (g_ggwave == 0) return;

    jsize dataSize = env->GetArrayLength(data);
    if (dataSize <= 0) return;

    jboolean isCopy = false;
    jshort * cData = env->GetShortArrayElements(data, &isCopy);

    char output[1024];
    int ret = ggwave_decode(g_ggwave, (char *) cData, 2 * dataSize, output);

    env->ReleaseShortArrayElements(data, cData, JNI_ABORT);

    if (ret > 0) {
        output[ret] = '\0';
        __android_log_print(ANDROID_LOG_DEBUG, "ggwave (native)", "Received message: '%s'", output);

        if (g_receiverObject != nullptr) {
            jclass handlerClass = env->GetObjectClass(g_receiverObject);
            jmethodID mid_onReceivedMessage = env->GetMethodID(handlerClass, "onNativeReceivedMessage", "([B)V");
            if (mid_onReceivedMessage != nullptr) {
                jbyteArray jba_message = env->NewByteArray(ret);
                env->SetByteArrayRegion(jba_message, 0, ret, (jbyte*) output);
                env->CallVoidMethod(g_receiverObject, mid_onReceivedMessage, jba_message);
                env->DeleteLocalRef(jba_message);
            }
        }
    }
}