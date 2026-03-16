# FORGE Groovebox Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a feature-complete AI-powered groovebox in pure Java with DOOM aesthetic, multi-engine synth, drum machine, sequencer, visualizers, and VEGA AI assistant.

**Architecture:** Three-thread model (audio/UI/AI). JSyn for synthesis, JavaFX for UI, LangChain4j + Claude for VEGA. Audio thread is sacred — zero allocations, lock-free ring buffers to UI. All voices pre-allocated. Data model layer shared between audio and UI via thread-safe state objects.

**Tech Stack:** Java 21+, JavaFX 21, JSyn 17.1, iirj, LangChain4j, FXGL, Maven

**Spec:** `docs/superpowers/specs/2026-03-17-forge-groovebox-design.md`

---

## Chunk 1: Foundation + Audio Engine

### Task 1: Maven Project Scaffold

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/forge/ForgeApp.java`
- Create: `src/main/java/module-info.java`
- Create: `src/test/java/com/forge/ForgeAppTest.java`

- [ ] **Step 1: Create pom.xml with all dependencies**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.forge</groupId>
    <artifactId>forge-groovebox</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    <name>FORGE Groovebox</name>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <javafx.version>21.0.2</javafx.version>
    </properties>

    <dependencies>
        <!-- JSyn synthesis engine -->
        <dependency>
            <groupId>com.jsyn</groupId>
            <artifactId>jsyn</artifactId>
            <version>17.1.0</version>
        </dependency>

        <!-- JavaFX -->
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
            <version>${javafx.version}</version>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-graphics</artifactId>
            <version>${javafx.version}</version>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-media</artifactId>
            <version>${javafx.version}</version>
        </dependency>

        <!-- IIR Filters -->
        <dependency>
            <groupId>uk.me.berndporr</groupId>
            <artifactId>iirj</artifactId>
            <version>1.7</version>
        </dependency>

        <!-- LangChain4j + Anthropic -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-anthropic</artifactId>
            <version>0.36.2</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
            <version>0.36.2</version>
        </dependency>

        <!-- FXGL for particles -->
        <dependency>
            <groupId>com.github.almasb</groupId>
            <artifactId>fxgl</artifactId>
            <version>21.1</version>
        </dependency>

        <!-- JSON for project persistence -->
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.11.0</version>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.openjfx</groupId>
                <artifactId>javafx-maven-plugin</artifactId>
                <version>0.0.8</version>
                <configuration>
                    <mainClass>com.forge/com.forge.ForgeApp</mainClass>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create module-info.java**

```java
module com.forge {
    requires com.jsyn;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.media;
    requires com.google.gson;
    requires langchain4j;
    requires langchain4j.anthropic;
    requires java.desktop; // for javax.sound.midi, javax.sound.sampled

    opens com.forge.model to com.google.gson; // Gson reflection for persistence

    exports com.forge;
    exports com.forge.model;
    exports com.forge.audio.engine;
}
```

- [ ] **Step 3: Create minimal ForgeApp.java**

```java
package com.forge;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class ForgeApp extends Application {
    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #080808;");
        Scene scene = new Scene(root, 1200, 700, Color.web("#080808"));
        stage.setTitle("FORGE.EXE — Sound Terminal v2.016");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
```

- [ ] **Step 4: Create a basic test to verify project compiles**

```java
package com.forge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForgeAppTest {
    @Test
    void appClassExists() {
        assertNotNull(ForgeApp.class);
    }
}
```

- [ ] **Step 5: Verify build**

Run: `mvn compile test`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/
git commit -m "feat: Maven project scaffold with all dependencies"
```

---

### Task 2: Data Model — Core Types

**Files:**
- Create: `src/main/java/com/forge/model/EngineType.java`
- Create: `src/main/java/com/forge/model/WaveShape.java`
- Create: `src/main/java/com/forge/model/FilterType.java`
- Create: `src/main/java/com/forge/model/DrumTrack.java`
- Create: `src/main/java/com/forge/model/TrigCondition.java`
- Create: `src/main/java/com/forge/model/FillType.java`
- Create: `src/main/java/com/forge/model/VisualizerMode.java`
- Create: `src/main/java/com/forge/model/ScaleType.java`
- Create: `src/main/java/com/forge/model/SynthPatch.java`
- Create: `src/main/java/com/forge/model/DrumPatch.java`
- Create: `src/main/java/com/forge/model/DrumStep.java`
- Create: `src/main/java/com/forge/model/SynthStep.java`
- Create: `src/main/java/com/forge/model/Pattern.java`
- Create: `src/main/java/com/forge/model/Section.java`
- Create: `src/main/java/com/forge/model/EffectParams.java`
- Create: `src/main/java/com/forge/model/EffectType.java`
- Create: `src/main/java/com/forge/model/ProjectState.java`
- Test: `src/test/java/com/forge/model/PatternTest.java`

- [ ] **Step 1: Create all enum types**

```java
// EngineType.java
package com.forge.model;
public enum EngineType { SUBTRACTIVE, FM, WAVETABLE, GRANULAR }

// WaveShape.java
package com.forge.model;
public enum WaveShape { SINE, SAW, SQUARE, PULSE, TRIANGLE }

// FilterType.java
package com.forge.model;
public enum FilterType { LOW_PASS, HIGH_PASS, BAND_PASS, NOTCH }

// DrumTrack.java
package com.forge.model;
public enum DrumTrack { KICK, SNARE, HAT, PERC }

// TrigCondition.java
package com.forge.model;
public enum TrigCondition { ALWAYS, FIFTY_PERCENT, TWENTY_FIVE_PERCENT, FIRST, SECOND, THIRD, FOURTH, FILL_ONLY }

// FillType.java
package com.forge.model;
public enum FillType { SIMPLE, ROLL, BUILDUP, BREAKDOWN }

// VisualizerMode.java
package com.forge.model;
public enum VisualizerMode { SPECTRUM, OSCILLOSCOPE, SPECTROGRAM, TERRAIN, PARTICLES, VEGA_EYE }

// ScaleType.java
package com.forge.model;
public enum ScaleType { MAJOR, MINOR, DORIAN, PHRYGIAN, LYDIAN, MIXOLYDIAN, AEOLIAN, LOCRIAN, PENTATONIC_MAJOR, PENTATONIC_MINOR, BLUES, CHROMATIC }

// EffectType.java
package com.forge.model;
public enum EffectType { DISTORTION, DELAY, REVERB, CHORUS, COMPRESSOR, EQ }
```

- [ ] **Step 2: Create data classes for patches, steps, patterns**

```java
// SynthPatch.java — all synth parameters in one place
package com.forge.model;
public class SynthPatch {
    public EngineType engine = EngineType.SUBTRACTIVE;
    public WaveShape oscAShape = WaveShape.SAW;
    public double oscADetune = 0.0;    // cents
    public double oscALevel = 0.8;
    public WaveShape oscBShape = WaveShape.SQUARE;
    public double oscBDetune = 0.0;
    public double oscBLevel = 0.5;
    public FilterType filterType = FilterType.LOW_PASS;
    public double filterCutoff = 8000.0; // Hz
    public double filterResonance = 0.0; // 0-1
    public double ampAttack = 0.01, ampDecay = 0.3, ampSustain = 0.7, ampRelease = 0.3;
    public double filterAttack = 0.01, filterDecay = 0.2, filterSustain = 0.5, filterRelease = 0.3;
    // FM-specific
    public double fmRatio = 2.0, fmDepth = 0.5, fmFeedback = 0.0;
    // Wavetable-specific
    public double wavetableMorph = 0.0; // 0-1 table position
    // Granular-specific
    public double grainSize = 50.0; // ms
    public double grainDensity = 20.0; // grains/sec
    public double grainPositionScatter = 0.1;
    public double grainPitchScatter = 0.0;

    public SynthPatch copy() { /* deep copy via Gson or manual */ }
}

// DrumPatch.java — per-drum-track parameters
package com.forge.model;
public class DrumPatch {
    public double pitch = 60.0;    // Hz (kick default)
    public double decay = 0.3;     // seconds
    public double toneNoise = 0.8; // 1.0 = all tone, 0.0 = all noise
    public double drive = 0.0;     // 0-1
    public double snap = 0.5;      // snare attack sharpness
    public double clickLevel = 0.3; // kick click
    public boolean open = false;   // hat open/closed

    public DrumPatch copy() { /* deep copy */ }
}

// DrumStep.java — one step in a drum track
package com.forge.model;
import java.util.HashMap;
import java.util.Map;
public class DrumStep {
    public boolean active = false;
    public double velocity = 0.8;
    public boolean accent = false;
    public boolean flam = false;
    public TrigCondition condition = TrigCondition.ALWAYS;
    public Map<String, Double> pLocks = new HashMap<>(); // param name → override value
}

// SynthStep.java — one step in the synth sequence
package com.forge.model;
import java.util.HashMap;
import java.util.Map;
public class SynthStep {
    public boolean active = false;
    public int midiNote = 60;       // C4
    public double velocity = 0.8;
    public double gateLength = 0.5; // 0-1, fraction of step duration
    public boolean slide = false;   // portamento to next step
    public TrigCondition condition = TrigCondition.ALWAYS;
    public Map<String, Double> pLocks = new HashMap<>();
}

// Pattern.java — one full pattern (synth + 4 drum tracks)
package com.forge.model;
public class Pattern {
    public static final int DEFAULT_STEPS = 16;
    public String name = "A";
    public int synthStepCount = DEFAULT_STEPS;
    public SynthStep[] synthSteps;
    public int[] drumStepCounts = {DEFAULT_STEPS, DEFAULT_STEPS, DEFAULT_STEPS, DEFAULT_STEPS};
    public DrumStep[][] drumSteps; // [4 tracks][N steps]
    public SynthPatch synthPatch;  // optional per-pattern patch override (null = use global)
    public Double bpmOverride;     // optional (null = use global)

    public Pattern() {
        synthSteps = new SynthStep[DEFAULT_STEPS];
        for (int i = 0; i < DEFAULT_STEPS; i++) synthSteps[i] = new SynthStep();
        drumSteps = new DrumStep[4][DEFAULT_STEPS];
        for (int t = 0; t < 4; t++)
            for (int s = 0; s < DEFAULT_STEPS; s++)
                drumSteps[t][s] = new DrumStep();
    }

    public Pattern copy() { /* deep copy */ }
}

// Section.java
package com.forge.model;
import java.util.HashMap;
import java.util.Map;
public class Section {
    public String name;
    public int patternIndex = 0;   // index into pattern bank
    public int barLength = 4;
    public Map<EffectType, EffectParams> fxOverrides = new HashMap<>();
    public SynthPatch patchOverride; // null = use pattern's patch
}

// EffectParams.java
package com.forge.model;
import java.util.HashMap;
import java.util.Map;
public class EffectParams {
    public boolean enabled = false;
    public Map<String, Double> params = new HashMap<>();

    public EffectParams() {}
    public EffectParams(boolean enabled, Map<String, Double> params) {
        this.enabled = enabled;
        this.params = params;
    }
}

// ProjectState.java — everything needed to save/load
package com.forge.model;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
public class ProjectState {
    public double bpm = 128.0;
    public double swing = 50.0;
    public String rootNote = "C";
    public ScaleType scale = ScaleType.MINOR;
    public SynthPatch globalSynthPatch = new SynthPatch();
    public DrumPatch[] drumPatches = {new DrumPatch(), new DrumPatch(), new DrumPatch(), new DrumPatch()};
    public Pattern[] patterns = new Pattern[16];
    public List<Section> sections = new ArrayList<>();
    public Map<EffectType, EffectParams> masterFx = new HashMap<>();
    public int activePatternIndex = 0;
    public int activeSectionIndex = -1; // -1 = no section active, pattern mode

    public ProjectState() {
        for (int i = 0; i < 16; i++) patterns[i] = new Pattern();
        // Set default drum patches per voice
        drumPatches[0].pitch = 60.0; drumPatches[0].decay = 0.3; drumPatches[0].toneNoise = 1.0; // kick
        drumPatches[1].pitch = 200.0; drumPatches[1].decay = 0.15; drumPatches[1].toneNoise = 0.5; // snare
        drumPatches[2].pitch = 8000.0; drumPatches[2].decay = 0.05; drumPatches[2].toneNoise = 0.0; // hat
        drumPatches[3].pitch = 400.0; drumPatches[3].decay = 0.1; drumPatches[3].toneNoise = 0.7; // perc
    }
}
```

- [ ] **Step 3: Write pattern test**

```java
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
```

- [ ] **Step 4: Run tests**

Run: `mvn test`
Expected: 3 tests pass

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: data model — enums, patches, patterns, sections, project state"
```

---

### Task 3: Audio Engine — JSyn Bootstrap + Ring Buffer

**Files:**
- Create: `src/main/java/com/forge/audio/engine/AudioEngine.java`
- Create: `src/main/java/com/forge/audio/engine/AudioRingBuffer.java`
- Create: `src/main/java/com/forge/audio/engine/AnalysisBus.java`
- Test: `src/test/java/com/forge/audio/engine/AudioRingBufferTest.java`
- Test: `src/test/java/com/forge/audio/engine/AudioEngineTest.java`

- [ ] **Step 1: Write AudioRingBuffer — lock-free SPSC ring buffer for audio data**

```java
package com.forge.audio.engine;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single-producer, single-consumer lock-free ring buffer for passing audio
 * samples from the audio thread to the UI thread.
 * Producer: audio thread (write). Consumer: UI thread (read).
 */
public class AudioRingBuffer {
    private final float[] buffer;
    private final int capacity;
    private final AtomicInteger writePos = new AtomicInteger(0);
    private final AtomicInteger readPos = new AtomicInteger(0);

    public AudioRingBuffer(int capacity) {
        // Round up to power of 2 for fast modulo
        this.capacity = Integer.highestOneBit(capacity - 1) << 1;
        this.buffer = new float[this.capacity];
    }

    /** Called from audio thread only. */
    public void write(float[] data, int offset, int length) { /* ... */ }

    /** Called from UI thread only. Returns number of samples read. */
    public int read(float[] dest, int offset, int maxLength) { /* ... */ }

    public int available() { /* ... */ }
}
```

**IMPORTANT concurrency note for AnalysisBus:** The `volatile` array fields use a swap pattern — audio thread creates a NEW array each update cycle and assigns the reference. Never mutate an existing array in place. The UI thread reads the reference atomically. This prevents torn reads without locks.
```

- [ ] **Step 2: Write ring buffer test**

```java
package com.forge.audio.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioRingBufferTest {
    @Test
    void writeAndReadRoundTrip() {
        AudioRingBuffer rb = new AudioRingBuffer(256);
        float[] data = {1.0f, 2.0f, 3.0f, 4.0f};
        rb.write(data, 0, 4);
        float[] out = new float[4];
        int read = rb.read(out, 0, 4);
        assertEquals(4, read);
        assertArrayEquals(data, out);
    }

    @Test
    void emptyBufferReadsZero() {
        AudioRingBuffer rb = new AudioRingBuffer(256);
        float[] out = new float[4];
        assertEquals(0, rb.read(out, 0, 4));
    }
}
```

- [ ] **Step 3: Run tests, verify pass**

Run: `mvn test -pl . -Dtest=AudioRingBufferTest`

- [ ] **Step 4: Write AnalysisBus — shared audio analysis data for visualizers**

```java
package com.forge.audio.engine;

/**
 * Holds analysis data computed from the audio stream.
 * Written by the audio thread, read by the UI thread.
 * Uses volatile fields for safe cross-thread reads (no locks).
 */
public class AnalysisBus {
    public static final int FFT_SIZE = 256;
    public static final int FFT_BINS = FFT_SIZE / 2;

    private volatile float[] fftMagnitudes = new float[FFT_BINS];
    private volatile float[] waveformSamples = new float[FFT_SIZE];
    private volatile float rmsEnergy = 0f;
    private volatile float peakAmplitude = 0f;
    private volatile float spectralCentroid = 0f;
    private volatile boolean beatDetected = false;

    // Sequencer-driven beat info (set by clock, not audio analysis)
    private volatile boolean clockBeat = false;
    private volatile int clockStep = 0;

    // Setters (audio thread)
    public void setFftMagnitudes(float[] mags) { this.fftMagnitudes = mags; }
    public void setWaveformSamples(float[] samples) { this.waveformSamples = samples; }
    public void setRmsEnergy(float rms) { this.rmsEnergy = rms; }
    public void setPeakAmplitude(float peak) { this.peakAmplitude = peak; }
    public void setSpectralCentroid(float centroid) { this.spectralCentroid = centroid; }
    public void setBeatDetected(boolean beat) { this.beatDetected = beat; }
    public void setClockBeat(boolean beat) { this.clockBeat = beat; }
    public void setClockStep(int step) { this.clockStep = step; }

    // Getters (UI thread)
    public float[] getFftMagnitudes() { return fftMagnitudes; }
    public float[] getWaveformSamples() { return waveformSamples; }
    public float getRmsEnergy() { return rmsEnergy; }
    public float getPeakAmplitude() { return peakAmplitude; }
    public float getSpectralCentroid() { return spectralCentroid; }
    public boolean isBeatDetected() { return beatDetected; }
    public boolean isClockBeat() { return clockBeat; }
    public int getClockStep() { return clockStep; }
}
```

- [ ] **Step 5: Write AudioEngine — JSyn initialization, start/stop, master output**

```java
package com.forge.audio.engine;

import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.unitgen.LineOut;
import com.jsyn.unitgen.MixerMono;

/**
 * Central audio engine. Owns the JSyn synthesizer, master mixer,
 * and output. All synth/drum voices connect to the mixer.
 */
public class AudioEngine {
    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 256;

    private final Synthesizer synth;
    private final LineOut lineOut;
    private final MixerMono masterMixer;
    private final AnalysisBus analysisBus;
    private final AudioRingBuffer waveformBuffer;

    private boolean running = false;

    public AudioEngine() {
        this.synth = JSyn.createSynthesizer();
        this.lineOut = new LineOut();
        this.masterMixer = new MixerMono(16); // 8 synth + 4 drum + headroom
        this.analysisBus = new AnalysisBus();
        this.waveformBuffer = new AudioRingBuffer(4096);

        synth.add(lineOut);
        synth.add(masterMixer);
        masterMixer.output.connect(0, lineOut.input, 0);
        masterMixer.output.connect(0, lineOut.input, 1);
    }

    public void start() {
        synth.start(SAMPLE_RATE);
        lineOut.start();
        running = true;
    }

    public void stop() {
        lineOut.stop();
        synth.stop();
        running = false;
    }

    public Synthesizer getSynth() { return synth; }
    public MixerMono getMasterMixer() { return masterMixer; }
    public AnalysisBus getAnalysisBus() { return analysisBus; }
    public AudioRingBuffer getWaveformBuffer() { return waveformBuffer; }
    public boolean isRunning() { return running; }
    public int getSampleRate() { return SAMPLE_RATE; }
}
```

- [ ] **Step 6: Write AudioEngine test**

```java
package com.forge.audio.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioEngineTest {
    @Test
    void engineStartsAndStops() {
        AudioEngine engine = new AudioEngine();
        assertFalse(engine.isRunning());
        engine.start();
        assertTrue(engine.isRunning());
        engine.stop();
        assertFalse(engine.isRunning());
    }

    @Test
    void analysisBusExists() {
        AudioEngine engine = new AudioEngine();
        assertNotNull(engine.getAnalysisBus());
    }
}
```

- [ ] **Step 7: Run all tests**

Run: `mvn test`
Expected: All pass

- [ ] **Step 8: Commit**

```bash
git add src/
git commit -m "feat: audio engine — JSyn bootstrap, ring buffer, analysis bus"
```

---

### Task 4: Subtractive Synth Voice

**Files:**
- Create: `src/main/java/com/forge/audio/synth/SubtractiveSynthVoice.java`
- Create: `src/main/java/com/forge/audio/synth/SynthVoice.java` (interface)
- Create: `src/main/java/com/forge/audio/synth/VoiceAllocator.java`
- Test: `src/test/java/com/forge/audio/synth/SubtractiveSynthVoiceTest.java`
- Test: `src/test/java/com/forge/audio/synth/VoiceAllocatorTest.java`

- [ ] **Step 1: Define SynthVoice interface**

```java
package com.forge.audio.synth;

import com.forge.model.SynthPatch;
import com.jsyn.Synthesizer;
import com.jsyn.unitgen.UnitGenerator;

public interface SynthVoice {
    void init(Synthesizer synth);
    void noteOn(int midiNote, double velocity);
    void noteOff();
    void applyPatch(SynthPatch patch);
    boolean isActive();
    UnitGenerator getOutput();
}
```

- [ ] **Step 2: Implement SubtractiveSynthVoice using JSyn units**

Two oscillators (saw/square/etc) → mixer → Moog-style 24dB/oct ladder filter → amplitude envelope → output. Use JSyn's built-in `SawtoothOscillatorBL`, `SquareOscillatorBL`, `PulseOscillatorBL` (for pulse width), `RedNoise`, `EnvelopeDAHDSR`. **Do NOT use JSyn's FilterStateVariable (only 12dB/oct)**. Instead implement a custom `MoogLadderFilter` as a JSyn `UnitFilter` subclass — 4 cascaded one-pole lowpass sections with resonance feedback. This is critical for sound quality (spec principle #7). Wire them together in `init()`. `noteOn` sets frequency from MIDI note and triggers envelopes. `noteOff` triggers release phase. Pulse width is controllable per-oscillator via `PulseOscillatorBL.width`.

Reference: Välimäki & Huovilainen (2006) "Oscillator and Filter Algorithms for Virtual Analog Synthesis" for the Moog ladder topology.

- [ ] **Step 3: Write SubtractiveSynthVoice test**

Test that: a voice can be created, noteOn sets it active, noteOff triggers release, applyPatch changes parameters without crashing.

- [ ] **Step 4: Implement VoiceAllocator**

```java
package com.forge.audio.synth;

/**
 * Manages 8 pre-allocated SynthVoice instances.
 * Allocates on noteOn, recycles oldest on voice exhaustion.
 */
public class VoiceAllocator {
    private final SynthVoice[] voices;
    private final long[] noteOnTimes;
    private int nextVoice = 0;

    public VoiceAllocator(SynthVoice[] voices) { /* ... */ }
    public SynthVoice allocate(int midiNote, double velocity) { /* oldest-release-first, then oldest-held */ }
    public void releaseNote(int midiNote) { /* find voice playing this note, call noteOff */ }
    public void releaseAll() { /* panic: noteOff all voices */ }
}
```

- [ ] **Step 5: Write VoiceAllocator test**

Test: allocate 8 voices, verify all active. Allocate 9th, verify oldest is stolen. Release a note, verify voice becomes available.

- [ ] **Step 6: Run all tests, verify pass**

Run: `mvn test`

- [ ] **Step 7: Commit**

```bash
git add src/
git commit -m "feat: subtractive synth voice with voice allocator"
```

---

### Task 5: Drum Synth Voices

**Files:**
- Create: `src/main/java/com/forge/audio/drums/DrumVoice.java` (interface)
- Create: `src/main/java/com/forge/audio/drums/KickVoice.java`
- Create: `src/main/java/com/forge/audio/drums/SnareVoice.java`
- Create: `src/main/java/com/forge/audio/drums/HatVoice.java`
- Create: `src/main/java/com/forge/audio/drums/PercVoice.java`
- Create: `src/main/java/com/forge/audio/drums/DrumEngine.java`
- Test: `src/test/java/com/forge/audio/drums/DrumEngineTest.java`

- [ ] **Step 1: Define DrumVoice interface**

```java
package com.forge.audio.drums;

import com.forge.model.DrumPatch;
import com.jsyn.Synthesizer;
import com.jsyn.unitgen.UnitGenerator;

public interface DrumVoice {
    void init(Synthesizer synth);
    void trigger(double velocity);
    void applyPatch(DrumPatch patch);
    UnitGenerator getOutput();
}
```

- [ ] **Step 2: Implement KickVoice**

Sine oscillator + pitch envelope (LineOut from high freq to low freq, exponential-ish). Noise burst click mixed in. Amplitude envelope (fast attack, adjustable decay). Tanh waveshaper for drive. All JSyn units, wired in init().

- [ ] **Step 3: Implement SnareVoice**

Sine body at tunable pitch + white noise through bandpass filter. Mix controlled by toneNoise param. Short amp envelope with snap control (envelope curve sharpness).

- [ ] **Step 4: Implement HatVoice**

6 square oscillators at detuned frequencies (e.g., 302, 375, 438, 523, 575, 627 Hz — classic metallic ratios). Summed and high-pass filtered. Very short envelope for closed, longer for open.

- [ ] **Step 5: Implement PercVoice**

Triangle/sine oscillator with pitch envelope sweep. Noise mix for texture. Adjustable to produce toms, rimshots, cowbell, claves by varying pitch/sweep/decay/tone.

- [ ] **Step 6: Implement DrumEngine — holds all 4 voices, connects to mixer**

```java
package com.forge.audio.drums;

import com.forge.model.DrumTrack;
import com.forge.model.DrumPatch;

public class DrumEngine {
    private final DrumVoice[] voices = new DrumVoice[4]; // KICK, SNARE, HAT, PERC

    public DrumEngine(AudioEngine engine) { /* init all 4, connect to master mixer */ }
    public void trigger(DrumTrack track, double velocity) { voices[track.ordinal()].trigger(velocity); }
    public void applyPatch(DrumTrack track, DrumPatch patch) { voices[track.ordinal()].applyPatch(patch); }
}
```

- [ ] **Step 7: Write DrumEngine test**

Test: trigger each voice, verify no exceptions. Apply patches, verify parameters change.

- [ ] **Step 8: Run all tests, verify pass**

Run: `mvn test`

- [ ] **Step 9: Integration test — play a kick drum through speakers**

Create a small main method or test that starts AudioEngine, creates DrumEngine, triggers a kick, waits 500ms, stops. Verify you hear a kick drum sound. This is the "first sound" milestone.

- [ ] **Step 10: Commit**

```bash
git add src/
git commit -m "feat: drum synth engine — kick, snare, hat, perc voices"
```

---

### Task 6: Sequencer Clock + Step Sequencer

**Files:**
- Create: `src/main/java/com/forge/audio/sequencer/SequencerClock.java`
- Create: `src/main/java/com/forge/audio/sequencer/StepSequencer.java`
- Create: `src/main/java/com/forge/audio/sequencer/SequencerListener.java`
- Test: `src/test/java/com/forge/audio/sequencer/SequencerClockTest.java`
- Test: `src/test/java/com/forge/audio/sequencer/StepSequencerTest.java`

- [ ] **Step 1: Implement SequencerClock**

Runs as a JSyn UnitGenerator so it's sample-accurate. Counts samples per tick based on BPM and PPQN (96). Fires callbacks on each 16th-note step. Handles swing by delaying even-numbered steps.

```java
package com.forge.audio.sequencer;

public class SequencerClock {
    private static final int PPQN = 96;
    private double bpm = 128.0;
    private double swing = 50.0;
    private boolean playing = false;
    private long sampleCount = 0;
    private int currentTick = 0;
    private int currentStep = 0; // 0-15 (16th notes)
    private SequencerListener listener;

    public void setSampleRate(int sampleRate) { /* recalculate samples per tick */ }
    public void setBpm(double bpm) { /* ... */ }
    public void setSwing(double swing) { /* 50-75, offsets even 16th notes */ }
    public void play() { playing = true; sampleCount = 0; currentTick = 0; currentStep = 0; }
    public void stop() { playing = false; }
    public boolean isPlaying() { return playing; }

    /** Called once per sample from audio thread */
    public void tick() { /* increment sampleCount, check if new step, fire listener */ }
}
```

- [ ] **Step 2: Define SequencerListener**

```java
package com.forge.audio.sequencer;

public interface SequencerListener {
    void onStep(int stepIndex); // 0-based step number
    void onBarEnd(int barNumber);
}
```

- [ ] **Step 3: Implement StepSequencer**

Listens to clock, reads current Pattern, triggers drum voices and synth notes on active steps. Handles velocity, accent, conditional triggers, and P-locks.

```java
package com.forge.audio.sequencer;

import com.forge.model.*;
import com.forge.audio.drums.DrumEngine;
import com.forge.audio.synth.VoiceAllocator;

public class StepSequencer implements SequencerListener {
    private Pattern currentPattern;
    private final DrumEngine drumEngine;
    private final VoiceAllocator synthVoices;
    private boolean fillActive = false;
    private int loopCount = 0;

    @Override
    public void onStep(int stepIndex) {
        // For each drum track: check if step is active, evaluate condition, apply P-locks, trigger
        // For synth track: check if step is active, evaluate condition, apply P-locks, noteOn/noteOff
    }

    public void setPattern(Pattern pattern) { this.currentPattern = pattern; }
    public void setFillActive(boolean active) { this.fillActive = active; }
}
```

- [ ] **Step 4: Write SequencerClock test**

Test: set BPM to 120, verify samples-per-step calculation is correct (44100 / (120/60) / 4 = 5512.5 samples per 16th note). Test swing offset calculation.

- [ ] **Step 5: Write StepSequencer test**

Test: create a pattern with kick on steps 0,4,8,12 (four-on-the-floor). Mock the DrumEngine. Walk through 16 steps, verify trigger is called exactly 4 times on the correct steps.

Test: conditional triggers — step with FIFTY_PERCENT condition fires approximately 50% of the time over many iterations.

Test: fill-only steps don't fire when fillActive=false, do fire when true.

- [ ] **Step 6: Run tests**

Run: `mvn test`

- [ ] **Step 7: Integration test — play a 4-on-the-floor beat**

Start AudioEngine + DrumEngine + SequencerClock + StepSequencer. Create a pattern with kick on 0,4,8,12 and hat on every step. Hit play. Verify you hear a basic beat. This is the "first groove" milestone.

- [ ] **Step 8: Commit**

```bash
git add src/
git commit -m "feat: sequencer clock and step sequencer with conditional triggers"
```

---

### Task 7: Effects Chain

**Files:**
- Create: `src/main/java/com/forge/audio/effects/Effect.java` (interface)
- Create: `src/main/java/com/forge/audio/effects/Distortion.java`
- Create: `src/main/java/com/forge/audio/effects/StereoDelay.java`
- Create: `src/main/java/com/forge/audio/effects/FreeverbReverb.java`
- Create: `src/main/java/com/forge/audio/effects/Chorus.java`
- Create: `src/main/java/com/forge/audio/effects/Compressor.java`
- Create: `src/main/java/com/forge/audio/effects/ParametricEQ.java`
- Create: `src/main/java/com/forge/audio/effects/EffectsChain.java`
- Test: `src/test/java/com/forge/audio/effects/DistortionTest.java`
- Test: `src/test/java/com/forge/audio/effects/EffectsChainTest.java`

- [ ] **Step 1: Define Effect interface**

```java
package com.forge.audio.effects;

import com.jsyn.Synthesizer;
import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;

public interface Effect {
    void init(Synthesizer synth);
    UnitInputPort getInput();
    UnitOutputPort getOutput();
    void setEnabled(boolean enabled);
    boolean isEnabled();
    void setParam(String name, double value);
    double getParam(String name);
}
```

- [ ] **Step 2: Implement Distortion — tanh waveshaper**

JSyn custom UnitGenerator that applies `Math.tanh(input * drive)` per sample. Params: drive (1-20), mix (0-1 dry/wet).

- [ ] **Step 3: Implement StereoDelay — ping-pong with BPM sync**

Circular buffer delay. Left delay = time, right delay = time * 0.75 for ping-pong. Feedback loop with gain. Mix control. Time in samples calculated from BPM-synced note values.

- [ ] **Step 4: Implement FreeverbReverb**

Schroeder reverb: 8 parallel comb filters (with low-pass in feedback) → 4 series all-pass filters. Params: room size, damping, mix, width. This is a well-documented algorithm — implement from the Freeverb reference.

- [ ] **Step 5: Implement Chorus — 2 modulated delay lines**

Two delay lines with LFO-modulated delay times (sine LFO, 0.1-5Hz, modulating delay from 5-30ms). Params: rate, depth, mix.

- [ ] **Step 6: Implement Compressor**

RMS envelope follower → gain computer (threshold, ratio, knee) → smooth gain (attack/release). Params: threshold (-60 to 0 dB), ratio (1:1 to 20:1), attack (1-100ms), release (10-500ms), makeup gain (0-20dB).

- [ ] **Step 7: Implement ParametricEQ**

3 biquad filters in series: low shelf, mid parametric peak, high shelf. Use iirj library for the filter implementations. Params: low gain, mid gain, mid freq, high gain.

- [ ] **Step 8: Implement EffectsChain — serial chain of all effects**

```java
package com.forge.audio.effects;

public class EffectsChain {
    private final Effect[] chain; // [DIST, DELAY, REVERB, CHORUS, COMP, EQ]

    public EffectsChain(Synthesizer synth) {
        // Create all effects, wire input→output in series
        // When an effect is disabled, it passes signal through unchanged
    }

    public void connectInput(UnitOutputPort source) { /* ... */ }
    public UnitOutputPort getOutput() { /* ... */ }
    public Effect getEffect(EffectType type) { /* ... */ }
}
```

- [ ] **Step 9: Write Distortion test**

Test: input a sine wave, apply drive, verify output is clipped (peak < input peak when driven hard). Verify bypass passes signal unchanged.

- [ ] **Step 10: Write EffectsChain test**

Test: chain connects all effects in order. Disabling an effect bypasses it. Signal flows from input to output.

- [ ] **Step 11: Run tests**

Run: `mvn test`

- [ ] **Step 12: Commit**

```bash
git add src/
git commit -m "feat: effects chain — distortion, delay, reverb, chorus, compressor, EQ"
```

---

## Chunk 2: UI Framework + Panels

### Task 8: DOOM Theme + UI Shell

**Files:**
- Create: `src/main/java/com/forge/ui/theme/ForgeTheme.java`
- Create: `src/main/java/com/forge/ui/theme/ForgeColors.java`
- Create: `src/main/java/com/forge/ui/theme/CrtOverlay.java`
- Create: `src/main/resources/css/forge-theme.css`
- Modify: `src/main/java/com/forge/ForgeApp.java`

- [ ] **Step 1: Create ForgeColors constants**

```java
package com.forge.ui.theme;

import javafx.scene.paint.Color;

public final class ForgeColors {
    public static final Color ARGENT_RED = Color.web("#ff2200");
    public static final Color ARGENT_ORANGE = Color.web("#ff6600");
    public static final Color ARGENT_AMBER = Color.web("#ff8800");
    public static final Color ARGENT_YELLOW = Color.web("#ffcc00");
    public static final Color VEGA_BLUE = Color.web("#44bbff");
    public static final Color VEGA_CYAN = Color.web("#88ccee");
    public static final Color DIVINE_GOLD = Color.web("#ffdd44");
    public static final Color BG_VOID = Color.web("#080808");
    public static final Color BG_PANEL = Color.web("#0a0a0a");
    public static final Color BG_INSET = Color.web("#050505");
    public static final Color BORDER_DIM = Color.web("#222222");
    public static final Color BORDER_ACTIVE = Color.web("#333333");
    public static final Color TEXT_DIM = Color.web("#666666");
    public static final Color TEXT_LABEL = Color.web("#888888");
    private ForgeColors() {}
}
```

- [ ] **Step 2: Create forge-theme.css — global JavaFX styling**

Dark background, monospace fonts, scrollbar styling, button/label defaults matching the DOOM aesthetic. Win98-style beveled borders on interactive elements.

- [ ] **Step 3: Create CrtOverlay — scanlines + vignette canvas**

```java
package com.forge.ui.theme;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.RadialGradient;

/**
 * Transparent Canvas overlay drawn on top of the entire scene.
 * Renders CRT scanlines and edge vignette. Toggleable.
 */
public class CrtOverlay extends Canvas {
    private boolean enabled = true;

    public CrtOverlay(double width, double height) { /* ... */ }
    public void setEnabled(boolean enabled) { this.enabled = enabled; redraw(); }
    public void redraw() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());
        if (!enabled) return;
        // Draw horizontal scanlines every 2px
        // Draw radial vignette gradient
    }
}
```

- [ ] **Step 4: Update ForgeApp — 3-panel layout with title bar, menu, status bar**

Build the full Win98-styled layout: custom title bar (not OS native), menu bar, left/center/right panels, status bar. Use BorderPane (top=titlebar+menu, center=3-panel SplitPane, bottom=statusbar). Apply forge-theme.css. Add CrtOverlay on top of everything via StackPane.

- [ ] **Step 5: Verify app launches with DOOM theme**

Run: `mvn javafx:run`
Expected: Dark window with title "FORGE.EXE", Win98-style title bar, 3 empty panels, status bar, scanlines.

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat: DOOM theme, CRT overlay, 3-panel UI shell"
```

---

### Task 9: Custom Controls — Knob, StepButton, Fader, WaveformDisplay

**Files:**
- Create: `src/main/java/com/forge/ui/controls/ForgeKnob.java`
- Create: `src/main/java/com/forge/ui/controls/StepButton.java`
- Create: `src/main/java/com/forge/ui/controls/ForgeFader.java`
- Create: `src/main/java/com/forge/ui/controls/WaveformDisplay.java`
- Create: `src/main/java/com/forge/ui/controls/ForgeDropdown.java`

- [ ] **Step 1: Implement ForgeKnob**

Extends Region (or Canvas). Circular knob with glowing border. Drag up = increase, drag down = decrease. Shift+drag for fine control. Value range configurable. Accent color per knob. Shows value tooltip on hover. Fires `valueProperty` change events.

- [ ] **Step 2: Implement StepButton**

Extends Region. Square toggle. Visual states: off (dark), on (track color, brightness=velocity), playing (white flash), P-locked (dot overlay), conditional (small icon). Click to toggle on/off. Right-click for context menu (condition, accent, flam).

- [ ] **Step 3: Implement ForgeFader**

Vertical fader. LED-style fill from bottom. Value range 0-1. Drag to change. Used for mixer levels.

- [ ] **Step 4: Implement WaveformDisplay**

Small Canvas that draws a single cycle of a waveform (saw, square, sine, etc.). Updates when wave type changes. Used in the oscillator section.

- [ ] **Step 5: Implement ForgeDropdown**

Styled ComboBox matching the dark theme with Win98 beveled border. For engine select, filter type, etc.

- [ ] **Step 6: Visual test — create a test layout with all controls**

Create a temporary test scene that shows each control. Verify they render correctly, respond to input, fire events.

- [ ] **Step 7: Commit**

```bash
git add src/
git commit -m "feat: custom controls — knob, step button, fader, waveform display, dropdown"
```

---

### Task 10: Synth Panel UI

**Files:**
- Create: `src/main/java/com/forge/ui/panels/SynthPanel.java`
- Create: `src/main/java/com/forge/ui/panels/OscillatorSection.java`
- Create: `src/main/java/com/forge/ui/panels/FilterSection.java`
- Create: `src/main/java/com/forge/ui/panels/EnvelopeSection.java`
- Create: `src/main/java/com/forge/ui/panels/FxChainSection.java`

- [ ] **Step 1: Implement SynthPanel — left panel container**

VBox with engine tabs at top (SUB|FM|WAVE|GRAIN), then stacked sections: Oscillators, Filter, Envelope, FX Chain. Each section has the "◆ SECTION NAME" header in argent colors. Switching engine tabs reconfigures which knobs are visible.

- [ ] **Step 2: Implement OscillatorSection**

Two oscillator sub-panels side by side. Each has: waveform dropdown, waveform display, 3 knobs (pitch/shape/level for sub, ratio/depth/feedback for FM, morph for wavetable, grain params for granular). Wired to update SynthPatch model on change.

- [ ] **Step 3: Implement FilterSection**

Filter type buttons (LP/HP/BP/NOTCH), filter response display (Canvas drawing the curve), cutoff + resonance knobs. Left accent border.

- [ ] **Step 4: Implement EnvelopeSection**

ADSR display (Canvas drawing the envelope shape, updates live). 4 vertical faders for A/D/S/R. Color-coded per stage.

- [ ] **Step 5: Implement FxChainSection**

3x2 grid of effect mini-panels. Each has: name label, on/off indicator, one knob (primary param). Click to expand/focus for full params. Active effects glow.

- [ ] **Step 6: Wire SynthPanel to AudioEngine**

Knob changes → update SynthPatch model → call `applyPatch()` on the active synth voice. This is the model→audio binding layer.

- [ ] **Step 7: Verify synth panel renders and controls audio**

Run app. Verify left panel shows oscillators, filter, envelope, FX. Tweak a knob, hear sound change (need keyboard input wired — can use a test button).

- [ ] **Step 8: Commit**

```bash
git add src/
git commit -m "feat: synth panel UI with oscillator, filter, envelope, FX sections"
```

---

### Task 11: Drum Panel + Transport UI

**Files:**
- Create: `src/main/java/com/forge/ui/panels/DrumPanel.java`
- Create: `src/main/java/com/forge/ui/panels/DrumGrid.java`
- Create: `src/main/java/com/forge/ui/panels/TransportBar.java`
- Create: `src/main/java/com/forge/ui/panels/SectionTriggers.java`

- [ ] **Step 1: Implement DrumGrid**

4 rows × 16 columns of StepButtons. Track labels on the left (KICK/SNARE/HAT/PERC) in track colors. Mute/Solo buttons per row. Pattern bank selector (A-P) at top. Step number indicators. Current-step highlight animates when playing.

- [ ] **Step 2: Implement TransportBar**

HBox with: [▶ RIP] [⏹ HALT] [● ARM] buttons, BPM display (editable), swing knob, section trigger buttons. DOOM-styled. BPM display shows tempo with argent glow.

- [ ] **Step 3: Implement SectionTriggers**

Row of section buttons (INTRO/VERSE/DROP/etc). Active section glows. Click to queue. Shift+click for instant switch. Shows section name and progress (bar X/Y).

- [ ] **Step 4: Implement DrumPanel — center-bottom panel**

Contains DrumGrid + TransportBar + SectionTriggers. Wired to StepSequencer and DrumEngine. Click steps → update Pattern model → sequencer reads on next loop.

- [ ] **Step 5: Wire transport controls**

Play button → SequencerClock.play(). Stop → SequencerClock.stop(). BPM changes → SequencerClock.setBpm(). Arm → toggle recording mode.

- [ ] **Step 6: Wire step grid to pattern**

Click a step → toggle drumSteps[track][step].active. Update velocity from click position. Sequencer reads pattern data directly (no copy needed — single writer).

- [ ] **Step 7: Playback position animation**

AnimationTimer reads AnalysisBus.getClockStep() at 60fps, highlights the corresponding column in the grid with a bright flash.

- [ ] **Step 8: Verify drum machine works end-to-end**

Run app. Click steps in the grid. Press RIP (play). Hear drum pattern. See step highlight move. Change BPM. Toggle mute. This is the "working groovebox" milestone.

- [ ] **Step 9: Commit**

```bash
git add src/
git commit -m "feat: drum grid, transport bar, section triggers — working drum machine"
```

---

### Task 12: Synth Sequence Grid UI

**Files:**
- Create: `src/main/java/com/forge/ui/panels/SynthSequenceGrid.java`

- [ ] **Step 1: Implement SynthSequenceGrid**

Sits above or below the drum grid (toggle-able view). 16 columns matching drum grid. Each column shows the note name (e.g., "C4") and velocity bar. Click a step to select it, then play a keyboard key to enter the note. Arrow keys move selection. Visual: active steps show note name in argent amber, selected step has bright border. Gate length shown as horizontal bar width within the step. Slide indicator: small arrow connecting to next step.

- [ ] **Step 2: Wire P-lock interaction**

Hold a synth step + drag any synth knob = P-lock that parameter on that step. P-locked steps get a colored dot overlay (same as drum P-locks).

- [ ] **Step 3: Wire gate length and slide**

Ctrl+drag on a step to adjust gate length (visual bar width changes). Shift+click to toggle slide to next step.

- [ ] **Step 4: Verify synth step editing works**

Enter a melody via keyboard into the grid. Hit play. Hear the melody play back with correct notes, velocities, gate lengths.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: synth sequence grid with note entry, P-locks, gate, slide"
```

---

### Task 13: Keyboard Input + Note Playing

**Files:**
- Create: `src/main/java/com/forge/ui/KeyboardHandler.java`
- Modify: `src/main/java/com/forge/ForgeApp.java`

- [ ] **Step 1: Implement KeyboardHandler**

Maps keyboard events to actions. Two rows of note keys (ZXCVBNM and ASDFGHJKL). Q/W for octave shift. Space for play/stop. Esc for halt. R for arm. F for fill (hold). 1-9 for sections. F1-F6 for visualizers. Ctrl+Z/Shift+Z for undo/redo. Ctrl+S for save. Ctrl+1-4 for drum mutes.

- [ ] **Step 2: Wire note keys to VoiceAllocator**

Key press → `voiceAllocator.allocate(midiNote, velocity)`. Key release → `voiceAllocator.releaseNote(midiNote)`. Octave tracking for Q/W shift.

- [ ] **Step 3: Wire transport keys**

Space → toggle play/stop. Esc → stop + release all notes. R → toggle arm.

- [ ] **Step 4: Verify playing synth with keyboard**

Run app. Press keys on ASDF row. Hear synth notes. Change octave with Q/W. Press Space to start drum pattern. Play synth over drums.

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: keyboard input — note playing, transport, shortcuts"
```

---

## Chunk 3: Additional Engines + Visualizers

### Task 13: FM Synth Voice

**Files:**
- Create: `src/main/java/com/forge/audio/synth/FmSynthVoice.java`
- Test: `src/test/java/com/forge/audio/synth/FmSynthVoiceTest.java`

- [ ] **Step 1: Implement FmSynthVoice**

Carrier oscillator (sine/saw) modulated by modulator oscillator. Modulator frequency = carrier freq × ratio. Modulator output × depth → added to carrier frequency input. Feedback: modulator's output fed back to its own phase. Then through shared filter → envelope → output. Implements `SynthVoice` interface.

- [ ] **Step 2: Write test — FM produces different timbre than subtractive**

Create FM voice with ratio=2.0, depth=500. Generate samples. Verify output has harmonic content different from a simple sine (check that multiple frequency components exist via zero-crossing analysis or similar).

- [ ] **Step 3: Wire to engine tab switching**

When user clicks "FM" tab in SynthPanel, all voices switch to FmSynthVoice. Knob labels update (PITCH/RATIO/DEPTH instead of PITCH/SHAPE/LVL).

- [ ] **Step 4: Run tests, commit**

```bash
git add src/
git commit -m "feat: FM synthesis engine"
```

---

### Task 14: Wavetable Synth Voice

**Files:**
- Create: `src/main/java/com/forge/audio/synth/WavetableSynthVoice.java`
- Create: `src/main/java/com/forge/audio/synth/WavetableBank.java`
- Create: `src/main/resources/wavetables/basic.json`
- Test: `src/test/java/com/forge/audio/synth/WavetableSynthVoiceTest.java`

- [ ] **Step 1: Create WavetableBank — loads and stores single-cycle waveforms**

Each wavetable = float[256] (256 samples per cycle). Bank holds multiple tables. Morph knob interpolates between adjacent tables (crossfade). Ship basic set: sine, saw, square, triangle, plus a few interesting ones (vocal, digital, PWM series).

- [ ] **Step 2: Implement WavetableSynthVoice**

Reads from wavetable at playback rate determined by pitch. Phase accumulator indexes into table. Linear interpolation between samples. Morph control crossfades between tables. Then filter → envelope → output.

- [ ] **Step 3: Write test, verify morph produces smooth transitions**

- [ ] **Step 4: Run tests, commit**

```bash
git add src/
git commit -m "feat: wavetable synthesis engine with morph control"
```

---

### Task 15: Granular Synth Voice

**Files:**
- Create: `src/main/java/com/forge/audio/synth/GranularSynthVoice.java`
- Create: `src/main/java/com/forge/audio/synth/GrainPool.java`
- Test: `src/test/java/com/forge/audio/synth/GranularSynthVoiceTest.java`

- [ ] **Step 1: Implement GrainPool — pre-allocated grain objects**

64 pre-allocated Grain objects. Each grain has: position in source buffer, playback rate, amplitude envelope (Hann window), pan, active flag, phase counter. Pool recycles inactive grains.

- [ ] **Step 2: Implement GranularSynthVoice**

Source buffer (2 seconds at 44.1kHz = 88200 samples). FREEZE captures current oscillator output into buffer. Grain spawner creates new grains at `density` rate. Each grain reads from buffer at `position ± scatter`, `pitch ± pitchScatter`, with Hann envelope of `grainSize` ms. All active grains summed → filter → envelope → output.

- [ ] **Step 3: Write test — granular produces output when grains are active**

- [ ] **Step 4: Run tests, commit**

```bash
git add src/
git commit -m "feat: granular synthesis engine with grain pool and freeze"
```

---

### Task 16: FFT Analysis + Visualizer Framework

**Files:**
- Create: `src/main/java/com/forge/audio/engine/FftProcessor.java`
- Create: `src/main/java/com/forge/ui/visualizer/VisualizerPanel.java`
- Create: `src/main/java/com/forge/ui/visualizer/VisualizerRenderer.java` (interface)
- Create: `src/main/java/com/forge/ui/visualizer/SpectrumRenderer.java`
- Create: `src/main/java/com/forge/ui/visualizer/OscilloscopeRenderer.java`
- Test: `src/test/java/com/forge/audio/engine/FftProcessorTest.java`

- [ ] **Step 1: Implement FftProcessor — Cooley-Tukey radix-2 FFT**

Pure Java FFT implementation. Takes 256 float samples, applies Hann window, returns 128 magnitude bins. Also computes RMS energy, peak amplitude, spectral centroid, and simple beat detection (RMS spike above running average).

- [ ] **Step 2: Write FFT test**

Test: feed a pure 440Hz sine, verify the peak bin is at the correct index (440 / (44100/256) ≈ bin 2.56, so bin 2 or 3 should be highest).

- [ ] **Step 3: Implement VisualizerPanel — container with mode tabs**

Top tabs for mode switching (F1-F6 shortcut). Hosts a Canvas that the active renderer draws to. AnimationTimer at 60fps reads AnalysisBus and delegates to renderer.

- [ ] **Step 4: Implement SpectrumRenderer**

Draws vertical bars from FFT magnitudes. Gradient from ARGENT_RED (bottom) → ARGENT_ORANGE → ARGENT_YELLOW (top). Glow on peaks (DropShadow). Decay trail (bars fall smoothly, not instantly).

- [ ] **Step 5: Implement OscilloscopeRenderer**

Draws waveform samples as a line on a CRT-style grid. Grid lines at 25% intervals. Phosphor color (green or red, configurable). Glow effect via line width + shadow.

- [ ] **Step 6: Verify visualizers react to audio**

Run app. Play a sound. See spectrum bars move. Switch to oscilloscope, see waveform.

- [ ] **Step 7: Commit**

```bash
git add src/
git commit -m "feat: FFT analysis, spectrum analyzer, oscilloscope visualizer"
```

---

### Task 17: Remaining Visualizers

**Files:**
- Create: `src/main/java/com/forge/ui/visualizer/SpectrogramRenderer.java`
- Create: `src/main/java/com/forge/ui/visualizer/TerrainRenderer.java`
- Create: `src/main/java/com/forge/ui/visualizer/ParticleRenderer.java`
- Create: `src/main/java/com/forge/ui/visualizer/VegaEyeRenderer.java`

- [ ] **Step 1: Implement SpectrogramRenderer**

Uses `WritableImage` + `PixelWriter`. Each frame: shift existing image left by 1 column, draw new FFT column on the right edge. Color map: black (silent) → red → orange → yellow (loud). Scrolls continuously.

- [ ] **Step 2: Implement TerrainRenderer**

JavaFX 3D SubScene. TriangleMesh with vertex grid (e.g., 64×32). Each frame: update vertex Y positions from FFT magnitude history (last 32 frames → Z depth). Wireframe rendering via `DrawMode.LINE`. Argent-colored `PhongMaterial`. `PerspectiveCamera` with slow orbit animation.

- [ ] **Step 3: Implement ParticleRenderer**

Uses FXGL's ParticleEmitter. Configure emitter for each drum voice:
- Kick → central burst (large particles, red/orange, fast outward velocity)
- Snare → scattered sparks (small particles, yellow, random directions)
- Hat → upward embers (tiny particles, amber, slow float)
- Perc → side splashes

Particle intensity scaled by audio RMS. Burst triggered on sequencer clock beats (not audio onset — for precision).

- [ ] **Step 4: Implement VegaEyeRenderer**

Canvas + Shape composition. Central circle (iris) with radial gradient. Inner pupil circle dilates with RMS energy (louder = bigger pupil). Iris color shifts with spectral centroid (dark=blue, bright=cyan). Concentric ring pulses expand outward on beats. In DIVINE mode: all colors shift to gold, rings become golden rays.

- [ ] **Step 5: Test all 6 visualizers, cycle through with F1-F6**

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat: spectrogram, 3D terrain, particles, VEGA eye visualizers"
```

---

## Chunk 4: VEGA AI + Live Performance + I/O

### Task 18: VEGA Terminal UI

**Files:**
- Create: `src/main/java/com/forge/ui/panels/VegaPanel.java`
- Create: `src/main/java/com/forge/ui/panels/VegaTerminal.java`
- Create: `src/main/java/com/forge/ui/panels/VegaAvatar.java`

- [ ] **Step 1: Implement VegaAvatar**

Circular VEGA eye widget. Blue iris + glow for ASSIST mode. Gold + rays for DIVINE mode. Status indicator below (OPERATIONAL / THINKING / OFFLINE). Mode toggle buttons (ASSIST / DIVINE).

- [ ] **Step 2: Implement VegaTerminal**

ScrollPane with VBox of chat messages. Each message: colored prefix (SLAYER> in orange, VEGA> in blue), monospace text, tree-style output for tool results (├─ / └─ prefixed lines). Input TextField at bottom with blinking cursor. Auto-scroll to bottom on new messages. Typing indicator when VEGA is processing.

- [ ] **Step 3: Implement VegaPanel**

VBox containing: header ("◇ V.E.G.A ◇"), VegaAvatar, mode toggle, VegaTerminal. Blue-tinted background. Border separating from center panel.

- [ ] **Step 4: Verify panel renders correctly**

Run app. See VEGA panel on the right. Type in the input field. Messages appear in the log (no AI backend yet — just local echo for testing).

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat: VEGA terminal UI with avatar, chat log, mode toggle"
```

---

### Task 19: VEGA AI Backend — LangChain4j Integration

**Files:**
- Create: `src/main/java/com/forge/vega/VegaAgent.java`
- Create: `src/main/java/com/forge/vega/VegaTools.java`
- Create: `src/main/java/com/forge/vega/VegaSystemPrompt.java`
- Create: `src/main/java/com/forge/vega/VegaConfig.java`
- Create: `src/main/java/com/forge/vega/GrooveboxState.java`
- Test: `src/test/java/com/forge/vega/VegaToolsTest.java`

- [ ] **Step 1: Implement VegaConfig — API key loading**

Checks: (1) env var `ANTHROPIC_API_KEY`, (2) file `~/.forge/config.json`, (3) returns null if neither. Provides method to save key to config file.

- [ ] **Step 2: Implement VegaSystemPrompt — builds the system prompt**

Assembles the system prompt with: persona description, musical knowledge (scales, common patterns, genre conventions), current groovebox state (serialized from GrooveboxState), DOOM flavor text.

- [ ] **Step 3: Implement GrooveboxState — snapshot of current state for VEGA context**

Reads from ProjectState + AudioEngine to build a compact description: current engine, patch summary, BPM, key, active pattern, active section, FX settings, what's muted.

- [ ] **Step 4: Implement VegaTools — all @Tool methods**

Each tool method receives parameters from LangChain4j, translates to model/audio changes. E.g., `setEngine()` updates SynthPatch.engine and calls applyPatch on voices. `setDrumPattern()` writes to current Pattern's drum steps. `setBPM()` calls SequencerClock.setBpm(). Each tool returns a confirmation string shown in the terminal.

- [ ] **Step 5: Implement VegaAgent — LangChain4j agent orchestration**

```java
package com.forge.vega;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.service.AiServices;

public class VegaAgent {
    private final VegaTools tools;
    private boolean divineMode = false;

    public void sendMessage(String userMessage, Consumer<String> onResponse) {
        Thread.ofVirtual().start(() -> {
            // Build model (Haiku for ASSIST, Sonnet for DIVINE)
            // Build system prompt with current state
            // Create AI service with tools
            // Send message, get response
            // Post response to UI via onResponse callback
        });
    }
}
```

- [ ] **Step 6: Write VegaTools test — test tool methods change state correctly**

Test: `setBPM(140)` changes ProjectState.bpm to 140. `setDrumPattern(KICK, [1,0,0,0,...])` activates step 0 of kick.

- [ ] **Step 7: Wire VegaPanel to VegaAgent**

Terminal input → VegaAgent.sendMessage() → response displayed in terminal with tree formatting. Show thinking indicator while waiting.

- [ ] **Step 8: End-to-end test — type "make a beat" and see VEGA configure the groovebox**

Run app with API key set. Type a prompt. VEGA responds, calls tools, pattern updates visually.

- [ ] **Step 9: Commit**

```bash
git add src/
git commit -m "feat: VEGA AI backend — LangChain4j agent with tool calling"
```

---

### Task 20: Live Performance — Sections + Fills + Mute

**Files:**
- Create: `src/main/java/com/forge/audio/sequencer/SectionManager.java`
- Create: `src/main/java/com/forge/audio/sequencer/FillGenerator.java`
- Modify: `src/main/java/com/forge/audio/sequencer/StepSequencer.java`
- Test: `src/test/java/com/forge/audio/sequencer/SectionManagerTest.java`
- Test: `src/test/java/com/forge/audio/sequencer/FillGeneratorTest.java`

- [ ] **Step 1: Implement SectionManager**

Manages section list, active section, queued section. On bar boundary: if queue is set, switch to queued section (load pattern, apply FX overrides). Tracks bar position within section. Fires onSectionChange callback.

- [ ] **Step 2: Implement FillGenerator**

Takes a drum pattern, returns a modified 1-bar pattern as a fill. Four types:
- SIMPLE: increase density on last 4 steps
- ROLL: snare 16th notes on last 4 steps
- BUILDUP: density + pitch rise + hat opens
- BREAKDOWN: strip to kick only for 12 steps, full kit on step 16

- [ ] **Step 3: Wire to StepSequencer**

Sequencer checks SectionManager each bar. If fill is queued, replace current bar's pattern with generated fill. After fill bar, switch to queued section.

- [ ] **Step 4: Wire track mute/solo**

Boolean array `muted[5]` (4 drums + 1 synth). StepSequencer checks mute state before triggering. Mute is instant — just skips the trigger.

- [ ] **Step 5: Write tests**

SectionManager test: queue a section, advance to bar boundary, verify switch happens.
FillGenerator test: generate each fill type, verify output pattern has expected characteristics (ROLL has snare on last 4 steps, etc).

- [ ] **Step 6: Run tests, commit**

```bash
git add src/
git commit -m "feat: section manager, fill generator, track muting"
```

---

### Task 21: MIDI Input

**Files:**
- Create: `src/main/java/com/forge/midi/MidiInputHandler.java`
- Create: `src/main/java/com/forge/midi/MidiLearn.java`

- [ ] **Step 1: Implement MidiInputHandler**

Uses `javax.sound.midi`. Opens first available MIDI input device. Listens for note on/off (routes to VoiceAllocator or DrumEngine based on channel). Listens for CC messages (routes to mapped parameters via MidiLearn).

- [ ] **Step 2: Implement MidiLearn**

CC auto-learn: enter learn mode, wiggle a MIDI CC, next UI knob clicked gets mapped to that CC. Stores mappings as CC number → parameter path. Pickup mode for absolute controllers.

- [ ] **Step 3: Commit**

```bash
git add src/
git commit -m "feat: MIDI input with auto-learn CC mapping"
```

---

### Task 22: WAV + MIDI Export

**Files:**
- Create: `src/main/java/com/forge/audio/export/WavExporter.java`
- Create: `src/main/java/com/forge/audio/export/MidiExporter.java`
- Test: `src/test/java/com/forge/audio/export/WavExporterTest.java`
- Test: `src/test/java/com/forge/audio/export/MidiExporterTest.java`

- [ ] **Step 1: Implement WavExporter**

Offline render: creates a second JSyn synthesizer (not connected to audio output), runs the full signal chain (synth voices + drums + effects) for the arrangement duration, writes PCM samples to a WAV file. Uses `javax.sound` AudioFileWriter. Configurable sample rate and bit depth. Progress callback for status bar.

- [ ] **Step 2: Implement MidiExporter**

Converts Pattern/arrangement to Standard MIDI File (Type 1) using `javax.sound.midi`. One track per channel (synth, kick, snare, hat, perc). Writes note on/off events with velocity. Includes tempo meta event.

- [ ] **Step 3: Write tests**

WavExporter test: render 1 second of a sine wave, verify file exists and has correct header (sample rate, channels, bit depth).
MidiExporter test: export a pattern with 4 kick hits, verify MIDI file has 4 note-on events.

- [ ] **Step 4: Commit**

```bash
git add src/
git commit -m "feat: WAV render and MIDI export"
```

---

### Task 23: Project Persistence

**Files:**
- Create: `src/main/java/com/forge/model/ProjectPersistence.java`
- Test: `src/test/java/com/forge/model/ProjectPersistenceTest.java`

- [ ] **Step 1: Implement ProjectPersistence**

Serialize `ProjectState` to JSON via Gson. Save to `.forge` file. Load from `.forge` file. Auto-save to `.forge.autosave` on Ctrl+S. On startup, check for autosave and offer recovery dialog.

- [ ] **Step 2: Write test — save and load round-trip**

Create a ProjectState with specific values (BPM=140, kick pattern with steps 0,4,8,12 active). Save to temp file. Load from temp file. Verify all values match.

- [ ] **Step 3: Wire to UI menus**

Protocol menu → Save As (file chooser), Open (file chooser), auto-save on Ctrl+S.

- [ ] **Step 4: Commit**

```bash
git add src/
git commit -m "feat: project persistence — save/load .forge files with auto-save"
```

---

### Task 24: Undo System

**Files:**
- Create: `src/main/java/com/forge/model/UndoManager.java`
- Test: `src/test/java/com/forge/model/UndoManagerTest.java`

- [ ] **Step 1: Implement UndoManager**

Stores snapshots of ProjectState (deep copies via Gson serialization). 50-level stack. `push()` on every user action that changes state. `undo()` restores previous snapshot. `redo()` moves forward. Snapshot is a serialized JSON string (compact, fast to copy).

- [ ] **Step 2: Write test**

Push 3 states. Undo twice. Verify state matches the first push. Redo once. Verify state matches second push.

- [ ] **Step 3: Wire to Ctrl+Z/Ctrl+Shift+Z**

- [ ] **Step 4: Commit**

```bash
git add src/
git commit -m "feat: undo/redo system with 50-level snapshot stack"
```

---

### Task 25: Polish + Integration

**Files:**
- Modify: `src/main/java/com/forge/ForgeApp.java`
- Create: `src/main/java/com/forge/model/DefaultProject.java`

- [ ] **Step 1: Create DefaultProject — sensible startup state**

Subtractive engine, saw + square oscillators, LP filter at 2kHz, medium ADSR, 128 BPM, no FX active, empty drum pattern. The app should make a good sound out of the box when you press a key.

- [ ] **Step 2: Wire everything together in ForgeApp**

Create AudioEngine, DrumEngine, VoiceAllocator (8 SubtractiveSynthVoices), SequencerClock, StepSequencer, SectionManager, EffectsChain, VegaAgent. Create all UI panels, connect to audio. Set up keyboard handler. Apply default project. Start audio engine.

- [ ] **Step 3: Parameter locking UX**

In DrumGrid: hold a step button + drag a knob = P-lock that parameter on that step. Visual: P-locked steps get a small colored dot. In StepSequencer (synth): same interaction.

- [ ] **Step 4: Copy/paste**

Ctrl+C copies current selection (depends on focus: step, track, pattern). Ctrl+V pastes. Ctrl+D duplicates. Selection model tracks what's focused.

- [ ] **Step 5: Real-time recording**

When ARM is active and sequencer is playing, keyboard/MIDI input writes to the current pattern's steps at the current position. Quantizes to nearest step.

- [ ] **Step 6: Note Repeat**

When ARM is active, hold a drum key + use a rate selector. Generates auto-repeating triggers at 8th/16th/32nd. Writes to pattern if recording.

- [ ] **Step 7: Full end-to-end test**

Launch app. Verify: synth plays from keyboard. Drums program via grid. Sequencer plays. Effects apply. VEGA responds. Visualizers react. Sections switch. Export works. Save/load works. Undo works. MIDI input works (if controller available).

- [ ] **Step 8: Commit**

```bash
git add src/
git commit -m "feat: integration — default project, P-locks, copy/paste, recording, note repeat"
```

---

### Task 26: Missing Features — Control All, Status Bar, CRT Polish, API Key Dialog

**Files:**
- Modify: `src/main/java/com/forge/ui/controls/ForgeKnob.java`
- Modify: `src/main/java/com/forge/ui/theme/CrtOverlay.java`
- Create: `src/main/java/com/forge/ui/panels/StatusBar.java`
- Create: `src/main/java/com/forge/ui/dialogs/ApiKeyDialog.java`
- Modify: `src/main/java/com/forge/vega/VegaAgent.java` (add conversation history persistence)

- [ ] **Step 1: Control All — Shift+drag any knob affects all tracks**

Add to ForgeKnob: detect Shift held during drag. If Shift is held, fire a `controlAllEvent` that the SynthPanel and DrumPanel listen to — apply the same delta to the equivalent parameter on all voices/tracks. Release Shift to return to single-knob mode.

- [ ] **Step 2: Status Bar — live data population**

Implement StatusBar as an HBox showing: VEGA status (ONLINE/THINKING/OFFLINE), audio engine status (ACTIVE/STOPPED), current section name + bar position ("VERSE [4/8]"), musical key ("Dm"), CPU usage estimate (measure audio callback duration), latency ("5.8ms"), sample rate, MIDI channel. Update at 10fps via Timeline.

- [ ] **Step 3: CRT chromatic aberration + screen glow**

Extend CrtOverlay: add optional chromatic aberration (slight red/blue offset on text — implemented by rendering text to two offset Canvas layers with different color channels). Add screen glow via `Bloom` effect on the root pane with low threshold. Both toggleable.

- [ ] **Step 4: API Key settings dialog**

JavaFX Dialog accessible from VEGA menu → "Configure API Key". Text field for key input. Save button writes to `~/.forge/config.json`. Test button validates key with a quick API ping. If no key configured on first launch, VEGA panel shows a styled prompt: "VEGA requires an API key. Configure in VEGA → Settings."

- [ ] **Step 5: VEGA conversation history in ProjectState**

Add `List<VegaChatMessage> vegaHistory` to ProjectState. Each message has role (SLAYER/VEGA), text, timestamp. Serialize/deserialize with project file. On load, repopulate VegaTerminal.

- [ ] **Step 6: Polymetric track length UI**

Add right-click menu on drum track label → "Set Track Length" → choose 16/32/64. DrumGrid redraws with paging (Page 1: steps 1-16, Page 2: steps 17-32). Page indicators at bottom of each track.

- [ ] **Step 7: Missing keyboard shortcuts**

Add to KeyboardHandler: Ctrl+5 for synth mute. Ctrl+Shift+S for Save As. Ctrl+O for Open. Tab for view switching (drum grid ↔ synth sequence grid).

- [ ] **Step 8: MIDI CC for transport and sections**

Extend MidiInputHandler: configurable CC mappings for play/stop (default CC 117), section triggers (CC 118 + value 0-15), mute toggles (CC 119 + value = track index). Stored in config file.

- [ ] **Step 9: Commit**

```bash
git add src/
git commit -m "feat: Control All, status bar, CRT polish, API key dialog, polymetric UI"
```

---

### Task 27: MIDI Input Tests

**Files:**
- Create: `src/test/java/com/forge/midi/MidiInputHandlerTest.java`
- Create: `src/test/java/com/forge/midi/MidiLearnTest.java`

- [ ] **Step 1: Write MidiInputHandler test**

Mock a MIDI Receiver. Send note-on messages, verify VoiceAllocator receives correct note/velocity. Send CC messages, verify mapped parameters change.

- [ ] **Step 2: Write MidiLearn test**

Enter learn mode. Simulate CC message (CC 74). Assign to a parameter path ("filter.cutoff"). Send CC 74 with value 64. Verify parameter is set to the mapped range midpoint.

- [ ] **Step 3: Run tests, commit**

```bash
git add src/
git commit -m "test: MIDI input and auto-learn tests"
```

---

## Build Order Summary

| Chunk | Tasks | Milestone |
|-------|-------|-----------|
| 1: Foundation | 1-7 | Working audio engine, synth, drums, sequencer, effects — no UI |
| 2: UI | 8-13 | Full DOOM-themed UI, playable groovebox with mouse + keyboard, synth sequence grid |
| 3: Engines + Viz | 14-18 | All 4 synth engines, all 6 visualizers |
| 4: VEGA + I/O | 19-27 | AI assistant, sections, MIDI, export, persistence, Control All, CRT polish, all gaps addressed |

Each chunk produces working, testable software. Chunk 1 can be verified purely through audio output and unit tests. Chunk 2 makes it playable. Chunk 3 adds depth. Chunk 4 completes the vision.

**Total: 27 tasks, ~130 steps, 4 working milestones.**
