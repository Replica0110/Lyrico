#include <jni.h>

#include <chrono>
#include <cstring>
#include <mutex>
#include <string>

extern "C" {
#include "quickjs.h"
}

namespace {

struct QuickJsRuntimeState {
    JSRuntime *runtime = nullptr;
    JSContext *context = nullptr;
    JavaVM *javaVm = nullptr;
    jobject hostApi = nullptr;
    jmethodID hostCallMethod = nullptr;
    std::mutex mutex;
    int64_t timeoutMs = 0;
    int64_t deadlineMs = 0;
};

int64_t nowMs() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

int interruptHandler(JSRuntime *, void *opaque) {
    const auto *state = static_cast<QuickJsRuntimeState *>(opaque);
    if (state == nullptr || state->deadlineMs <= 0) {
        return 0;
    }
    return nowMs() > state->deadlineMs ? 1 : 0;
}

void throwJava(JNIEnv *env, const char *className, const std::string &message) {
    jclass clazz = env->FindClass(className);
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message.c_str());
    }
}

JNIEnv *getEnv(QuickJsRuntimeState *state) {
    if (state == nullptr || state->javaVm == nullptr) {
        return nullptr;
    }
    JNIEnv *env = nullptr;
    jint status = state->javaVm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (status == JNI_OK) {
        return env;
    }
    if (status == JNI_EDETACHED &&
        state->javaVm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
        return env;
    }
    return nullptr;
}

QuickJsRuntimeState *requireState(JNIEnv *env, jlong runtimePtr) {
    if (runtimePtr == 0) {
        throwJava(env, "java/lang/IllegalStateException", "QuickJS runtime is closed");
        return nullptr;
    }
    auto *state = reinterpret_cast<QuickJsRuntimeState *>(runtimePtr);
    if (state->runtime == nullptr || state->context == nullptr) {
        throwJava(env, "java/lang/IllegalStateException", "QuickJS runtime is closed");
        return nullptr;
    }
    return state;
}

std::string valueToString(JSContext *ctx, JSValueConst value) {
    const char *cstr = JS_ToCString(ctx, value);
    if (cstr == nullptr) {
        return "";
    }
    std::string result(cstr);
    JS_FreeCString(ctx, cstr);
    return result;
}

std::string exceptionToString(JSContext *ctx) {
    JSValue exception = JS_GetException(ctx);
    std::string message = valueToString(ctx, exception);
    if (message == "null" || message == "undefined") {
        message.clear();
    }

    JSValue stack = JS_GetPropertyStr(ctx, exception, "stack");
    if (!JS_IsException(stack) && !JS_IsUndefined(stack)) {
        std::string stackMessage = valueToString(ctx, stack);
        if (stackMessage == "null" || stackMessage == "undefined") {
            stackMessage.clear();
        }
        if (!stackMessage.empty() && stackMessage != message) {
            if (!message.empty()) {
                message += "\n";
            }
            message += stackMessage;
        }
    }

    JS_FreeValue(ctx, stack);
    JS_FreeValue(ctx, exception);
    return message.empty() ? "JavaScript execution failed, interrupted, or out of memory" : message;
}

JSValue hostCall(JSContext *ctx, JSValueConst, int argc, JSValueConst *argv) {
    auto *state = static_cast<QuickJsRuntimeState *>(JS_GetContextOpaque(ctx));
    if (state == nullptr || state->hostApi == nullptr || state->hostCallMethod == nullptr) {
        return JS_ThrowInternalError(ctx, "Host API is not available");
    }
    if (argc < 2) {
        return JS_ThrowTypeError(ctx, "__lyricoHostCall requires name and payload");
    }

    JNIEnv *env = getEnv(state);
    if (env == nullptr) {
        return JS_ThrowInternalError(ctx, "Unable to attach JVM thread");
    }

    std::string name = valueToString(ctx, argv[0]);
    std::string payload = valueToString(ctx, argv[1]);
    jstring jName = env->NewStringUTF(name.c_str());
    jstring jPayload = env->NewStringUTF(payload.c_str());
    if (jName == nullptr || jPayload == nullptr) {
        if (jName != nullptr) env->DeleteLocalRef(jName);
        if (jPayload != nullptr) env->DeleteLocalRef(jPayload);
        return JS_ThrowInternalError(ctx, "Failed to allocate host call strings");
    }

    auto result = static_cast<jstring>(env->CallObjectMethod(
            state->hostApi,
            state->hostCallMethod,
            jName,
            jPayload
    ));
    env->DeleteLocalRef(jName);
    env->DeleteLocalRef(jPayload);

    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return JS_ThrowInternalError(ctx, "Host API call failed: %s", name.c_str());
    }
    if (result == nullptr) {
        return JS_ThrowInternalError(ctx, "Host API returned null: %s", name.c_str());
    }

    const char *chars = env->GetStringUTFChars(result, nullptr);
    if (chars == nullptr) {
        env->DeleteLocalRef(result);
        return JS_ThrowInternalError(ctx, "Failed to read host API result");
    }
    JSValue jsResult = JS_NewString(ctx, chars);
    env->ReleaseStringUTFChars(result, chars);
    env->DeleteLocalRef(result);
    return jsResult;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_lonx_lyrico_plugin_runtime_QuickJsNative_createRuntime(
        JNIEnv *env,
        jobject,
        jlong memoryLimitBytes,
        jlong stackSizeBytes,
        jlong timeoutMs,
        jobject hostApi) {
    auto *state = new QuickJsRuntimeState();
    env->GetJavaVM(&state->javaVm);
    state->runtime = JS_NewRuntime();
    if (state->runtime == nullptr) {
        delete state;
        throwJava(env, "java/lang/IllegalStateException", "Failed to create QuickJS runtime");
        return 0;
    }

    if (memoryLimitBytes > 0) {
        JS_SetMemoryLimit(state->runtime, static_cast<size_t>(memoryLimitBytes));
    }
    if (stackSizeBytes > 0) {
        JS_SetMaxStackSize(state->runtime, static_cast<size_t>(stackSizeBytes));
    }

    state->timeoutMs = timeoutMs;
    JS_SetInterruptHandler(state->runtime, interruptHandler, state);

    state->context = JS_NewContext(state->runtime);
    if (state->context == nullptr) {
        JS_FreeRuntime(state->runtime);
        delete state;
        throwJava(env, "java/lang/IllegalStateException", "Failed to create QuickJS context");
        return 0;
    }

    JS_SetContextOpaque(state->context, state);

    if (hostApi != nullptr) {
        state->hostApi = env->NewGlobalRef(hostApi);
        jclass hostClass = env->GetObjectClass(hostApi);
        if (hostClass == nullptr) {
            JS_FreeContext(state->context);
            JS_FreeRuntime(state->runtime);
            if (state->hostApi != nullptr) env->DeleteGlobalRef(state->hostApi);
            delete state;
            throwJava(env, "java/lang/IllegalStateException", "Failed to read host API class");
            return 0;
        }
        state->hostCallMethod = env->GetMethodID(
                hostClass,
                "call",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
        );
        env->DeleteLocalRef(hostClass);
        if (state->hostCallMethod == nullptr) {
            JS_FreeContext(state->context);
            JS_FreeRuntime(state->runtime);
            if (state->hostApi != nullptr) env->DeleteGlobalRef(state->hostApi);
            delete state;
            throwJava(env, "java/lang/IllegalStateException", "Host API call method not found");
            return 0;
        }

        JSValue global = JS_GetGlobalObject(state->context);
        JSValue fn = JS_NewCFunction(state->context, hostCall, "__lyricoHostCall", 2);
        JS_SetPropertyStr(state->context, global, "__lyricoHostCall", fn);
        JS_FreeValue(state->context, global);
    }

    return reinterpret_cast<jlong>(state);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lonx_lyrico_plugin_runtime_QuickJsNative_eval(
        JNIEnv *env,
        jobject,
        jlong runtimePtr,
        jstring script,
        jstring filename) {
    auto *state = requireState(env, runtimePtr);
    if (state == nullptr) {
        return nullptr;
    }
    if (script == nullptr) {
        throwJava(env, "java/lang/IllegalArgumentException", "script is null");
        return nullptr;
    }

    const char *scriptChars = env->GetStringUTFChars(script, nullptr);
    if (scriptChars == nullptr) {
        return nullptr;
    }

    const char *filenameChars = filename != nullptr
            ? env->GetStringUTFChars(filename, nullptr)
            : "<eval>";
    if (filename != nullptr && filenameChars == nullptr) {
        env->ReleaseStringUTFChars(script, scriptChars);
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(state->mutex);
    JS_UpdateStackTop(state->runtime);
    state->deadlineMs = state->timeoutMs > 0 ? nowMs() + state->timeoutMs : 0;

    JSValue result = JS_Eval(
            state->context,
            scriptChars,
            std::strlen(scriptChars),
            filenameChars,
            JS_EVAL_TYPE_GLOBAL
    );
    state->deadlineMs = 0;

    jstring output = nullptr;
    if (JS_IsException(result)) {
        throwJava(env, "java/lang/IllegalStateException", exceptionToString(state->context));
    } else {
        std::string resultString = valueToString(state->context, result);
        output = env->NewStringUTF(resultString.c_str());
    }

    JS_FreeValue(state->context, result);

    if (filename != nullptr) env->ReleaseStringUTFChars(filename, filenameChars);
    env->ReleaseStringUTFChars(script, scriptChars);

    return output;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lonx_lyrico_plugin_runtime_QuickJsNative_call(
        JNIEnv *env,
        jobject,
        jlong runtimePtr,
        jstring functionName,
        jstring requestJson) {
    auto *state = requireState(env, runtimePtr);
    if (state == nullptr) {
        return nullptr;
    }
    if (functionName == nullptr || requestJson == nullptr) {
        throwJava(env, "java/lang/IllegalArgumentException", "functionName and requestJson are required");
        return nullptr;
    }

    const char *functionNameChars = env->GetStringUTFChars(functionName, nullptr);
    if (functionNameChars == nullptr) {
        return nullptr;
    }
    const char *requestJsonChars = env->GetStringUTFChars(requestJson, nullptr);
    if (requestJsonChars == nullptr) {
        env->ReleaseStringUTFChars(functionName, functionNameChars);
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(state->mutex);
    JS_UpdateStackTop(state->runtime);
    state->deadlineMs = state->timeoutMs > 0 ? nowMs() + state->timeoutMs : 0;

    JSValue global = JS_GetGlobalObject(state->context);
    JSValue function = JS_GetPropertyStr(state->context, global, functionNameChars);
    JSValue result = JS_UNDEFINED;
    JSValue jsonResult = JS_UNDEFINED;
    jstring output = nullptr;

    if (!JS_IsFunction(state->context, function)) {
        throwJava(
                env,
                "java/lang/IllegalStateException",
                std::string("JavaScript function not found: ") + functionNameChars
        );
    } else {
        JSValue request = JS_ParseJSON(
                state->context,
                requestJsonChars,
                std::strlen(requestJsonChars),
                "<request>"
        );

        if (JS_IsException(request)) {
            throwJava(env, "java/lang/IllegalArgumentException", exceptionToString(state->context));
        } else {
            JSValue argv[] = { request };
            result = JS_Call(state->context, function, JS_UNDEFINED, 1, argv);

            if (JS_IsException(result)) {
                throwJava(env, "java/lang/IllegalStateException", exceptionToString(state->context));
            } else if (JS_IsUndefined(result)) {
                output = env->NewStringUTF("null");
            } else {
                jsonResult = JS_JSONStringify(state->context, result, JS_UNDEFINED, JS_UNDEFINED);
                if (JS_IsException(jsonResult)) {
                    throwJava(env, "java/lang/IllegalStateException", exceptionToString(state->context));
                } else if (JS_IsUndefined(jsonResult)) {
                    output = env->NewStringUTF("null");
                } else {
                    std::string resultString = valueToString(state->context, jsonResult);
                    output = env->NewStringUTF(resultString.c_str());
                }
            }
        }

        JS_FreeValue(state->context, request);
    }

    state->deadlineMs = 0;

    JS_FreeValue(state->context, jsonResult);
    JS_FreeValue(state->context, result);
    JS_FreeValue(state->context, function);
    JS_FreeValue(state->context, global);

    env->ReleaseStringUTFChars(requestJson, requestJsonChars);
    env->ReleaseStringUTFChars(functionName, functionNameChars);

    return output;
}

extern "C" JNIEXPORT void JNICALL
Java_com_lonx_lyrico_plugin_runtime_QuickJsNative_closeRuntime(
        JNIEnv *,
        jobject,
        jlong runtimePtr) {
    if (runtimePtr == 0) {
        return;
    }

    auto *state = reinterpret_cast<QuickJsRuntimeState *>(runtimePtr);
    std::lock_guard<std::mutex> lock(state->mutex);
    JNIEnv *env = getEnv(state);
    if (state->context != nullptr) {
        JS_FreeContext(state->context);
        state->context = nullptr;
    }
    if (state->hostApi != nullptr && env != nullptr) {
        env->DeleteGlobalRef(state->hostApi);
        state->hostApi = nullptr;
    }
    if (state->runtime != nullptr) {
        JS_FreeRuntime(state->runtime);
        state->runtime = nullptr;
    }
    delete state;
}
