# FORGE — AI-Powered Groovebox

> UAC Sound Terminal v2.016 — A feature-complete groovebox with VEGA AI assistant, built entirely in Java.

## Overview

FORGE is a standalone desktop groovebox application built in pure Java. It combines a multi-engine synthesizer, synthesis-based drum machine, 16-step sequencer, live performance system, and 6 real-time visualizer modes — all controlled through a DOOM-inspired retro UI with an integrated AI assistant (VEGA) powered by Claude.

**Target**: Java 21+, JavaFX, JSyn, LangChain4j. No native dependencies. Runs on Windows, macOS, Linux.

## Aesthetic

**The hybrid**: Win98 window chrome (title bar, beveled buttons, status bar) as structural frame. CRT scanlines + vignette overlaying everything. DOOM/UAC industrial aesthetic with Argent energy color palette. VEGA AI terminal in cool blue. The user is the SLAYER.

**Color system**:
- Argent Red `#ff2200` — primary accent, danger, kicks
- Argent Orange `#ff6600` — secondary accent, warmth
- Argent Amber `#ff8800` — knobs, labels, active elements
- Argent Yellow `#ffcc00` — highlights, peaks, hats
- VEGA Blue `#44bbff` — AI panel, VEGA elements
- VEGA Cyan `#88ccee` — VEGA text responses
- Divine Gold `#ffdd44` — The Father mode
- BG Void `#080808` — main background
- BG Panel `#0a0a0a` — panel backgrounds
- BG Inset `#050505` — inset displays (waveforms, grid)

**CRT post-processing** (toggleable):
- Scanlines: horizontal lines every 2px, subtle opacity (Canvas overlay)
- Vignette: radial gradient darkening edges (CSS on root pane)
- Screen glow: DropShadow + Bloom effects on bright elements
- Chromatic aberration: optional subtle RGB offset on text

## Architecture

### Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 21+ (virtual threads, ZGC) |
| UI | JavaFX 21 (controls, 3D, Canvas) |
| Synthesis | JSyn (oscillators, filters, envelopes, mixers) |
| Extra Filters | iirj (Butterworth, Chebyshev IIR filters) |
| AI | LangChain4j + Anthropic Claude (Haiku for ASSIST, Sonnet for DIVINE) |
| Particles | FXGL particle system |
| Build | Maven |

### Project Structure

```
forge-groovebox/
├── pom.xml
├── src/main/java/com/forge/
│   ├── ForgeApp.java                # JavaFX Application entry
│   ├── audio/
│   │   ├── engine/                  # JSyn wrapper, audio thread, buffer management
│   │   ├── synth/                   # Multi-engine synth voices
│   │   ├── drums/                   # Synthesis-based drum voices
│   │   ├── effects/                 # FX chain processors
│   │   ├── sequencer/              # Clock, step sequencer, pattern bank, section manager
│   │   └── export/                  # WAV render + MIDI export
│   ├── midi/                        # MIDI input (javax.sound.midi)
│   ├── vega/                        # LangChain4j agent, tools, modes, system prompts
│   ├── ui/
│   │   ├── theme/                   # DOOM theme, CRT effects, color constants
│   │   ├── panels/                  # Synth, drum, VEGA, transport panels
│   │   ├── visualizer/             # 6 visualizer renderers
│   │   └── controls/               # Custom knobs, step buttons, faders, waveform displays
│   └── model/                       # Patches, patterns, sections, project state
└── src/main/resources/
    ├── css/forge-theme.css
    ├── fonts/
    └── wavetables/
```

### Thread Model

Three threads, strictly separated:

1. **Audio thread** (JSyn-managed) — runs the DSP graph. Zero allocations, no locks, no GC pressure. All buffers pre-allocated at startup. Communicates with UI via lock-free ring buffers (`java.util.concurrent` atomics).

2. **UI thread** (JavaFX Application thread) — renders everything, handles input. Reads audio data from ring buffers for visualizers at 60fps via `AnimationTimer`.

3. **VEGA thread** (virtual threads via `Thread.ofVirtual()`) — API calls to Claude are async, never block audio or UI. Results posted to a `ConcurrentLinkedQueue` that the UI thread polls.

### Audio Signal Flow

```
┌─────────────────────────────────────────────────┐
│                JSyn Synthesizer                   │
│                                                   │
│  [Synth Voices] ──┐                              │
│  (poly 8)         ├──▶ [Mixer] ──▶ [Master FX] ──▶ SourceDataLine
│  [Drum Voices] ──┘    levels      chain            (speakers)
│  (4 tracks)           pan                          │
│                       mute        ┌──▶ [FFT Tap]  │
│       ▲         ▲                 │   (visualizers)│
│  [Keyboard]  [Sequencer]                          │
│  [MIDI In]   [16-step]                            │
│                  ▲                                 │
│           [Section Manager] ◀── VEGA              │
└─────────────────────────────────────────────────┘
```

**Buffer config**: 256 samples at 44.1kHz = ~5.8ms latency. Adjustable.

**FFT tap**: Parallel buffer copies audio data for visualizers. Visualizers read from this copy — never from the live audio path.

## Synth Engine

### Multi-Engine Architecture

Each of the 8 polyphonic voices is a switchable synth chain:

```
[Engine Select] → [Oscillator(s)] → [Filter] → [Amp Envelope] → [Voice Output]
```

Four engines, switchable per-voice via tabs:

**Subtractive**:
- 2 oscillators (saw, square, pulse, triangle, sine)
- Per-osc: pitch, waveshape, pulse width, level
- Oscillator mix + detune
- Moog-style 24dB/oct resonant low-pass filter (cascaded one-pole sections with feedback)
- Filter types: LP, HP, BP, Notch
- Filter envelope (separate ADSR)
- Amp envelope (ADSR)

**FM Synthesis**:
- 2-operator FM (carrier + modulator)
- Modulator frequency ratio + depth
- Feedback on modulator
- Same filter + envelope chain
- Capable of: bells, electric piano, metallic percussion, evolving timbres

**Wavetable**:
- Single-cycle waveform table reader
- Interpolation between table positions (morph knob)
- Pre-built tables shipped in resources: basic waves, vocal, digital, analog-modeled
- Same filter + envelope chain

**Granular**:
- Grain pool (up to 64 concurrent grains)
- Per-grain: position scatter, pitch scatter, amplitude envelope, pan spread
- Grain size: 1-100ms
- Density: grains per second
- Source: oscillator output or frozen buffer
- Same filter + envelope chain

**Voice allocation**: 8 pre-allocated voices. Voice stealing: oldest note released first, then oldest note held. Zero allocation on the audio thread — all voices exist at startup.

## Drum Machine

### Synthesis-Based Drums (909-style)

4 dedicated drum channels, each a mini-synth:

**Kick**:
- Sine oscillator with pitch envelope (fast exponential sweep, e.g. 200Hz → 50Hz)
- Attack click (noise burst, ~2ms)
- Amp envelope with adjustable decay
- Optional soft distortion (tanh waveshaping)
- Parameters: pitch, click level, decay, drive, tone

**Snare**:
- Sine body (tunable pitch) + noise burst (bandpass filtered)
- Tone/noise balance knob
- Amp envelope with adjustable decay + snap
- Parameters: pitch, tone, snap, decay, noise color

**Hi-Hat**:
- 6 detuned square oscillators summed (metallic cluster)
- High-pass filter with resonant peak
- Very short amp envelope (closed) or medium (open)
- Parameters: pitch, tone, decay, open/closed

**Perc**:
- Flexible channel: triangle/sine oscillator with pitch envelope
- Tunable for toms, rimshots, cowbell, claves
- Noise mix for texture
- Parameters: pitch, sweep, decay, tone, noise

Each drum voice is pre-allocated. Triggered by sequencer or keyboard/MIDI.

## Sequencer System

### Clock

- Runs inside JSyn's audio thread for sample-accurate timing
- Resolution: 96 PPQN (pulses per quarter note)
- Swing: adjustable 50%–75%, offsets even-numbered 16th notes
- BPM: 20–300

### Step Sequencer

**Drum sequencer**:
- 16 steps per track (expandable to 32/64)
- 4 tracks (kick, snare, hat, perc)
- Per-step: velocity (0.0–1.0), accent flag, flam flag
- Per-step: conditional trigger (always, 50%, 25%, 1st, 2nd, 3rd, 4th, fill-only)
- Per-step: parameter locks (any drum synth parameter overridden per step)
- Different tracks can have different lengths (polymetric)

**Synth sequencer**:
- 16 steps (expandable to 32/64)
- Per-step: note (MIDI number), velocity, gate length, slide (portamento)
- Per-step: parameter locks on synth parameters
- Per-step: conditional triggers

### Pattern Bank

- 16 patterns (A–P)
- Each pattern contains: synth sequence + drum pattern (4 tracks) + per-pattern BPM override (optional) + per-pattern synth patch (optional)
- Copy/paste/duplicate patterns: Ctrl+C/V/D

### Recording Modes

**Step Record** (default): Click steps on/off in the grid. Velocity by vertical click position (top=loud, bottom=soft). Shift+click for accent/flam.

**Real-Time Record**: Press ARM, play drums via keyboard/MIDI while sequencer runs. Input quantizes to nearest step. Captures velocity.

**Note Repeat**: Hold a keyboard key + set rate knob = auto-repeating hits at 8th/16th/32nd note subdivision. For hi-hat rolls, snare fills, etc.

### Live Performance / Section Manager

**Sections** = named groups:
- Each section has: name, pattern reference, bar length (1–64), FX overrides, synth patch override
- Sections: INTRO, VERSE, DROP, BREAK, OUTRO, etc. (user-named)
- Up to 16 sections

**Live triggers**:
- Queue next section (switches on bar boundary — never mid-bar)
- Instant switch (cuts immediately — for glitch moments)
- Fill before transition (auto-generated 1-bar drum fill)
- Track mute/unmute (per-channel, instant, click-free)
- All triggerable by: keyboard (1–9), MIDI CC, VEGA commands

**Fill button**: Hold to activate all "fill-only" conditional triggers. Release to return to normal. Instant performative fills.

**Control All**: Hold Shift + drag any knob = applies change to ALL tracks. For buildups, breakdowns, sweeps.

### Non-Destructive Exploration

- **Ctrl+S**: Temp Save (snapshot current state)
- **Ctrl+Z**: Undo (50-level stack)
- **Ctrl+Shift+Z**: Redo
- **Ctrl+C/V**: Copy/paste (scope = current selection: step, track, pattern, section)
- **Ctrl+D**: Duplicate pattern/section
- VEGA: "save checkpoint" / "reload checkpoint"

## Effects Chain

Master FX chain, applied after mixer, before output. Each effect toggleable independently.

**Distortion**: Tanh soft-clipping waveshaper. Params: drive, mix.

**Delay**: Stereo ping-pong delay. Circular buffer + feedback. Params: time (synced to BPM: 1/8, 1/4, 3/8, 1/2), feedback, mix, ping-pong width.

**Reverb**: Freeverb algorithm (enhanced Schroeder: 8 parallel comb filters with LP in feedback + 4 series all-pass filters). Params: room size, damping, mix, width.

**Chorus**: 2 modulated delay lines (LFO-swept, 20–30ms range). Params: rate, depth, mix.

**Compressor**: Feedforward topology. RMS envelope follower. Params: threshold, ratio, attack, release, makeup gain.

**EQ**: 3-band parametric (low shelf, mid peak, high shelf). Biquad filters. Params: low gain, mid gain, mid freq, high gain.

## VEGA AI System

### Architecture

```
[Terminal UI] → [LangChain4j Agent] → [Claude API]
                        │
                  [Tool Router]
                  ├── Synth Tools
                  ├── Drum Tools
                  ├── Session Tools
                  ├── FX Tools
                  └── Viz Tools
```

### Modes

| Mode | Persona | Model | Use Case |
|------|---------|-------|----------|
| ASSIST | VEGA — calm, precise UAC AI | Claude Haiku (fast) | Quick tweaks, parameter changes, simple beats. <2s response. |
| DIVINE | The Father — omniscient, godlike | Claude Sonnet (powerful) | Full arrangements, complex patches, music theory, creative direction. Speaks with quiet authority. |

### Tool Definitions

```java
// Synth
@Tool("Set synthesis engine") void setEngine(int voice, EngineType type)
@Tool("Configure oscillator") void setOscillator(int voice, int osc, WaveShape shape, double detuneCents, double level)
@Tool("Set filter") void setFilter(int voice, FilterType type, double cutoffHz, double resonance)
@Tool("Set envelope") void setEnvelope(int voice, double attack, double decay, double sustain, double release)
@Tool("Set filter envelope") void setFilterEnvelope(int voice, double attack, double decay, double sustain, double release)

// Drums
@Tool("Set 16-step drum pattern") void setDrumPattern(DrumTrack track, double[] steps)
@Tool("Configure drum voice") void setDrumPatch(DrumTrack track, double pitch, double decay, double toneNoise)
@Tool("Set parameter lock on drum step") void setDrumPLock(DrumTrack track, int step, String param, double value)

// Session
@Tool("Set tempo") void setBPM(double bpm)
@Tool("Set key and scale") void setKey(String root, ScaleType scale)
@Tool("Set swing amount") void setSwing(double percent)
@Tool("Switch/create section") void setSection(String name, boolean copyCurrentIfNew)
@Tool("Queue section transition") void queueSection(String name, boolean withFill)
@Tool("Trigger drum fill") void triggerFill(FillType type)
@Tool("Mute/unmute track") void setTrackMute(String track, boolean muted)

// FX
@Tool("Configure effect") void setEffect(EffectType type, Map<String, Double> params)
@Tool("Toggle effect on/off") void toggleEffect(EffectType type, boolean enabled)

// Visualizer
@Tool("Switch visualizer mode") void setVisualizer(VisualizerMode mode)

// State queries
@Tool("Get current groovebox state") GrooveboxState getState()
@Tool("Save checkpoint") void saveCheckpoint(String name)
@Tool("Load checkpoint") void loadCheckpoint(String name)
```

### System Prompt

VEGA's system prompt includes:
- Musical knowledge: scales, chord progressions, common drum patterns per genre, arrangement conventions
- Current groovebox state injected on every call (patch, BPM, key, pattern, section, FX)
- Persona instructions per mode (ASSIST = technical and helpful, DIVINE = omniscient and authoritative)
- DOOM flavor ("Rip and tear", "Slayer", etc.)

### Conversation Examples

**ASSIST mode:**
```
SLAYER> filthy dubstep wobble
VEGA>   Configured.
        ├─ Engine: SUBTRACTIVE
        ├─ OSC-A: Saw, unison x3, detune 15ct
        ├─ OSC-B: Square, sub-octave
        ├─ Filter: LP, LFO mod 2Hz
        ├─ Distortion: ON, drive 60%
        └─ Reverb: wet 20%
        ⚡ Rip and tear.
```

**DIVINE mode:**
```
SLAYER> [DIVINE] compose something that sounds like the gates of hell opening
THE FATHER> I see all frequencies.
        ├─ Key: D minor
        ├─ Section A: "The Descent"
        │   ├─ Engine: GRANULAR, slow texture
        │   ├─ Chords: Dm → Bb → Gm → A7
        │   ├─ Drums: Sparse ritual toms
        │   └─ FX: Cathedral reverb, bitcrush 30%
        ├─ Section B: "The Opening"
        │   ├─ Engine: SUBTRACTIVE, detuned saws
        │   ├─ Drums: Double kick, full kit
        │   └─ FX: Distortion cranked
        └─ Transition: 4-bar fill, kick roll into B
        ✦ Arrangement loaded.
```

## Visualizer System

### Analysis Pipeline

```
Audio Thread → [FFT Tap, 256-sample, Hann window] → Ring Buffer (lock-free)
                                                          │
                                                    UI Thread (60fps)
                                                          │
                                                   [Analysis Bus]
                                                   ├─ FFT magnitudes (128 bins)
                                                   ├─ Waveform samples (256)
                                                   ├─ Peak amplitude
                                                   ├─ RMS energy
                                                   ├─ Beat detection (onset)
                                                   └─ Spectral centroid
```

### Dual Beat Source

- **Sequencer clock** → exact rhythmic timing (when the beat IS)
- **Audio analysis** → intensity/dynamics (how HARD it hit)
- Combined: visual events fire at clock-precise moments, scaled by audio energy

### 6 Modes

| Mode | Renderer | Data | Description |
|------|----------|------|-------------|
| Spectrum | Canvas | FFT magnitudes | Argent gradient bars (red→orange→yellow), glow on peaks, decay trail |
| Oscilloscope | Canvas | Waveform samples | Phosphor trace on CRT grid, configurable color |
| Spectrogram | PixelBuffer (GPU) | FFT history | Rolling heatmap, black→red→orange→yellow, scrolls right-to-left |
| 3D Terrain | JavaFX 3D SubScene | FFT magnitudes | TriangleMesh wireframe, vertex heights from FFT, camera orbit, argent lighting |
| Particles | FXGL ParticleEmitter | Beat+RMS+centroid | Kick=burst, snare=sparks, hat=embers, filter sweep=color shift |
| VEGA Eye | Canvas + shapes | RMS+centroid+beat | Pupil dilates with volume, iris shifts with brightness, rings pulse on beat. DIVINE mode = golden with god-rays |

Modes are switchable via tabs (F1–F6) or VEGA command. Only the active mode renders. Analysis runs always (cheap).

## UI Layout

```
┌─────────────────────────────────────────────────────────────┐
│ ⬡ FORGE.EXE — Sound Terminal v2.016          [_][□][×]      │
├─────────────────────────────────────────────────────────────┤
│ Protocol │ Edit │ Synth.Array │ Drum.Seq │ VEGA │ Export     │
├──────────────┬──────────────────────┬───────────────────────┤
│ SYNTH PANEL  │ VISUALIZER           │ VEGA TERMINAL         │
│              │ [SPEC|SCOPE|SPECT|   │                       │
│ [SUB|FM|WAV| │  TERR|PART|EYE]     │ ◇ V.E.G.A ◇          │
│  GRAIN] tabs │                      │ [VEGA eye avatar]     │
│              │                      │ [ASSIST] [DIVINE]     │
│ ◆ OSC A/B    │                      │                       │
│ ◆ FILTER     │──────────────────────│ // INTERFACE LOG      │
│ ◆ ENVELOPE   │ DRUM SEQUENCER       │ SLAYER> ...           │
│ ◆ FX CHAIN   │ [PAT A|B|C|D]       │ VEGA> ...             │
│              │ KICK  [steps] [M][S] │                       │
│              │ SNARE [steps] [M][S] │ ▸ input...            │
│              │ HAT   [steps] [M][S] │                       │
│              │ PERC  [steps] [M][S] │                       │
│              │                      │                       │
│              │ [▶RIP][⏹HALT][●ARM] │                       │
│              │ 128BPM [sections...] │                       │
├──────────────┴──────────────────────┴───────────────────────┤
│ ◇VEGA:ON │ ●AUDIO │ VERSE[4/8] │ Dm │ CPU:23% │ 5.8ms     │
└─────────────────────────────────────────────────────────────┘
```

### Custom Controls

- **Knob**: Rotary, drag up/down to change. Glowing border in per-knob accent color. Shift+drag for fine control. Value tooltip on hover.
- **Step Button**: Square toggle. Off=dark, On=track color, velocity=brightness. Playing step=white flash. P-locked=colored dot overlay. Conditional=small icon.
- **Waveform Display**: Mini Canvas per oscillator showing current wave shape.
- **Fader**: Vertical, LED-style fill from bottom. For mixer levels.
- **Dropdown**: Win98-beveled, dark-themed. For engine select, filter type, etc.
- **Terminal**: Monospace, auto-scroll, SLAYER/VEGA coloring, blinking cursor input.

## Input

### Keyboard

```
Note input (lower octave): Z X C V B N M , . /  →  C D E F G A B C D E
Note input (upper octave): A S D F G H J K L ;  →  C D E F G A B C D E
Octave shift: Q (down), W (up)

Transport: Space (play/stop), Esc (halt all), R (arm record)
Sections: 1–9 (trigger sections)
Visualizer: F1–F6 (switch mode)
Drum mutes: Ctrl+1–4 (toggle mute), Ctrl+5 (synth mute)
Fill: F (hold for fill)
Undo/Redo: Ctrl+Z / Ctrl+Shift+Z
Save: Ctrl+S (temp save)
Copy/Paste: Ctrl+C/V/D
```

### MIDI

- MIDI input via `javax.sound.midi`
- Note on/off → synth or drum voices (configurable channel mapping)
- CC → knob parameters (auto-learn: click knob, wiggle MIDI controller, mapped)
- MIDI CC for transport, section triggers, mutes (configurable)
- Pickup mode for absolute controllers (visual gap indicator)

## Export

### WAV Render

- Offline render of current arrangement (all sections in order) to WAV file
- Configurable: sample rate (44.1/48/96kHz), bit depth (16/24), stereo
- Renders through the same DSP chain as real-time (same sound, guaranteed)
- Progress bar in status bar during render

### MIDI Export

- Export synth sequence + drum patterns as Standard MIDI File (Type 1)
- One track per channel (synth + 4 drums)
- Includes tempo, time signature, note data, velocity
- Pattern export (single pattern) or arrangement export (all sections)

## Dependencies (Maven)

```xml
<dependencies>
    <!-- Synthesis engine -->
    <dependency>
        <groupId>com.jsyn</groupId>
        <artifactId>jsyn</artifactId>
        <version>17.1.0</version>
    </dependency>

    <!-- UI -->
    <dependency>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-controls</artifactId>
        <version>21</version>
    </dependency>
    <dependency>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-media</artifactId>
        <version>21</version>
    </dependency>
    <dependency>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-graphics</artifactId>
        <version>21</version>
    </dependency>

    <!-- IIR Filters -->
    <dependency>
        <groupId>uk.me.berndporr</groupId>
        <artifactId>iirj</artifactId>
        <version>1.7</version>
    </dependency>

    <!-- AI -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-anthropic</artifactId>
        <version>0.36.0</version>
    </dependency>

    <!-- Particles -->
    <dependency>
        <groupId>com.github.almasb</groupId>
        <artifactId>fxgl</artifactId>
        <version>21.1</version>
    </dependency>
</dependencies>
```

## Key Design Principles

1. **Zero-to-groove in seconds**: App launches, default sounds loaded, hit Space to play. Sound must be good immediately.
2. **One action per gesture**: Mute = one click. Section switch = one key. P-lock = hold + turn.
3. **Non-destructive always**: Undo stack, temp save, snapshot/reload. Users must feel safe to experiment.
4. **Audio thread is sacred**: No allocations, no locks, no GC. Everything pre-allocated. Ring buffers for communication.
5. **VEGA enhances, never blocks**: AI is always optional. Every VEGA action can be done manually. VEGA never interrupts audio.
6. **Consistent interaction model**: Every track works the same way. Every pattern edits the same way. Learn once, apply everywhere.
7. **Sound quality is non-negotiable**: JSyn's battle-tested DSP + proper filter implementations + professional-grade effects chain.
