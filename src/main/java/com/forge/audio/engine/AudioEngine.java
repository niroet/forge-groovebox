package com.forge.audio.engine;

import com.forge.audio.effects.EffectsChain;
import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.unitgen.LineOut;
import com.jsyn.unitgen.MixerMono;

/**
 * Central audio engine for the FORGE Groovebox.
 *
 * <p>Wraps a JSyn {@link Synthesizer} instance, provides a master mono mixer that downstream
 * synth voices can connect to, and owns the {@link AnalysisBus} and {@link AudioRingBuffer}
 * shared with the UI layer.
 *
 * <p>Typical lifecycle:
 * <pre>
 *   AudioEngine engine = new AudioEngine();
 *   engine.start();
 *   // ... plug voices into engine.getMasterMixer() ...
 *   engine.stop();
 * </pre>
 */
public final class AudioEngine {

    // ---- Constants ----------------------------------------------------------

    /** Sample rate used throughout the engine. */
    public static final int SAMPLE_RATE = 44_100;

    /**
     * Audio processing block size in samples. Matches JSyn's default render quantum and is
     * also the unit in which analysis updates are pushed to the {@link AnalysisBus}.
     */
    public static final int BUFFER_SIZE = 256;

    // ---- Number of mixer input channels available for voices ----------------
    private static final int MIXER_CHANNELS = 16;

    // ---- JSyn graph ---------------------------------------------------------
    private final Synthesizer synth;
    private final MixerMono   masterMixer;
    private final LineOut      lineOut;

    // ---- Effects chain (inserted between mixer and lineOut) -----------------
    private final EffectsChain effectsChain;

    // ---- Shared data --------------------------------------------------------
    private final AnalysisBus     analysisBus;
    private final AudioRingBuffer ringBuffer;
    private final AnalysisDriver  analysisDriver;

    // ---- State --------------------------------------------------------------
    private volatile boolean running;

    // =========================================================================

    /** Construct the audio engine.  Does not start audio output; call {@link #start()}. */
    public AudioEngine() {
        synth = JSyn.createSynthesizer();

        masterMixer = new MixerMono(MIXER_CHANNELS);
        lineOut     = new LineOut();

        synth.add(masterMixer);
        synth.add(lineOut);

        // Create effects chain and wire it between mixer and lineOut:
        //   masterMixer.output → EffectsChain.input → EffectsChain.output → lineOut.input
        effectsChain = new EffectsChain(synth);
        masterMixer.output.connect(0, effectsChain.getInput(), 0);
        effectsChain.getOutput().connect(0, lineOut.input, 0);
        effectsChain.getOutput().connect(0, lineOut.input, 1);

        analysisBus    = new AnalysisBus();
        ringBuffer     = new AudioRingBuffer(BUFFER_SIZE * 8); // 8 blocks of headroom

        // Analysis driver: taps the master mix and feeds the FftProcessor
        analysisDriver = new AnalysisDriver(analysisBus);
        synth.add(analysisDriver);
        // Connect master mixer output channel 0 to the analysis input
        masterMixer.output.connect(0, analysisDriver.input, 0);
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Start the JSyn synthesizer and begin audio output.
     *
     * @throws IllegalStateException if the engine is already running
     */
    public void start() {
        if (running) throw new IllegalStateException("AudioEngine is already running");
        synth.start(SAMPLE_RATE);
        lineOut.start();
        analysisDriver.start();
        running = true;
    }

    /**
     * Stop audio output and shut down the JSyn synthesizer.
     */
    public void stop() {
        if (!running) return;
        lineOut.stop();
        synth.stop();
        running = false;
    }

    /** @return {@code true} if the engine is currently producing audio */
    public boolean isRunning() {
        return running;
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /**
     * @return the JSyn {@link Synthesizer} — use this to add unit generators and schedule
     *         events
     */
    public Synthesizer getSynth() {
        return synth;
    }

    /**
     * @return the master {@link MixerMono} — connect voice outputs to the inputs of this
     *         mixer to route them to the hardware output
     */
    public MixerMono getMasterMixer() {
        return masterMixer;
    }

    /**
     * @return the {@link LineOut} connected to the hardware speakers
     */
    public LineOut getLineOut() {
        return lineOut;
    }

    /**
     * @return the shared {@link AnalysisBus} for UI visualisers
     */
    public AnalysisBus getAnalysisBus() {
        return analysisBus;
    }

    /**
     * @return the {@link AudioRingBuffer} for streaming raw samples to the UI
     */
    public AudioRingBuffer getRingBuffer() {
        return ringBuffer;
    }

    /**
     * @return the {@link EffectsChain} wired between the master mixer and the line-out
     */
    public EffectsChain getEffectsChain() {
        return effectsChain;
    }
}
