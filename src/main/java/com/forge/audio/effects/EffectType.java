package com.forge.audio.effects;

/**
 * Enumerates the effects in the {@link EffectsChain} in signal-flow order.
 *
 * <p>The ordinal of each value corresponds directly to the index inside
 * {@link EffectsChain}'s internal array, so the chain is always ordered:
 * Distortion → Delay → Reverb → Chorus → Compressor → EQ.
 */
public enum EffectType {
    DISTORTION,
    DELAY,
    REVERB,
    CHORUS,
    COMPRESSOR,
    EQ
}
