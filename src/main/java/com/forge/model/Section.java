package com.forge.model;

import java.util.HashMap;
import java.util.Map;

public class Section {
    public String name;
    public int patternIndex;
    public int barLength;
    public Map<EffectType, EffectParams> fxOverrides;
    public SynthPatch patchOverride; // optional

    public Section() {
        this.name = "Section";
        this.patternIndex = 0;
        this.barLength = 4;
        this.fxOverrides = new HashMap<>();
        this.patchOverride = null;
    }
}
