package com.forge.model;

import com.google.gson.Gson;

public class DrumPatch {
    public double pitch;       // Hz
    public double decay;       // seconds
    public double toneNoise;   // 0 = noise, 1 = tone
    public double drive;       // 0-1
    public double snap;        // 0-1
    public double clickLevel;  // 0-1
    public boolean open;       // open/closed (e.g. open hi-hat)

    public DrumPatch() {
        this.pitch = 60.0;
        this.decay = 0.3;
        this.toneNoise = 1.0;
        this.drive = 0.0;
        this.snap = 0.0;
        this.clickLevel = 0.0;
        this.open = false;
    }

    public DrumPatch copy() {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(this), DrumPatch.class);
    }
}
