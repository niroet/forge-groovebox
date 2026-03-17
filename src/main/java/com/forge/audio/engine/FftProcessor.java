package com.forge.audio.engine;

/**
 * Pure-Java Cooley-Tukey radix-2 DIT FFT processor.
 *
 * <p>Processes 256-sample frames and writes analysis results to an {@link AnalysisBus}.
 * Uses a Hann window to reduce spectral leakage. Exposes 128 magnitude bins.
 *
 * <p>This class is not thread-safe; callers must ensure only one thread invokes
 * {@link #process} at a time. In practice it is called exclusively from the audio thread.
 */
public final class FftProcessor {

    public static final int SIZE = 256;
    public static final int BINS = SIZE / 2;   // 128 magnitude bins

    private final float[] hannWindow;
    private final float[] real;
    private final float[] imag;

    // Beat detection state: exponentially-smoothed RMS
    private float runningAvgRms = 0f;
    private static final float BEAT_THRESHOLD  = 1.5f;
    private static final float SMOOTH_COEFF    = 0.1f;   // for running average update

    public FftProcessor() {
        hannWindow = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            hannWindow[i] = (float) (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / SIZE)));
        }
        real = new float[SIZE];
        imag = new float[SIZE];
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Process one 256-sample frame. Applies the Hann window, runs the FFT,
     * computes all analysis metrics, and writes results to {@code bus}.
     *
     * @param samples 256 mono samples in [-1, 1]
     * @param bus     analysis bus to receive results
     */
    public void process(float[] samples, AnalysisBus bus) {
        if (samples.length < SIZE) {
            throw new IllegalArgumentException("samples array must have at least " + SIZE + " elements");
        }

        // 1. Apply Hann window and copy into working arrays
        for (int i = 0; i < SIZE; i++) {
            real[i] = samples[i] * hannWindow[i];
            imag[i] = 0f;
        }

        // 2. In-place FFT
        fft(real, imag, SIZE);

        // 3. Compute magnitude spectrum (128 bins, normalised)
        float[] magnitudes = new float[BINS];
        float magScale = 2.0f / SIZE;   // 2x because we only look at positive frequencies
        for (int k = 0; k < BINS; k++) {
            float re = real[k];
            float im = imag[k];
            magnitudes[k] = (float) Math.sqrt(re * re + im * im) * magScale;
        }

        // 4. Compute RMS energy of the raw (un-windowed) input
        double sumSq = 0.0;
        for (int i = 0; i < SIZE; i++) {
            double s = samples[i];
            sumSq += s * s;
        }
        float rms = (float) Math.sqrt(sumSq / SIZE);

        // 5. Compute peak amplitude
        float peak = 0f;
        for (int i = 0; i < SIZE; i++) {
            float abs = Math.abs(samples[i]);
            if (abs > peak) peak = abs;
        }

        // 6. Spectral centroid: weighted average bin index, normalised to [0, 1]
        double weightedBinSum = 0.0;
        double totalMag = 0.0;
        for (int k = 0; k < BINS; k++) {
            weightedBinSum += k * magnitudes[k];
            totalMag       += magnitudes[k];
        }
        float centroid = (totalMag > 1e-10) ? (float) (weightedBinSum / (totalMag * (BINS - 1))) : 0f;
        centroid = Math.max(0f, Math.min(1f, centroid));

        // 7. Beat detection: RMS spike relative to running average
        boolean beat = false;
        if (runningAvgRms > 1e-6f) {
            beat = rms > runningAvgRms * BEAT_THRESHOLD;
        }
        // Update running average (exponential moving average)
        if (runningAvgRms < 1e-10f) {
            runningAvgRms = rms;
        } else {
            runningAvgRms = runningAvgRms * (1f - SMOOTH_COEFF) + rms * SMOOTH_COEFF;
        }

        // 8. Write waveform snapshot (raw samples — no window applied)
        float[] waveSnap = new float[SIZE];
        System.arraycopy(samples, 0, waveSnap, 0, SIZE);

        // 9. Publish to AnalysisBus
        bus.setFftMagnitudes(magnitudes);
        bus.setWaveformSamples(waveSnap);
        bus.setRmsEnergy(rms);
        bus.setPeakAmplitude(peak);
        bus.setSpectralCentroid(centroid);
        bus.setBeatDetected(beat);
    }

    // =========================================================================
    // Cooley-Tukey radix-2 DIT FFT
    // =========================================================================

    /**
     * In-place Cooley-Tukey radix-2 Decimation-In-Time FFT.
     * {@code n} must be a power of two.
     *
     * @param re real parts (in/out)
     * @param im imaginary parts (in/out)
     * @param n  transform length (power of two)
     */
    private void fft(float[] re, float[] im, int n) {
        // --- Bit-reversal permutation ---
        int j = 0;
        for (int i = 1; i < n; i++) {
            int bit = n >> 1;
            while ((j & bit) != 0) {
                j ^= bit;
                bit >>= 1;
            }
            j ^= bit;

            if (i < j) {
                float tmpR = re[i]; re[i] = re[j]; re[j] = tmpR;
                float tmpI = im[i]; im[i] = im[j]; im[j] = tmpI;
            }
        }

        // --- Butterfly stages ---
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2.0 * Math.PI / len;
            float wRe = (float) Math.cos(angle);
            float wIm = (float) Math.sin(angle);

            for (int i = 0; i < n; i += len) {
                float curRe = 1f;
                float curIm = 0f;

                for (int k = 0; k < len / 2; k++) {
                    int lo = i + k;
                    int hi = i + k + len / 2;

                    float evenRe = re[lo];
                    float evenIm = im[lo];
                    float oddRe  = re[hi] * curRe - im[hi] * curIm;
                    float oddIm  = re[hi] * curIm + im[hi] * curRe;

                    re[lo] = evenRe + oddRe;
                    im[lo] = evenIm + oddIm;
                    re[hi] = evenRe - oddRe;
                    im[hi] = evenIm - oddIm;

                    // Advance twiddle factor
                    float nextRe = curRe * wRe - curIm * wIm;
                    float nextIm = curRe * wIm + curIm * wRe;
                    curRe = nextRe;
                    curIm = nextIm;
                }
            }
        }
    }
}
