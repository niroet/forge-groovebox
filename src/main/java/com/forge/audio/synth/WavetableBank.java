package com.forge.audio.synth;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a bank of single-cycle waveforms as {@code float[256]} arrays.
 *
 * <p>Provides a default set of 8 waveforms covering the range from pure tones to
 * characterful digital textures. Additional tables can be added at runtime via
 * {@link #addTable(String, float[])}.
 */
public class WavetableBank {

    /** Number of samples in each single-cycle wavetable. Must be a power of two. */
    public static final int TABLE_SIZE = 256;

    private final List<float[]> tables = new ArrayList<>();
    private final List<String>  names  = new ArrayList<>();

    // =========================================================================
    // Constructor — populate default tables
    // =========================================================================

    public WavetableBank() {
        addTable("Sine",     generateSine());
        addTable("Saw",      generateSaw());
        addTable("Square",   generateSquare());
        addTable("Triangle", generateTriangle());
        addTable("Pulse25",  generatePulse(0.25));
        addTable("Vocal",    generateVocal());
        addTable("Digital",  generateDigital());
        addTable("Warm",     generateWarm());
    }

    // =========================================================================
    // Public accessors
    // =========================================================================

    /**
     * Add a custom table to the bank.
     *
     * @param name  display name for the waveform
     * @param table array of exactly {@value #TABLE_SIZE} samples in the range [-1, 1]
     */
    public void addTable(String name, float[] table) {
        if (table.length != TABLE_SIZE) {
            throw new IllegalArgumentException(
                    "Wavetable must have " + TABLE_SIZE + " samples, got " + table.length);
        }
        names.add(name);
        tables.add(table);
    }

    /** @return the number of tables in the bank */
    public int getTableCount() {
        return tables.size();
    }

    /**
     * @param index table index (0-based)
     * @return the sample array for the requested table
     */
    public float[] getTable(int index) {
        return tables.get(index);
    }

    /**
     * @param index table index (0-based)
     * @return the display name of the requested table
     */
    public String getTableName(int index) {
        return names.get(index);
    }

    // =========================================================================
    // Waveform generators
    // =========================================================================

    private static float[] generateSine() {
        float[] t = new float[TABLE_SIZE];
        for (int i = 0; i < TABLE_SIZE; i++) {
            t[i] = (float) Math.sin(2.0 * Math.PI * i / TABLE_SIZE);
        }
        return t;
    }

    /** Band-limited sawtooth approximated by a ramp from -1 to just under +1. */
    private static float[] generateSaw() {
        float[] t = new float[TABLE_SIZE];
        for (int i = 0; i < TABLE_SIZE; i++) {
            t[i] = (float) (2.0 * i / TABLE_SIZE - 1.0);
        }
        return t;
    }

    private static float[] generateSquare() {
        float[] t = new float[TABLE_SIZE];
        int half = TABLE_SIZE / 2;
        for (int i = 0; i < TABLE_SIZE; i++) {
            t[i] = (i < half) ? 1.0f : -1.0f;
        }
        return t;
    }

    private static float[] generateTriangle() {
        float[] t = new float[TABLE_SIZE];
        int quarter = TABLE_SIZE / 4;
        for (int i = 0; i < TABLE_SIZE; i++) {
            // Peaks at i=quarter (+1) and i=3*quarter (-1), zero crossings at 0 and half
            double phase = (double) i / TABLE_SIZE; // 0..1
            t[i] = (float) (1.0 - 4.0 * Math.abs(phase - 0.5));
        }
        return t;
    }

    /**
     * Pulse wave with a variable duty cycle.
     *
     * @param duty fraction of the cycle that is high (0.0–1.0)
     */
    private static float[] generatePulse(double duty) {
        float[] t = new float[TABLE_SIZE];
        int threshold = (int) (TABLE_SIZE * duty);
        for (int i = 0; i < TABLE_SIZE; i++) {
            t[i] = (i < threshold) ? 1.0f : -1.0f;
        }
        return t;
    }

    /**
     * "Vocal" waveform: sum of formant-like harmonics that produce a nasal, vowel-ish quality.
     * Harmonics 1, 2, 3, 4, 5 are weighted to emphasise the 2nd and 3rd partials.
     */
    private static float[] generateVocal() {
        float[] t = new float[TABLE_SIZE];
        double[] amps = {0.5, 0.8, 0.7, 0.3, 0.15};  // harmonic 1–5 amplitudes
        for (int i = 0; i < TABLE_SIZE; i++) {
            double v = 0.0;
            double theta = 2.0 * Math.PI * i / TABLE_SIZE;
            for (int h = 0; h < amps.length; h++) {
                v += amps[h] * Math.sin((h + 1) * theta);
            }
            t[i] = (float) v;
        }
        normalise(t);
        return t;
    }

    /**
     * "Digital" waveform: harsh, aliased-sounding wave with many high-frequency harmonics at
     * similar amplitudes — produces a bright, metallic texture.
     */
    private static float[] generateDigital() {
        float[] t = new float[TABLE_SIZE];
        for (int i = 0; i < TABLE_SIZE; i++) {
            double theta = 2.0 * Math.PI * i / TABLE_SIZE;
            double v = 0.0;
            // Odd harmonics with slowly decaying amplitudes for a harsh digital character
            for (int h = 1; h <= 15; h += 2) {
                v += (1.0 / Math.sqrt(h)) * Math.sin(h * theta);
            }
            // Add a quantisation-step flavour by hard-clipping then adding some even harmonics
            v = Math.max(-1.0, Math.min(1.0, v * 1.2));
            v += 0.15 * Math.sin(4.0 * theta) + 0.1 * Math.sin(8.0 * theta);
            t[i] = (float) v;
        }
        normalise(t);
        return t;
    }

    /**
     * "Warm" waveform: saw wave with high harmonics rolled off — produces a rounded, warm sound
     * similar to a mellow string or pad oscillator.
     */
    private static float[] generateWarm() {
        float[] t = new float[TABLE_SIZE];
        for (int i = 0; i < TABLE_SIZE; i++) {
            double theta = 2.0 * Math.PI * i / TABLE_SIZE;
            double v = 0.0;
            // Sawtooth series (1/n) but attenuated exponentially above harmonic 4
            for (int h = 1; h <= 12; h++) {
                double rolloff = (h <= 4) ? 1.0 : Math.exp(-0.4 * (h - 4));
                v += rolloff * (1.0 / h) * Math.sin(h * theta);
            }
            t[i] = (float) v;
        }
        normalise(t);
        return t;
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /** Scale {@code t} in-place so its peak absolute value is 1.0. */
    private static void normalise(float[] t) {
        float peak = 0.0f;
        for (float v : t) {
            float abs = Math.abs(v);
            if (abs > peak) peak = abs;
        }
        if (peak > 1e-6f) {
            float inv = 1.0f / peak;
            for (int i = 0; i < t.length; i++) {
                t[i] *= inv;
            }
        }
    }
}
