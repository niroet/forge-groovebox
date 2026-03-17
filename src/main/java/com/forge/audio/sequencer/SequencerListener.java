package com.forge.audio.sequencer;

public interface SequencerListener {
    /** Called when a new step begins. @param stepIndex 0-based step within the pattern (0-15 for 16-step). */
    void onStep(int stepIndex);

    /** Called when the pattern has completed a full bar. @param barNumber 0-based bar count. */
    void onBarEnd(int barNumber);
}
