package com.forge.audio.export;

import com.forge.model.DrumPatch;
import com.forge.model.DrumStep;
import com.forge.model.DrumTrack;
import com.forge.model.Pattern;
import com.forge.model.SynthPatch;
import com.forge.model.SynthStep;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Logger;

/**
 * Offline WAV exporter for FORGE patterns.
 *
 * <p>Because JSyn requires real audio hardware to render, this implementation
 * uses a pure-Java synthesis approach for offline rendering: each step is
 * rendered as a short synthesised burst of PCM audio and written to a WAV file
 * via {@link AudioSystem#write}.
 *
 * <p>The rendered audio is a simplified but representative approximation of the
 * live audio output — suitable for demos, previews, and testing. Full fidelity
 * export would require JSyn's offline rendering API.
 */
public class WavExporter {

    private static final Logger LOG = Logger.getLogger(WavExporter.class.getName());

    /** Output sample rate in Hz. */
    public static final int SAMPLE_RATE = 44100;

    /** Bit depth: 16-bit signed PCM. */
    private static final int BITS_PER_SAMPLE = 16;

    /** Mono output. */
    private static final int CHANNELS = 1;

    private WavExporter() { /* static utility class */ }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Export a single pattern as a WAV file.
     *
     * @param pattern     the pattern to render
     * @param drumPatches 4-element array of drum patches (KICK, SNARE, HAT, PERC)
     * @param synthPatch  synth patch (used for note colour — can be null)
     * @param bpm         tempo in BPM
     * @param bars        number of bars to render (≥ 1)
     * @param outputFile  destination WAV file
     * @throws Exception if rendering or writing fails
     */
    public static void exportPattern(Pattern pattern,
                                     DrumPatch[] drumPatches,
                                     SynthPatch synthPatch,
                                     double bpm,
                                     int bars,
                                     File outputFile) throws Exception {
        if (bars < 1) throw new IllegalArgumentException("bars must be >= 1");

        int stepsPerBar = 16;
        int totalSteps  = stepsPerBar * bars;

        // Samples per 16th note at given BPM
        double beatsPerSecond  = bpm / 60.0;
        double beatsPerStep    = 1.0 / 4.0; // 1 bar = 4 beats = 16 steps → each step = 1/4 beat
        double secondsPerStep  = beatsPerStep / beatsPerSecond;
        int    samplesPerStep  = (int) Math.round(secondsPerStep * SAMPLE_RATE);

        int totalSamples = totalSteps * samplesPerStep;
        float[] mixBuffer = new float[totalSamples];

        // Render drums
        for (int trackIdx = 0; trackIdx < 4; trackIdx++) {
            DrumTrack track = DrumTrack.values()[trackIdx];
            DrumPatch patch = (drumPatches != null && drumPatches.length > trackIdx)
                ? drumPatches[trackIdx] : new DrumPatch();

            DrumStep[] steps = pattern.drumSteps[trackIdx];
            int stepCount = Math.min(steps.length, pattern.drumStepCounts[trackIdx]);

            for (int bar = 0; bar < bars; bar++) {
                for (int s = 0; s < Math.min(stepCount, stepsPerBar); s++) {
                    int globalStep = bar * stepsPerBar + s;
                    if (globalStep >= totalSteps) break;
                    if (steps[s] != null && steps[s].active) {
                        int offset = globalStep * samplesPerStep;
                        renderDrumHit(mixBuffer, offset, totalSamples, track, patch, steps[s].velocity);
                    }
                }
            }
        }

        // Render synth steps
        if (synthPatch == null) synthPatch = new SynthPatch();
        SynthStep[] synthSteps = pattern.synthSteps;
        int synthStepCount = Math.min(synthSteps.length, pattern.synthStepCount);

        for (int bar = 0; bar < bars; bar++) {
            for (int s = 0; s < Math.min(synthStepCount, stepsPerBar); s++) {
                int globalStep = bar * stepsPerBar + s;
                if (globalStep >= totalSteps) break;
                if (synthSteps[s] != null && synthSteps[s].active) {
                    int offset = globalStep * samplesPerStep;
                    double gateLen = synthSteps[s].gateLength > 0 ? synthSteps[s].gateLength : 0.5;
                    int gateSamples = (int) (samplesPerStep * gateLen * 2);
                    renderSynthNote(mixBuffer, offset,
                        Math.min(offset + gateSamples, totalSamples),
                        synthSteps[s].midiNote, synthSteps[s].velocity, synthPatch);
                }
            }
        }

        // Normalise and convert to 16-bit PCM
        byte[] pcmData = floatsToPcm16(mixBuffer);

        // Write WAV file
        AudioFormat format = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            SAMPLE_RATE,
            BITS_PER_SAMPLE,
            CHANNELS,
            CHANNELS * (BITS_PER_SAMPLE / 8),
            SAMPLE_RATE,
            false // little-endian
        );

        ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
        AudioInputStream audioStream = new AudioInputStream(bais, format, totalSamples);

        outputFile.getParentFile().mkdirs();
        AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, outputFile);

        LOG.info("[WAV] Exported " + bars + " bar(s) to: " + outputFile.getAbsolutePath()
            + " (" + totalSamples + " samples @ " + SAMPLE_RATE + " Hz)");
    }

    // =========================================================================
    // Private synthesis helpers
    // =========================================================================

    /**
     * Render a single drum hit into the mix buffer.
     * Uses a simple exponentially-decaying oscillator / noise burst model.
     */
    private static void renderDrumHit(float[] buf, int offset, int bufLen,
                                       DrumTrack track, DrumPatch patch, double velocity) {
        if (patch == null) patch = new DrumPatch();

        double freq    = patch.pitch > 0 ? patch.pitch : defaultFreq(track);
        double decay   = patch.decay > 0 ? patch.decay : 0.2;
        double toneNoise = patch.toneNoise; // 1.0 = pure tone, 0.0 = pure noise

        int decaySamples = (int) (decay * SAMPLE_RATE);
        int renderSamples = Math.min(decaySamples, bufLen - offset);

        for (int i = 0; i < renderSamples; i++) {
            double t       = (double) i / SAMPLE_RATE;
            double env     = Math.exp(-t / (decay / 5.0)); // 5-tau envelope
            double phase   = 2 * Math.PI * freq * t;

            // Optional pitch sweep for kicks
            double pitchMod = (track == DrumTrack.KICK) ? Math.exp(-t / 0.02) : 0.0;
            double sweepedPhase = 2 * Math.PI * (freq + freq * 3.0 * pitchMod) * t;

            double toneSample  = Math.sin(sweepedPhase);
            double noiseSample = (Math.random() * 2.0) - 1.0;

            double sample = (toneNoise * toneSample + (1.0 - toneNoise) * noiseSample)
                            * env * (float) velocity;

            buf[offset + i] += (float) sample;
        }
    }

    /**
     * Render a synthesised note (simple additive sawtooth) into the mix buffer.
     */
    private static void renderSynthNote(float[] buf, int startSample, int endSample,
                                         int midiNote, double velocity, SynthPatch patch) {
        double freq = midiNoteToFreq(midiNote);
        double attackTime  = 0.01; // 10 ms
        double releaseTime = 0.05; // 50 ms
        int totalSamples   = endSample - startSample;

        for (int i = 0; i < totalSamples; i++) {
            int absIdx = startSample + i;
            if (absIdx >= buf.length) break;

            double t   = (double) i / SAMPLE_RATE;
            double env = 1.0;

            if (t < attackTime) {
                env = t / attackTime;
            } else if (i >= totalSamples - (int)(releaseTime * SAMPLE_RATE)) {
                int releaseStart = totalSamples - (int)(releaseTime * SAMPLE_RATE);
                double tRelease = (double)(i - releaseStart) / SAMPLE_RATE;
                env = 1.0 - (tRelease / releaseTime);
                env = Math.max(0, env);
            }

            // Sawtooth approximated by summing first few harmonics
            double phase = 2 * Math.PI * freq * t;
            double sample = Math.sin(phase)
                          + 0.5 * Math.sin(2 * phase)
                          + 0.25 * Math.sin(3 * phase);
            sample = (sample / 1.75) * env * velocity * 0.4; // scale down to avoid clipping

            buf[absIdx] += (float) sample;
        }
    }

    /** Convert a float[] mix buffer (may exceed ±1.0) to 16-bit signed little-endian PCM. */
    private static byte[] floatsToPcm16(float[] samples) {
        // Find peak for normalisation
        float peak = 0.001f;
        for (float s : samples) {
            float abs = Math.abs(s);
            if (abs > peak) peak = abs;
        }
        float gain = (peak > 1.0f) ? 1.0f / peak : 1.0f;

        ByteBuffer buf = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (float s : samples) {
            short pcm = (short) Math.max(Short.MIN_VALUE,
                            Math.min(Short.MAX_VALUE, Math.round(s * gain * Short.MAX_VALUE)));
            buf.putShort(pcm);
        }
        return buf.array();
    }

    /** Convert a MIDI note number to frequency in Hz. */
    private static double midiNoteToFreq(int midiNote) {
        return 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0);
    }

    /** Sensible default frequencies when no patch is available. */
    private static double defaultFreq(DrumTrack track) {
        return switch (track) {
            case KICK  -> 60.0;
            case SNARE -> 200.0;
            case HAT   -> 8000.0;
            case PERC  -> 400.0;
        };
    }
}
