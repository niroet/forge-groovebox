package com.forge.model;

import java.util.HashMap;
import java.util.Map;

public class SynthStep {
    public boolean active;
    public int midiNote;
    public double velocity;       // 0-1
    public double gateLength;     // 0-1
    public boolean slide;
    public TrigCondition trigCondition;
    public Map<String, Double> pLocks;

    public SynthStep() {
        this.active = false;
        this.midiNote = 60;
        this.velocity = 0.8;
        this.gateLength = 0.5;
        this.slide = false;
        this.trigCondition = TrigCondition.ALWAYS;
        this.pLocks = new HashMap<>();
    }
}
