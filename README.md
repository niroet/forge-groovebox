# FORGE — AI-Powered Groovebox

> UAC Sound Terminal v2.016

A feature-complete groovebox built entirely in Java. DOOM/UAC-inspired retro aesthetic with CRT scanlines, Win98 chrome, and Argent energy visuals. Includes an integrated AI assistant (VEGA) powered by Claude that can design patches, generate beats, and compose arrangements from natural language.

![Java 21+](https://img.shields.io/badge/Java-21%2B-orange) ![JavaFX 21](https://img.shields.io/badge/JavaFX-21-blue) ![License](https://img.shields.io/badge/License-MIT-green)

## Features

### Synth Engine
- **4 synthesis engines**: Subtractive, FM, Wavetable, Granular — switchable per voice
- **8-voice polyphony** with voice stealing
- **2 oscillators** per voice (saw, square, pulse, triangle, sine)
- **Resonant filter** (LP, HP, BP, Notch) with dedicated envelope
- **ADSR amplitude envelope**

### Drum Machine
- **4 synthesis-based voices**: Kick, Snare, Hi-Hat, Perc — no samples needed
- **16-step sequencer** with velocity, accent, and flam
- **Conditional triggers**: always, 50%, 25%, 1st/2nd/3rd/4th play, fill-only
- **Parameter locking** (per-step automation)
- **Swing** (50%–75%)

### Effects
- Distortion (tanh waveshaper)
- Stereo Delay (ping-pong with feedback)
- Reverb (plate)
- Chorus (dual modulated delay)
- Compressor (feedforward RMS)
- 3-Band Parametric EQ

### VEGA — AI Assistant
- Powered by Claude (Anthropic)
- **ASSIST mode** (Haiku) — fast patch tweaks, quick beats
- **DIVINE mode** (Sonnet) — full arrangements, music theory, creative direction
- 13 tools for controlling every aspect of the groovebox
- Natural language sound design: *"filthy dubstep wobble with heavy distortion"*

### Visualizers (6 modes)
- **Spectrum Analyzer** — FFT bars with Argent gradient
- **Oscilloscope** — CRT phosphor waveform display
- **Spectrogram** — scrolling frequency×time heatmap
- **3D Terrain** — wireframe mountains from FFT data
- **Particles** — beat-reactive particle system (shockwaves, sparks, embers)
- **VEGA Eye** — AI iris that reacts to audio (pupil = volume, color = brightness)

### Live Performance
- **Section system** with bar-quantized transitions
- **4 fill types**: Simple, Roll, Buildup, Breakdown
- **Track mute/solo** per channel
- **Pattern bank** (16 patterns, A–P)

### I/O
- **Computer keyboard** piano (ASDF/ZXCV rows)
- **MIDI input** with auto-learn CC mapping
- **WAV export** (offline render)
- **MIDI export** (Standard MIDI File)
- **Project save/load** (.forge JSON files)
- **Undo/Redo** (50-level snapshot stack)

## Requirements

- **Java 21+** (Temurin recommended)
- **Maven 3.9+**
- **Linux/macOS/Windows** with audio output

If you have [mise](https://mise.jdx.dev/) installed, just run `mise install` — it'll set up Java 21 and Maven automatically.

## Quick Start

```bash
# Clone
git clone https://github.com/niroet/forge-groovebox.git
cd forge-groovebox

# Option A: If you have mise
mise install
mvn javafx:run

# Option B: Manual (requires Java 21+ and Maven on PATH)
mvn javafx:run
```

## Controls

### Keyboard

| Key | Action |
|-----|--------|
| `Space` | Play / Stop |
| `A S D F G H J K L` | Play synth notes (C4–D5) |
| `Z X C V B N M` | Play synth notes (C3–B3) |
| `Q` / `W` | Octave down / up |
| `1`–`9` | Trigger sections |
| `F1`–`F6` | Switch visualizer mode |
| `F` (hold) | Activate fill triggers |
| `R` | Toggle record arm |
| `Ctrl+Z` / `Ctrl+Shift+Z` | Undo / Redo |
| `Ctrl+S` | Quick save |
| `Ctrl+E` | Toggle CRT effects |

### Mouse

- **Click steps** in the drum grid to toggle them
- **Click + drag knobs** up/down to adjust (Shift for fine control)
- **Click synth sequence steps** then press a note key to enter melody
- **Right-click** synth steps to toggle slide

## VEGA Setup (Optional)

VEGA requires a Claude API key from [Anthropic](https://console.anthropic.com/).

```bash
# Option A: Environment variable
export ANTHROPIC_API_KEY=sk-ant-...

# Option B: Config file
mkdir -p ~/.forge
echo '{"apiKey":"sk-ant-..."}' > ~/.forge/config.json
```

Without an API key, VEGA shows a setup prompt but the entire groovebox works without it.

### Example prompts
- *"make a four-on-the-floor beat at 128 BPM"*
- *"dark ambient pad with slow attack and cathedral reverb"*
- *"heavy distorted bass, like a demon dying"*
- *"add a breakdown section with just kick and reverb"*

## Tech Stack

Everything is **pure Java** — no native code, no C libraries, no external audio engines. The synth DSP runs as math on Java doubles, JSyn handles getting samples to your speakers at 44,100/sec, and JavaFX renders the UI at 60fps. One process, three threads (audio, UI, AI).

| Component | Technology | What it does |
|-----------|-----------|-------------|
| Language | **Java 21** | Modern Java with virtual threads (lightweight async for VEGA API calls) and ZGC (garbage collector that won't freeze your audio mid-beat) |
| UI | **[JavaFX 21](https://openjfx.io/)** | Desktop UI toolkit — all the windows, knobs, buttons, Canvas drawing for visualizers, 3D for terrain mode |
| Synthesis | **[JSyn](https://github.com/philburk/jsyn)** | Audio synthesis library by Phil Burk (the guy who wrote Java's audio spec). Provides oscillators, filters, envelopes, mixers — the building blocks wired together for sound. Runs its own real-time audio thread |
| Filters | **[iirj](https://github.com/berndporr/iirj)** | IIR filter library for Butterworth/Chebyshev filters used in the parametric EQ |
| AI | **[LangChain4j](https://github.com/langchain4j/langchain4j)** + Claude | Connects VEGA to Anthropic's Claude. Handles tool-calling so Claude can invoke `setBPM()`, `setDrumPattern()`, etc. Haiku for fast tweaks, Sonnet for complex composition |
| JSON | **[Gson](https://github.com/google/gson)** | Save/load `.forge` project files, undo snapshots, API key config |
| Build | **[Maven](https://maven.apache.org/)** | Dependency management and build tool. `pom.xml` = Java's `package.json` |

## Project Structure

```
src/main/java/com/forge/
├── ForgeApp.java              # Main application
├── audio/
│   ├── engine/                # Audio engine, FFT, ring buffer
│   ├── synth/                 # 4 synth engines + voice allocator
│   ├── drums/                 # 4 drum synth voices
│   ├── effects/               # 6 effects + chain
│   ├── sequencer/             # Clock, step sequencer, sections, fills
│   └── export/                # WAV + MIDI export
├── midi/                      # MIDI input handling
├── vega/                      # AI assistant (LangChain4j + Claude)
├── ui/
│   ├── theme/                 # DOOM theme, colors, CRT overlay
│   ├── panels/                # Synth, drum, VEGA, transport panels
│   ├── visualizer/            # 6 visualizer renderers
│   └── controls/              # Custom knobs, step buttons, faders
└── model/                     # Data model, persistence, undo
```

## License

MIT
