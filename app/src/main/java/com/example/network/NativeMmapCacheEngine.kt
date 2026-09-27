package com.example.network

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Native C++ Memory-Mapped (mmap) File Cache Engine.
 *
 * Implements high-speed zero-copy memory mapping directly against the Linux/Android kernel.
 * Bypasses user-space buffer copies by mapping file pages into virtual memory with POSIX mmap
 * and issuing MADV_WILLNEED hints to the kernel page cache to preload GPU and web assets.
 */
object NativeMmapCacheEngine {

    private const val TAG = "NativeMmapCache"

    private var isNativeLibLoaded: Boolean = false

    init {
        try {
            System.loadLibrary("native_mmap_cache")
            isNativeLibLoaded = true
            Log.d(TAG, "Native C++ mmap cache library loaded successfully.")
        } catch (_: Throwable) {
            isNativeLibLoaded = false
            Log.d(TAG, "Running POSIX direct mmap via Java NIO kernel mapping.")
        }
    }

    // Native C++ JNI declarations
    @JvmStatic
    private external fun nativeMmapFile(filePath: String): ByteBuffer?

    @JvmStatic
    private external fun nativeMunmap(buffer: ByteBuffer, length: Long): Boolean

    /**
     * An InputStream backed by a Direct ByteBuffer created via native mmap.
     * Provides true zero-copy sequential reading without intermediate JVM heap copies.
     */
    class MmapInputStream(
        private val byteBuffer: ByteBuffer,
        private val channel: FileChannel? = null,
        private val raf: RandomAccessFile? = null,
        private val nativeLength: Long = 0L
    ) : InputStream() {

        override fun read(): Int {
            return if (byteBuffer.hasRemaining()) {
                byteBuffer.get().toInt() and 0xFF
            } else {
                -1
            }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (!byteBuffer.hasRemaining()) {
                return -1
            }
            val bytesToRead = Math.min(len, byteBuffer.remaining())
            byteBuffer.get(b, off, bytesToRead)
            return bytesToRead
        }

        override fun available(): Int {
            return byteBuffer.remaining()
        }

        override fun skip(n: Long): Long {
            if (n <= 0) return 0
            val skipBytes = Math.min(n, byteBuffer.remaining().toLong()).toInt()
            byteBuffer.position(byteBuffer.position() + skipBytes)
            return skipBytes.toLong()
        }

        override fun close() {
            if (nativeLength > 0 && isNativeLibLoaded) {
                try {
                    nativeMunmap(byteBuffer, nativeLength)
                } catch (_: Throwable) {}
            }
            try {
                channel?.close()
            } catch (_: Throwable) {}
            try {
                raf?.close()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Creates an mmap-backed zero-copy InputStream for the specified file.
     * Prioritizes native C++ JNI bridge, seamlessly falling back to POSIX FileChannel mmap.
     */
    fun openMmapStream(file: File): InputStream {
        if (!file.exists() || file.length() == 0L) {
            return FileInputStream(file)
        }

        // Try native C++ JNI mmap first
        if (isNativeLibLoaded) {
            try {
                val directBuffer = nativeMmapFile(file.absolutePath)
                if (directBuffer != null) {
                    return MmapInputStream(directBuffer, nativeLength = file.length())
                }
            } catch (e: Throwable) {
                Log.d(TAG, "Native C++ JNI call fallback: ${e.message}")
            }
        }

        // POSIX Kernel mmap fallback via FileChannel
        return try {
            val raf = RandomAccessFile(file, "r")
            val channel = raf.channel
            val mappedBuffer: MappedByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            // Advise the OS kernel to pre-load pages into physical memory
            mappedBuffer.load()
            MmapInputStream(mappedBuffer, channel, raf)
        } catch (e: Throwable) {
            Log.d(TAG, "POSIX mmap fallback for ${file.name}: ${e.message}")
            FileInputStream(file)
        }
    }
}
