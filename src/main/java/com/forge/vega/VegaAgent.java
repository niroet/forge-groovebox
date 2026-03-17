package com.forge.vega;

import com.forge.audio.drums.DrumEngine;
import com.forge.audio.effects.EffectsChain;
import com.forge.audio.sequencer.SequencerClock;
import com.forge.audio.sequencer.StepSequencer;
import com.forge.audio.synth.VoiceAllocator;
import com.forge.model.ProjectState;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Orchestrates the LangChain4j + Anthropic Claude agent for VEGA.
 *
 * <p>All model calls run on a virtual thread so the JavaFX UI is never blocked.
 * Responses (and errors) are delivered via the {@code onResponse} callback, which
 * implementations should marshal back to the FX thread if needed.
 */
public class VegaAgent {

    // -------------------------------------------------------------------------
    // Inner interface — LangChain4j AiServices proxy
    // -------------------------------------------------------------------------

    /**
     * LangChain4j dynamic proxy interface.
     *
     * <p>The {@code @SystemMessage} here is a placeholder that gets overridden at
     * runtime via {@code systemMessageProvider} on the builder.
     */
    interface VegaAssistant {
        String chat(String userMessage);
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final VegaTools    tools;
    private final ProjectState state;
    private final SequencerClock clock;

    /** Whether the DIVINE/Father persona is active. */
    private volatile boolean divineMode = false;

    // Keep per-session chat memory so VEGA has context within a session.
    private MessageWindowChatMemory chatMemory;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public VegaAgent(
            ProjectState   state,
            DrumEngine     drumEngine,
            VoiceAllocator voiceAllocator,
            SequencerClock clock,
            StepSequencer  sequencer,
            EffectsChain   effectsChain) {
        this.state = state;
        this.clock = clock;
        this.tools = new VegaTools(state, drumEngine, voiceAllocator, clock, sequencer, effectsChain);
        resetMemory();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Switch between ASSIST and DIVINE personality. */
    public void setDivineMode(boolean divine) {
        this.divineMode = divine;
    }

    /** Returns the current persona mode. */
    public boolean isDivineMode() {
        return divineMode;
    }

    /**
     * Send a user message to VEGA and receive a response asynchronously.
     *
     * <p>The {@code onResponse} consumer is called from the virtual thread — callers
     * on the JavaFX thread must use {@code Platform.runLater()} if they touch UI nodes.
     *
     * @param userMessage the text the user typed
     * @param onResponse  callback that receives the final response string
     */
    public void sendMessage(String userMessage, Consumer<String> onResponse) {
        if (!VegaConfig.hasApiKey()) {
            onResponse.accept(
                "⚠ No API key configured. Set ANTHROPIC_API_KEY or use VEGA → Settings.");
            return;
        }

        // Capture mode snapshot so the virtual thread sees a consistent value
        boolean divine = this.divineMode;

        Thread.ofVirtual().start(() -> {
            try {
                // Pick model: haiku for assist, sonnet for divine
                String modelName = divine
                        ? "claude-sonnet-4-5-20250514"
                        : "claude-haiku-4-5-20251001";

                AnthropicChatModel model = AnthropicChatModel.builder()
                        .apiKey(VegaConfig.getApiKey())
                        .modelName(modelName)
                        .maxTokens(1024)
                        .timeout(Duration.ofSeconds(60))
                        .build();

                // Build groovebox state description and system prompt
                String stateDesc   = GrooveboxState.describe(state, clock.isPlaying(), clock.getCurrentStep());
                String systemPrompt = VegaSystemPrompt.build(divine, stateDesc);

                VegaAssistant assistant = AiServices.builder(VegaAssistant.class)
                        .chatLanguageModel(model)
                        .tools(tools)
                        .chatMemory(chatMemory)
                        .systemMessageProvider(id -> systemPrompt)
                        .build();

                String response = assistant.chat(userMessage);
                onResponse.accept(response != null ? response : "(no response)");

            } catch (Exception e) {
                System.err.println("[VEGA] Agent error: " + e.getMessage());
                onResponse.accept("⚠ VEGA error: " + e.getMessage());
            }
        });
    }

    /**
     * Clear conversation history (start a fresh session context).
     */
    public void resetMemory() {
        chatMemory = MessageWindowChatMemory.withMaxMessages(20);
    }
}
