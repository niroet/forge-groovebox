package com.forge.midi;

import com.forge.audio.drums.DrumEngine;
import com.forge.audio.synth.VoiceAllocator;
import com.forge.model.DrumTrack;

import javax.sound.midi.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Receives MIDI input from the first available hardware MIDI input device.
 *
 * <p>Channel routing:
 * <ul>
 *   <li>Channels 1–9 (MIDI channels 0–8): routed to {@link VoiceAllocator}</li>
 *   <li>Channel 10 (MIDI channel 9): routed to {@link DrumEngine} using GM drum map</li>
 * </ul>
 *
 * <p>CC messages are dispatched to registered {@link Runnable} handlers keyed by CC number.
 *
 * <p>If no MIDI device is available the handler silently continues — MIDI is optional.
 */
public class MidiInputHandler implements Receiver {

    private static final Logger LOG = Logger.getLogger(MidiInputHandler.class.getName());

    // GM drum map note numbers
    private static final int GM_KICK  = 36;
    private static final int GM_SNARE = 38;
    private static final int GM_HAT   = 42;
    private static final int GM_PERC  = 47;

    // MIDI channel index for drums (0-based; channel 10 in 1-based convention)
    private static final int DRUM_CHANNEL = 9;

    private final VoiceAllocator voiceAllocator;
    private final DrumEngine drumEngine;

    /** CC number → action (e.g. map CC 7 to a volume knob). */
    private final Map<Integer, Runnable> ccMappings = new HashMap<>();

    private MidiDevice device;
    private volatile boolean connected = false;

    /**
     * Construct an unstarted handler.
     *
     * @param voiceAllocator synth voice allocator for channels 1–9
     * @param drumEngine     drum engine for channel 10
     */
    public MidiInputHandler(VoiceAllocator voiceAllocator, DrumEngine drumEngine) {
        this.voiceAllocator = voiceAllocator;
        this.drumEngine = drumEngine;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Open the first available MIDI input device (excludes Java internal synthesisers).
     * If none is found, logs a message and returns quietly — MIDI is optional.
     */
    public void start() {
        MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
        for (MidiDevice.Info info : infos) {
            try {
                MidiDevice candidate = MidiSystem.getMidiDevice(info);
                // getMaxTransmitters() == 0 means output-only; -1 means unlimited
                if (candidate.getMaxTransmitters() == 0) continue;
                // Skip the built-in Java synthesiser and sequencer
                if (candidate instanceof Synthesizer || candidate instanceof Sequencer) continue;

                candidate.open();
                candidate.getTransmitter().setReceiver(this);
                device = candidate;
                connected = true;
                LOG.info("[MIDI] Connected to: " + info.getName());
                return;
            } catch (MidiUnavailableException e) {
                LOG.log(Level.FINE, "[MIDI] Skipping device: " + info.getName(), e);
            }
        }
        LOG.info("[MIDI] No MIDI input device found — running without hardware MIDI.");
    }

    /** Close the MIDI device if one was opened. */
    public void stop() {
        connected = false;
        if (device != null && device.isOpen()) {
            device.close();
            LOG.info("[MIDI] Device closed.");
        }
        device = null;
    }

    /** Returns {@code true} if a MIDI device is currently open and active. */
    public boolean isConnected() {
        return connected && device != null && device.isOpen();
    }

    // =========================================================================
    // CC mappings
    // =========================================================================

    /**
     * Register a handler for a specific MIDI CC number.
     *
     * @param ccNumber  MIDI CC number (0–127)
     * @param handler   runnable invoked on each CC event for that number
     */
    public void registerCcMapping(int ccNumber, Runnable handler) {
        ccMappings.put(ccNumber, handler);
    }

    /** Remove the CC mapping for the given CC number, if any. */
    public void unregisterCcMapping(int ccNumber) {
        ccMappings.remove(ccNumber);
    }

    // =========================================================================
    // Receiver implementation
    // =========================================================================

    @Override
    public void send(MidiMessage message, long timeStamp) {
        if (!(message instanceof ShortMessage sm)) return;

        int command = sm.getCommand();
        int channel = sm.getChannel();
        int data1   = sm.getData1();   // note or CC number
        int data2   = sm.getData2();   // velocity or CC value

        switch (command) {
            case ShortMessage.NOTE_ON -> {
                if (data2 == 0) {
                    // Note On with velocity 0 is treated as Note Off per MIDI spec
                    handleNoteOff(channel, data1);
                } else {
                    handleNoteOn(channel, data1, data2);
                }
            }
            case ShortMessage.NOTE_OFF -> handleNoteOff(channel, data1);
            case ShortMessage.CONTROL_CHANGE -> handleControlChange(data1, data2);
            default -> { /* other message types ignored */ }
        }
    }

    @Override
    public void close() {
        stop();
    }

    // =========================================================================
    // Private routing helpers
    // =========================================================================

    private void handleNoteOn(int channel, int note, int velocity) {
        double normVelocity = velocity / 127.0;

        if (channel == DRUM_CHANNEL) {
            // Route to drum engine using GM drum map
            DrumTrack track = gmNoteToTrack(note);
            if (track != null) {
                drumEngine.trigger(track, normVelocity);
                LOG.fine("[MIDI] Drum trigger: " + track + " vel=" + normVelocity);
            }
        } else {
            // Channels 0-8 (MIDI 1-9): route to synth voices
            voiceAllocator.allocate(note, normVelocity);
            LOG.fine("[MIDI] Note ON: ch=" + (channel + 1) + " note=" + note + " vel=" + normVelocity);
        }
    }

    private void handleNoteOff(int channel, int note) {
        if (channel != DRUM_CHANNEL) {
            voiceAllocator.releaseNote(note);
            LOG.fine("[MIDI] Note OFF: ch=" + (channel + 1) + " note=" + note);
        }
    }

    private void handleControlChange(int ccNumber, int ccValue) {
        Runnable handler = ccMappings.get(ccNumber);
        if (handler != null) {
            handler.run();
        }
        LOG.fine("[MIDI] CC " + ccNumber + " = " + ccValue);
    }

    /**
     * Map a GM drum note number to a {@link DrumTrack}.
     * Returns {@code null} for unmapped notes.
     */
    private static DrumTrack gmNoteToTrack(int note) {
        return switch (note) {
            case GM_KICK  -> DrumTrack.KICK;
            case GM_SNARE -> DrumTrack.SNARE;
            case GM_HAT   -> DrumTrack.HAT;
            case GM_PERC  -> DrumTrack.PERC;
            default       -> null;
        };
    }
}
