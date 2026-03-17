module com.forge {
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.media;
    requires com.google.gson;
    requires langchain4j;
    requires langchain4j.anthropic;
    requires iirj;
    requires java.desktop; // for javax.sound.midi, javax.sound.sampled
    requires java.sql;     // needed by JSyn at runtime
    requires jsyn;

    opens com.forge.model to com.google.gson; // Gson reflection for persistence

    exports com.forge;
    exports com.forge.model;
    exports com.forge.audio.engine;
    exports com.forge.audio.synth;
    exports com.forge.audio.drums;
    exports com.forge.audio.sequencer;
    exports com.forge.audio.effects;
    exports com.forge.ui.theme;
    exports com.forge.ui.controls;
    exports com.forge.ui.panels;
}
