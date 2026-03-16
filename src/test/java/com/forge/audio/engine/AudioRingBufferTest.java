package com.forge.audio.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AudioRingBufferTest {

    @Test
    void writeAndReadRoundTrip() {
        AudioRingBuffer buf = new AudioRingBuffer(16);

        float[] input = { 0.1f, 0.2f, 0.3f, 0.4f };
        buf.write(input, 0, input.length);

        assertEquals(4, buf.available(), "should have 4 samples available");

        float[] output = new float[4];
        int read = buf.read(output, 0, output.length);

        assertEquals(4, read, "should read back exactly 4 samples");
        assertArrayEquals(input, output, 1e-6f, "round-tripped samples must be identical");
        assertEquals(0, buf.available(), "buffer should be empty after full read");
    }

    @Test
    void emptyBufferReadsZero() {
        AudioRingBuffer buf = new AudioRingBuffer(8);

        float[] dest = new float[4];
        int read = buf.read(dest, 0, dest.length);

        assertEquals(0, read, "reading from empty buffer must return 0");
    }
}
