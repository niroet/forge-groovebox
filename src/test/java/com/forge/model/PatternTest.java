package com.forge.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PatternTest {
    @Test
    void defaultPatternHas16Steps() {
        Pattern p = new Pattern();
        assertEquals(16, p.synthSteps.length);
        assertEquals(16, p.drumSteps[0].length);
        assertEquals(4, p.drumSteps.length);
    }

    @Test
    void drumStepsDefaultInactive() {
        Pattern p = new Pattern();
        for (DrumStep[] track : p.drumSteps)
            for (DrumStep s : track)
                assertFalse(s.active);
    }

    @Test
    void projectStateHas16Patterns() {
        ProjectState state = new ProjectState();
        assertEquals(16, state.patterns.length);
        assertNotNull(state.patterns[0]);
        assertNotNull(state.patterns[15]);
    }
}
