package com.forge.audio.engine;

/**
 * Shared analysis data written by the audio thread and read by the UI thread.
 *
 * <p>All scalar fields are declared {@code volatile} so that writes from the audio thread are
 * immediately visible to the UI thread without locks. Array fields follow a swap pattern:
 * the audio thread always assigns a <em>new</em> array reference — it never mutates an
 * existing one. Because a reference assignment is atomic on the JVM, the UI thread will
 * always observe either the old or the new complete array, never a partially-written one.
 *
 * <p>Thread-safety contract:
 * <ul>
 *   <li>All setters are called exclusively from the audio thread.</li>
 *   <li>All getters are called exclusively from the UI (JavaFX application) thread.</li>
 * </ul>
 */
public final class AnalysisBus {

    // --- Frequency domain ---
    /** 128 FFT magnitude bins, normalised to [0, 1]. */
    private volatile float[] fftMagnitudes = new float[128];

    // --- Time domain ---
    /** Most recent 256 mono samples, normalised to [-1, 1]. */
    private volatile float[] waveformSamples = new float[256];

    // --- Energy / dynamics ---
    /** Root-mean-square energy of the current audio frame. */
    private volatile float rmsEnergy;

    /** Peak absolute sample amplitude of the current audio frame. */
    private volatile float peakAmplitude;

    /** Spectral centroid in normalised frequency [0, 1]. */
    private volatile float spectralCentroid;

    // --- Beat detection ---
    /** Set to {@code true} by the audio thread when an onset is detected. */
    private volatile boolean beatDetected;

    // --- Sequencer clock ---
    /** {@code true} on the beat tick driven by the sequencer clock. */
    private volatile boolean clockBeat;

    /** Current sequencer step index (0-based). */
    private volatile int clockStep;

    // =========================================================================
    // Setters — audio thread only
    // =========================================================================

    /**
     * Swap in a fresh FFT magnitudes array.  The audio thread must pass a newly-allocated
     * array; never pass an array that is already referenced by this bus.
     *
     * @param magnitudes new array of 128 FFT bin magnitudes
     */
    public void setFftMagnitudes(float[] magnitudes) {
        this.fftMagnitudes = magnitudes;
    }

    /**
     * Swap in a fresh waveform snapshot.
     *
     * @param samples new array of 256 waveform samples
     */
    public void setWaveformSamples(float[] samples) {
        this.waveformSamples = samples;
    }

    /** Set the RMS energy for the current frame. */
    public void setRmsEnergy(float rmsEnergy) {
        this.rmsEnergy = rmsEnergy;
    }

    /** Set the peak amplitude for the current frame. */
    public void setPeakAmplitude(float peakAmplitude) {
        this.peakAmplitude = peakAmplitude;
    }

    /** Set the spectral centroid (normalised [0, 1]). */
    public void setSpectralCentroid(float spectralCentroid) {
        this.spectralCentroid = spectralCentroid;
    }

    /** Set whether a beat was detected in the most recent frame. */
    public void setBeatDetected(boolean beatDetected) {
        this.beatDetected = beatDetected;
    }

    /** Set the sequencer clock beat flag. */
    public void setClockBeat(boolean clockBeat) {
        this.clockBeat = clockBeat;
    }

    /** Set the current sequencer step (0-based). */
    public void setClockStep(int clockStep) {
        this.clockStep = clockStep;
    }

    // =========================================================================
    // Getters — UI thread only
    // =========================================================================

    /** @return current FFT magnitudes array (128 bins) */
    public float[] getFftMagnitudes() {
        return fftMagnitudes;
    }

    /** @return current waveform snapshot (256 samples) */
    public float[] getWaveformSamples() {
        return waveformSamples;
    }

    /** @return RMS energy of the most recently processed frame */
    public float getRmsEnergy() {
        return rmsEnergy;
    }

    /** @return peak amplitude of the most recently processed frame */
    public float getPeakAmplitude() {
        return peakAmplitude;
    }

    /** @return spectral centroid, normalised [0, 1] */
    public float getSpectralCentroid() {
        return spectralCentroid;
    }

    /** @return {@code true} if a beat was detected in the most recent frame */
    public boolean isBeatDetected() {
        return beatDetected;
    }

    /** @return {@code true} on the sequencer clock beat tick */
    public boolean isClockBeat() {
        return clockBeat;
    }

    /** @return current sequencer step index (0-based) */
    public int getClockStep() {
        return clockStep;
    }
}
