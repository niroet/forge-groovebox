package com.forge.model;

import java.util.HashMap;
import java.util.Map;

public class EffectParams {
    public boolean enabled;
    public Map<String, Double> params;

    public EffectParams() {
        this.enabled = false;
        this.params = new HashMap<>();
    }

    public EffectParams(boolean enabled, Map<String, Double> params) {
        this.enabled = enabled;
        this.params = params != null ? new HashMap<>(params) : new HashMap<>();
    }
}
