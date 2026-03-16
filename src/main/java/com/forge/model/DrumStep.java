package com.forge.model;

import java.util.HashMap;
import java.util.Map;

public class DrumStep {
    public boolean active;
    public double velocity;       // 0-1
    public boolean accent;
    public boolean flam;
    public TrigCondition trigCondition;
    public Map<String, Double> pLocks;

    public DrumStep() {
        this.active = false;
        this.velocity = 0.8;
        this.accent = false;
        this.flam = false;
        this.trigCondition = TrigCondition.ALWAYS;
        this.pLocks = new HashMap<>();
    }
}
