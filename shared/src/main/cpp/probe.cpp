#include <jni.h>
#include <android/binder_ibinder.h>
#include <android/binder_ibinder_jni.h>
#include <android/binder_parcel.h>
#include <android/log.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <dlfcn.h>
#include <cerrno>
#include <climits>
#include <cstdio>
#include <cstring>

#define INFO(...) __android_log_print(ANDROID_LOG_INFO, "PicoBinderProbe", __VA_ARGS__)
namespace {
void* onCreate(void* args) { return args; }
void onDestroy(void*) {}
binder_status_t onTransact(AIBinder*, transaction_code_t, const AParcel*, AParcel*) { return STATUS_UNKNOWN_TRANSACTION; }
AIBinder_Class* eyeClass() {
    static AIBinder_Class* clazz = AIBinder_Class_define("pvr.IEyeTrackingService", onCreate, onDestroy, onTransact);
    return clazz;
}
void ioError(JNIEnv* env, const char* operation, int error) {
    char message[256];
    snprintf(message, sizeof(message), "%s errno=%d (%s)", operation, error, strerror(error));
    INFO("%s", message);
    env->ThrowNew(env->FindClass("java/io/IOException"), message);
}
}

extern "C" JNIEXPORT jobject JNICALL
Java_dev_pico_facialprobe_NativeBindings_lookupService(JNIEnv* env, jclass) {
    // This manager entry point exists on Android 10 as an APEX symbol, not a stable NDK API.
    // Resolve it optionally; never claim future-OS portability. Java ServiceManager is a fallback.
    void* lib = dlopen("libbinder_ndk.so", RTLD_NOW | RTLD_LOCAL);
    if (!lib) { INFO("NDK_LIBRARY failed=%s errno=%d", dlerror(), errno); return nullptr; }
    using Check = AIBinder* (*)(const char*);
    auto check = reinterpret_cast<Check>(dlsym(lib, "AServiceManager_checkService"));
    if (!check) { INFO("NDK_MANAGER symbol unavailable=%s", dlerror()); dlclose(lib); return nullptr; }
    errno = 0;
    AIBinder* binder = check("pxreyetrackingservice");
    INFO("NDK_MANAGER checkService binder=%p errno=%d uid=%u", binder, errno, getuid());
    jobject result = nullptr;
    if (binder) {
        INFO("NDK_PING binderStatus=%d", AIBinder_ping(binder));
        result = AIBinder_toJavaBinder(env, binder);
        AIBinder_decStrong(binder);
    }
    dlclose(lib);
    return result;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_dev_pico_facialprobe_NativeBindings_algorithm(JNIEnv* env, jclass, jobject javaBinder, jboolean start) {
    jint result[3] = {STATUS_BAD_VALUE, INT_MIN, INT_MIN};
    AIBinder* binder = AIBinder_fromJavaBinder(env, javaBinder);
    AParcel *request = nullptr, *reply = nullptr;
    if (binder && AIBinder_associateClass(binder, eyeClass())) {
        result[0] = AIBinder_prepareTransaction(binder, &request);
        if (result[0] == STATUS_OK) result[0] = AParcel_writeInt32(request, 5);
        if (result[0] == STATUS_OK) {
            result[0] = start ? AParcel_writeString(request, "12", 2) : AParcel_writeInt32(request, 12);
        }
        if (result[0] == STATUS_OK && start) result[0] = AParcel_writeInt32(request, 1000);
        if (result[0] == STATUS_OK) {
            errno = 0;
            result[0] = AIBinder_transact(binder, start ? 6 : 9, &request, &reply, 0);
            INFO("NDK_%s transportStatus=%d errno=%d", start ? "START" : "STOP", result[0], errno);
        }
        if (result[0] == STATUS_OK) {
            binder_status_t parse = AParcel_readInt32(reply, &result[1]);
            if (parse == STATUS_OK && result[1] == STATUS_OK) parse = AParcel_readInt32(reply, &result[2]);
            INFO("NDK_%s parseStatus=%d binderStatus=%d serviceStatus=%d", start ? "START" : "STOP", parse, result[1], result[2]);
        }
    } else { INFO("NDK_ASSOCIATE_CLASS failed"); }
    if (request) AParcel_delete(request);
    if (reply) AParcel_delete(reply);
    if (binder) AIBinder_decStrong(binder);
    jintArray array = env->NewIntArray(3);
    if (array) env->SetIntArrayRegion(array, 0, 3, result);
    return array;
}

extern "C" JNIEXPORT jobject JNICALL
Java_dev_pico_facialprobe_NativeBindings_map(JNIEnv* env, jclass, jint fd, jint size) {
    struct stat statbuf{};
    errno = 0;
    if (fstat(fd, &statbuf) != 0) { ioError(env, "fstat", errno); return nullptr; }
    if (size < 20 || size > 16 * 1024 * 1024 || (statbuf.st_size > 0 && statbuf.st_size < size)) {
        ioError(env, "mmap bounds", EINVAL); return nullptr;
    }
    void* memory = mmap(nullptr, size, PROT_READ, MAP_SHARED, fd, 0);
    if (memory == MAP_FAILED) { ioError(env, "mmap", errno); return nullptr; }
    INFO("NATIVE_MMAP fd=%d memorySize=%d fstatSize=%lld errno=0 address=%p", fd, size, (long long)statbuf.st_size, memory);
    jobject buffer = env->NewDirectByteBuffer(memory, size);
    if (!buffer) munmap(memory, size);
    return buffer;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_pico_facialprobe_NativeBindings_unmap(JNIEnv* env, jclass, jobject buffer) {
    void* memory = env->GetDirectBufferAddress(buffer);
    jlong size = env->GetDirectBufferCapacity(buffer);
    if (!memory || size <= 0) { ioError(env, "munmap bounds", EINVAL); return; }
    if (munmap(memory, static_cast<size_t>(size)) != 0) ioError(env, "munmap", errno);
    else INFO("NATIVE_MUNMAP size=%lld errno=0", (long long)size);
}
