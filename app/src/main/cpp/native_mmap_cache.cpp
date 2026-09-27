#include <jni.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <android/log.h>
#include <cstdint>

#define LOG_TAG "NativeMmapCacheEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * Native C++ Memory-Mapped (mmap) File Cache Bridge.
 *
 * Implements high-speed zero-copy memory mapping directly against the Linux/Android kernel.
 * Bypasses user-space buffer copies by mapping file pages into virtual memory with POSIX mmap
 * and issuing MADV_WILLNEED hints to the kernel page cache to preload GPU and web assets.
 */
extern "C" {

/**
 * Maps a file into direct memory space using POSIX mmap.
 * Returns a Direct ByteBuffer wrapping the mapped memory region.
 */
JNIEXPORT jobject JNICALL
Java_com_example_network_NativeMmapCacheEngine_nativeMmapFile(
    JNIEnv *env,
    jobject /* thiz */,
    jstring filePath
) {
    if (filePath == nullptr) {
        return nullptr;
    }

    const char *path = env->GetStringUTFChars(filePath, nullptr);
    if (path == nullptr) {
        return nullptr;
    }

    int fd = open(path, O_RDONLY);
    if (fd < 0) {
        LOGW("Failed to open file for native mmap: %s", path);
        env->ReleaseStringUTFChars(filePath, path);
        return nullptr;
    }
    env->ReleaseStringUTFChars(filePath, path);

    struct stat sb;
    if (fstat(fd, &sb) < 0 || sb.st_size <= 0) {
        close(fd);
        return nullptr;
    }

    size_t length = static_cast<size_t>(sb.st_size);

    // Call POSIX mmap for shared read-only virtual memory allocation
    void *mappedAddress = mmap(nullptr, length, PROT_READ, MAP_SHARED, fd, 0);
    close(fd); // File descriptor can be safely closed after mmap succeeds

    if (mappedAddress == MAP_FAILED) {
        LOGE("POSIX mmap syscall failed for size %zu", length);
        return nullptr;
    }

    // Advise the Linux kernel that the pages will be accessed immediately
    // to preload them into physical RAM for the GPU / rendering pipeline
    madvise(mappedAddress, length, MADV_WILLNEED);
    madvise(mappedAddress, length, MADV_SEQUENTIAL);

    LOGD("Successfully mmap'd %zu bytes at address %p", length, mappedAddress);

    // Return a direct ByteBuffer that references the native mapped memory directly
    return env->NewDirectByteBuffer(mappedAddress, static_cast<jlong>(length));
}

/**
 * Unmaps the memory region previously mapped by nativeMmapFile.
 */
JNIEXPORT jboolean JNICALL
Java_com_example_network_NativeMmapCacheEngine_nativeMunmap(
    JNIEnv *env,
    jobject /* thiz */,
    jobject directByteBuffer,
    jlong length
) {
    if (directByteBuffer == nullptr || length <= 0) {
        return JNI_FALSE;
    }

    void *mappedAddress = env->GetDirectBufferAddress(directByteBuffer);
    if (mappedAddress == nullptr) {
        return JNI_FALSE;
    }

    int result = munmap(mappedAddress, static_cast<size_t>(length));
    if (result == 0) {
        LOGD("Successfully munmap'd %lld bytes at address %p", (long long)length, mappedAddress);
        return JNI_TRUE;
    } else {
        LOGE("Failed to munmap address %p", mappedAddress);
        return JNI_FALSE;
    }
}

}
