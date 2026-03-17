package com.forge.audio.sequencer;

import com.forge.model.FillType;
import com.forge.model.Pattern;
import com.forge.model.ProjectState;
import com.forge.model.Section;

/**
 * Manages named sections (INTRO, VERSE, DROP, etc.) for live arrangement.
 *
 * <p>Sections are queued and switch on bar boundaries. Optionally a fill
 * pattern can be injected on the last bar before a transition.
 *
 * <p>Call {@link #onBarEnd()} from the sequencer clock callback once per bar.
 */
public class SectionManager {

    private final ProjectState state;
    private final StepSequencer sequencer;

    private Section activeSection  = null;
    private Section queuedSection  = null;
    private boolean fillBeforeTransition = false;
    private int currentBar = 0;    // bar within the active section
    private FillGenerator fillGenerator;

    // -------------------------------------------------------------------------

    public SectionManager(ProjectState state, StepSequencer sequencer) {
        this.state     = state;
        this.sequencer = sequencer;
        this.fillGenerator = new FillGenerator();
    }

    // -------------------------------------------------------------------------
    // Clock callback
    // -------------------------------------------------------------------------

    /**
     * Called by the sequencer clock on each bar end.
     *
     * <p>Logic:
     * <ol>
     *   <li>Increment the bar counter.</li>
     *   <li>If the active section has finished all its bars, switch to the
     *       queued section (if any) or loop.</li>
     *   <li>If a section is queued and no fill is pending, switch immediately
     *       on the next bar boundary.</li>
     * </ol>
     */
    public void onBarEnd() {
        currentBar++;

        if (activeSection != null && currentBar >= activeSection.barLength) {
            // Section complete — switch to queued section or loop
            if (queuedSection != null) {
                switchToSection(queuedSection);
                queuedSection = null;
                fillBeforeTransition = false;
            } else {
                currentBar = 0; // loop current section
            }
        } else if (queuedSection != null && !fillBeforeTransition) {
            // Queued section switches on next bar boundary
            switchToSection(queuedSection);
            queuedSection = null;
        }
    }

    // -------------------------------------------------------------------------
    // Section queueing
    // -------------------------------------------------------------------------

    /**
     * Queue a section by name.
     *
     * @param name     the section name (case-sensitive)
     * @param withFill if {@code true}, injects a SIMPLE fill pattern on the
     *                 last bar before the transition
     */
    public void queueSection(String name, boolean withFill) {
        for (Section s : state.sections) {
            if (s.name.equals(name)) {
                queuedSection = s;
                fillBeforeTransition = withFill;
                if (withFill && activeSection != null) {
                    // Inject fill on the current bar immediately
                    Pattern source = state.patterns[activeSection.patternIndex];
                    sequencer.setFillActive(true);
                    // Optionally: apply the generated fill steps to the pattern
                    // (advanced usage — for now enabling the fill flag is sufficient
                    //  so FILL_ONLY conditions fire on the next bar)
                }
                return;
            }
        }
        System.out.println("[SectionManager] Section not found: " + name);
    }

    /**
     * Queue a section by its index in {@code state.sections}.
     *
     * @param index zero-based index
     */
    public void queueSectionByIndex(int index) {
        if (index >= 0 && index < state.sections.size()) {
            queuedSection = state.sections.get(index);
        }
    }

    // -------------------------------------------------------------------------
    // Section creation
    // -------------------------------------------------------------------------

    /**
     * Create a new section and add it to the project state.
     *
     * @param name         display name (e.g. "VERSE")
     * @param patternIndex index into {@code state.patterns[]}
     * @param barLength    how many bars the section plays before looping/transitioning
     * @return the newly created {@link Section}
     */
    public Section createSection(String name, int patternIndex, int barLength) {
        Section s = new Section();
        s.name         = name;
        s.patternIndex = patternIndex;
        s.barLength    = barLength;
        state.sections.add(s);
        return s;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Section getActiveSection()  { return activeSection; }
    public int     getCurrentBar()     { return currentBar; }
    public Section getQueuedSection()  { return queuedSection; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void switchToSection(Section section) {
        activeSection = section;
        currentBar    = 0;
        fillBeforeTransition = false;
        sequencer.setFillActive(false);

        // Load the section's pattern
        if (section.patternIndex >= 0 && section.patternIndex < state.patterns.length) {
            sequencer.setPattern(state.patterns[section.patternIndex]);
        }

        System.out.println("[SectionManager] → " + section.name
                + "  pattern=" + section.patternIndex
                + "  bars="    + section.barLength);
    }
}
