package com.forge.audio.engine;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lock-free Single-Producer Single-Consumer (SPSC) ring buffer for audio samples.
 *
 * <p>The audio thread is the sole producer (calls {@link #write}); the UI thread is the sole
 * consumer (calls {@link #read}). Capacity is rounded up to the next power of two so that
 * modulo arithmetic can be replaced with a cheap bitwise AND.
 *
 * <p>Concurrency contract: read and write positions are stored in {@link AtomicInteger}s so
 * that plain reads/writes are visible across threads without locks. The audio thread never
 * mutates the backing array in place — it writes into a pre-allocated slot and advances the
 * write position only after the data is fully written.
 */
public final class AudioRingBuffer {

    private final float[] buffer;
    private final int     capacity;   // always a power of 2
    private final int     mask;       // capacity - 1, for fast modulo

    private final AtomicInteger writePos = new AtomicInteger(0);
    private final AtomicInteger readPos  = new AtomicInteger(0);

    /**
     * @param minCapacity minimum number of float samples the buffer must hold; actual capacity
     *                    is rounded up to the next power of two.
     */
    public AudioRingBuffer(int minCapacity) {
        if (minCapacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = nextPowerOfTwo(minCapacity);
        this.mask     = this.capacity - 1;
        this.buffer   = new float[this.capacity];
    }

    // ---- Producer API (audio thread) ----------------------------------------

    /**
     * Write {@code length} samples from {@code data[offset..offset+length-1]} into the ring
     * buffer. If the buffer does not have enough free space the excess samples are silently
     * dropped — the audio thread must never block.
     *
     * @param data   source array
     * @param offset start index in {@code data}
     * @param length number of samples to write
     */
    public void write(float[] data, int offset, int length) {
        int wp   = writePos.get();
        int rp   = readPos.get();
        int free = capacity - (wp - rp);  // always non-negative because wp >= rp logically

        int toWrite = Math.min(length, free);
        for (int i = 0; i < toWrite; i++) {
            buffer[(wp + i) & mask] = data[offset + i];
        }
        // Publish the new write position after all samples are in the buffer.
        writePos.set(wp + toWrite);
    }

    // ---- Consumer API (UI thread) -------------------------------------------

    /**
     * Read up to {@code maxLength} samples into {@code dest[offset..]} and return the number
     * of samples actually read. Returns 0 if the buffer is empty.
     *
     * @param dest      destination array
     * @param offset    start index in {@code dest}
     * @param maxLength maximum samples to read
     * @return number of samples read
     */
    public int read(float[] dest, int offset, int maxLength) {
        int rp        = readPos.get();
        int wp        = writePos.get();
        int available = wp - rp;

        int toRead = Math.min(maxLength, available);
        for (int i = 0; i < toRead; i++) {
            dest[offset + i] = buffer[(rp + i) & mask];
        }
        readPos.set(rp + toRead);
        return toRead;
    }

    /**
     * @return number of samples currently available to read
     */
    public int available() {
        return writePos.get() - readPos.get();
    }

    // ---- Internal helpers ---------------------------------------------------

    private static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }
}
