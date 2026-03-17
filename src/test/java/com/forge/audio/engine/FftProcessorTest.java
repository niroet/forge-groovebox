package com.forge.audio.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FftProcessor}.
 */
class FftProcessorTest {

    private FftProcessor processor;
    private AnalysisBus   bus;

    @BeforeEach
    void setUp() {
        processor = new FftProcessor();
        bus       = new AnalysisBus();
    }

    // -----------------------------------------------------------------------
    // Pure sine wave at 440 Hz
    // -----------------------------------------------------------------------

    /**
     * Feed a 440 Hz sine wave sampled at 44100 Hz.
     *
     * Expected peak bin: 440 / (44100 / 256) ≈ 2.56  → bin 2 or 3.
     * Expected RMS: 1 / sqrt(2) ≈ 0.7071 (sine with amplitude 1.0).
     */
    @Test
    void pureSine440Hz_peakBinAndRms() {
        float[] samples = generate440HzSine(1.0f);

        processor.process(samples, bus);

        // --- Peak bin check ---
        float[] mags = bus.getFftMagnitudes();
        assertNotNull(mags, "FFT magnitudes must not be null");
        assertEquals(FftProcessor.BINS, mags.length, "must return exactly 128 bins");

        int peakBin = 0;
        for (int k = 1; k < FftProcessor.BINS; k++) {
            if (mags[k] > mags[peakBin]) peakBin = k;
        }
        // 440 / (44100/256) ≈ 2.56 → peak should be in bin 2 or 3
        assertTrue(peakBin == 2 || peakBin == 3,
            "peak bin for 440 Hz should be 2 or 3, was " + peakBin);

        // --- RMS check ---
        // A full-amplitude sine over 256 samples (not an integer number of cycles at 440/44100)
        // will have RMS very close to 1/sqrt(2) ≈ 0.7071, but the fractional cycle boundary
        // means a small deviation is expected. Tolerance is 0.02 to accommodate this.
        float rms = bus.getRmsEnergy();
        assertEquals(0.7071f, rms, 0.02f,
            "RMS of unit-amplitude sine should be ~0.707, was " + rms);
    }

    @Test
    void pureSine440Hz_peakAmplitudeNearOne() {
        float[] samples = generate440HzSine(1.0f);

        processor.process(samples, bus);

        float peak = bus.getPeakAmplitude();
        // Peak of sin(x) over 256 samples will be very close to 1.0
        assertTrue(peak > 0.99f && peak <= 1.001f,
            "peak amplitude should be close to 1.0, was " + peak);
    }

    @Test
    void spectralCentroidInRange() {
        float[] samples = generate440HzSine(1.0f);
        processor.process(samples, bus);

        float centroid = bus.getSpectralCentroid();
        // Centroid should be normalised [0, 1]
        assertTrue(centroid >= 0f && centroid <= 1f,
            "spectral centroid must be in [0, 1], was " + centroid);
        // A low-frequency sine's centroid should be close to 0
        assertTrue(centroid < 0.1f,
            "centroid of a 440 Hz sine should be near 0, was " + centroid);
    }

    @Test
    void waveformSamplesPassthrough() {
        float[] samples = generate440HzSine(1.0f);
        processor.process(samples, bus);

        float[] wave = bus.getWaveformSamples();
        assertNotNull(wave);
        assertEquals(256, wave.length);
        // First few samples should match input exactly
        for (int i = 0; i < 256; i++) {
            assertEquals(samples[i], wave[i], 1e-6f, "waveform sample " + i + " should pass through unchanged");
        }
    }

    @Test
    void silenceProducesZeroRmsAndNoBeat() {
        float[] silence = new float[256];
        processor.process(silence, bus);

        assertEquals(0f, bus.getRmsEnergy(), 1e-8f, "RMS of silence should be 0");
        assertEquals(0f, bus.getPeakAmplitude(), 1e-8f, "peak of silence should be 0");
        assertFalse(bus.isBeatDetected(), "silence should not trigger beat detection");
    }

    @Test
    void beatDetectedOnRmsSpike() {
        // Prime the running average with a quiet signal
        float[] quiet = generateSine(440f, 0.05f);
        for (int i = 0; i < 50; i++) {
            processor.process(quiet, bus);
        }
        assertFalse(bus.isBeatDetected(), "quiet signal should not trigger beat");

        // Now send a loud spike (amplitude 1.0, much louder than 0.05)
        float[] loud = generateSine(440f, 1.0f);
        processor.process(loud, bus);

        assertTrue(bus.isBeatDetected(), "loud spike after quiet signal should trigger beat");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static float[] generate440HzSine(float amplitude) {
        return generateSine(440f, amplitude);
    }

    private static float[] generateSine(float freqHz, float amplitude) {
        float[] s = new float[FftProcessor.SIZE];
        double phaseInc = 2.0 * Math.PI * freqHz / 44100.0;
        for (int i = 0; i < FftProcessor.SIZE; i++) {
            s[i] = (float) (amplitude * Math.sin(phaseInc * i));
        }
        return s;
    }
}
