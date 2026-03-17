package com.forge.ui.panels;

import com.forge.ui.theme.ForgeColors;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.function.Consumer;

/**
 * VEGA terminal UI panel — right-side 220 px column.
 *
 * <p>Contains:
 * <ol>
 *   <li>Header with V.E.G.A branding</li>
 *   <li>{@link VegaAvatar} eye + mode buttons</li>
 *   <li>Scrollable chat area</li>
 *   <li>Input bar with prompt</li>
 * </ol>
 */
public class VegaPanel extends VBox {

    private static final double PANEL_WIDTH = 220.0;

    // Chat text colours
    private static final Color COLOR_SLAYER = Color.web("#ff8800");  // ARGENT_ORANGE
    private static final Color COLOR_VEGA   = Color.web("#44bbff");  // VEGA_BLUE
    private static final Color COLOR_DIVINE = Color.web("#ffdd44");  // DIVINE_GOLD
    private static final Color COLOR_TREE   = Color.web("#1a5570");  // dim blue tree lines

    private static final Font MONO_11  = Font.font("Monospace", FontWeight.NORMAL, 11);
    private static final Font MONO_11B = Font.font("Monospace", FontWeight.BOLD,   11);
    private static final Font MONO_10  = Font.font("Monospace", FontWeight.NORMAL, 10);

    // ---- Child widgets (set during build, used throughout) ------------------
    private VegaAvatar avatar;
    private VBox       chatBox;
    private ScrollPane chatScroll;
    private TextField  inputField;
    private Label      thinkingLabel;

    // ---- Callbacks ----------------------------------------------------------
    private Consumer<String> onMessageSent;

    // =========================================================================
    // Constructor
    // =========================================================================

    public VegaPanel() {
        super(0);
        setPrefWidth(PANEL_WIDTH);
        setMinWidth(PANEL_WIDTH);
        setMaxWidth(PANEL_WIDTH);
        setStyle(
            "-fx-background-color: #080a0d;" +
            "-fx-border-color: transparent transparent transparent #111a22;" +
            "-fx-border-width: 0 0 0 1;"
        );

        getChildren().addAll(
            buildHeader(),
            buildAvatarSection(),
            buildDivider(),
            buildChatSection(),   // sets chatBox / chatScroll
            buildInputSection()   // sets inputField / thinkingLabel
        );
    }

    // =========================================================================
    // Section builders (each sets the corresponding fields)
    // =========================================================================

    private VBox buildHeader() {
        VBox hdr = new VBox(1);
        hdr.setAlignment(Pos.CENTER);
        hdr.setPadding(new Insets(6, 4, 5, 4));
        hdr.setStyle(
            "-fx-background-color: #050a10;" +
            "-fx-border-color: transparent transparent #0d2030 transparent;" +
            "-fx-border-width: 0 0 1 0;"
        );

        Label title = new Label("\u25C7 V.E.G.A \u25C7");
        title.setFont(Font.font("Monospace", FontWeight.BOLD, 13));
        title.setTextFill(ForgeColors.VEGA_BLUE);
        title.setStyle("-fx-effect: dropshadow(gaussian, #44bbff, 8, 0.4, 0, 0);");

        Label subtitle = new Label("VIRTUAL ENHANCED GROOVE ASSISTANT");
        subtitle.setFont(Font.font("Monospace", FontWeight.NORMAL, 7));
        subtitle.setTextFill(Color.web("#1a3a52"));
        subtitle.setWrapText(true);
        subtitle.setAlignment(Pos.CENTER);

        hdr.getChildren().addAll(title, subtitle);
        return hdr;
    }

    private VBox buildAvatarSection() {
        avatar = new VegaAvatar();

        VBox sec = new VBox(0);
        sec.setAlignment(Pos.CENTER);
        sec.setStyle(
            "-fx-background-color: #070a0e;" +
            "-fx-border-color: transparent transparent #0d2030 transparent;" +
            "-fx-border-width: 0 0 1 0;"
        );
        sec.getChildren().add(avatar);
        return sec;
    }

    private Region buildDivider() {
        Region r = new Region();
        r.setPrefHeight(1);
        r.setMinHeight(1);
        r.setMaxHeight(1);
        r.setStyle("-fx-background-color: #0d2030;");
        return r;
    }

    private ScrollPane buildChatSection() {
        chatBox = new VBox(2);
        chatBox.setPadding(new Insets(4, 4, 4, 4));
        chatBox.setStyle("-fx-background-color: #060a0d;");

        chatScroll = new ScrollPane(chatBox);
        chatScroll.setFitToWidth(true);
        chatScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chatScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        chatScroll.setStyle(
            "-fx-background-color: #060a0d;" +
            "-fx-background: #060a0d;" +
            "-fx-border-color: transparent;"
        );
        VBox.setVgrow(chatScroll, Priority.ALWAYS);

        // Auto-scroll to bottom whenever content grows
        chatBox.heightProperty().addListener((obs, oldH, newH) ->
            Platform.runLater(() -> chatScroll.setVvalue(1.0))
        );

        return chatScroll;
    }

    private VBox buildInputSection() {
        VBox section = new VBox(0);
        section.setStyle(
            "-fx-background-color: #070a0d;" +
            "-fx-border-color: #0d2030 transparent transparent transparent;" +
            "-fx-border-width: 1 0 0 0;"
        );

        // "Thinking..." indicator (hidden by default)
        thinkingLabel = new Label("Thinking\u2026");
        thinkingLabel.setFont(Font.font("Monospace", FontWeight.NORMAL, 10));
        thinkingLabel.setTextFill(Color.web("#225577"));
        thinkingLabel.setPadding(new Insets(2, 6, 0, 6));
        thinkingLabel.setVisible(false);
        thinkingLabel.setManaged(false);

        // Prompt row: ▸ + text field
        HBox promptRow = new HBox(4);
        promptRow.setAlignment(Pos.CENTER_LEFT);
        promptRow.setPadding(new Insets(4, 4, 4, 4));

        Label prompt = new Label("\u25B8");
        prompt.setFont(Font.font("Monospace", FontWeight.BOLD, 12));
        prompt.setTextFill(ForgeColors.ARGENT_ORANGE);

        inputField = new TextField();
        inputField.setFont(Font.font("Monospace", FontWeight.NORMAL, 11));
        inputField.setStyle(
            "-fx-background-color: #0a0f14;" +
            "-fx-text-fill: #aaccdd;" +
            "-fx-border-color: #0d2030;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 0;" +
            "-fx-background-radius: 0;" +
            "-fx-prompt-text-fill: #224455;" +
            "-fx-highlight-fill: #0d4466;"
        );
        inputField.setPromptText("command...");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        inputField.setOnAction(e -> {
            String text = inputField.getText().trim();
            if (!text.isEmpty() && onMessageSent != null) {
                onMessageSent.accept(text);
            }
        });

        promptRow.getChildren().addAll(prompt, inputField);
        section.getChildren().addAll(thinkingLabel, promptRow);
        return section;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Appends a SLAYER message: orange "SLAYER>" prefix + light body text.
     */
    public void addSlayerMessage(String text) {
        Platform.runLater(() -> chatBox.getChildren().add(buildSlayerFlow(text)));
    }

    /**
     * Appends a VEGA (or DIVINE) response, formatting multi-line text with
     * tree-style prefixes and status symbols.
     */
    public void addVegaMessage(String text) {
        Platform.runLater(() -> {
            boolean divine = avatar != null && avatar.isDivineMode();
            Color   prefixColor = divine ? COLOR_DIVINE : COLOR_VEGA;
            String  prefixLabel = divine ? "THE FATHER>" : "VEGA>";

            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                TextFlow flow = (i == 0)
                    ? buildPrefixedFlow(prefixLabel, prefixColor, lines[i])
                    : buildContinuationFlow(lines[i]);
                chatBox.getChildren().add(flow);
            }
        });
    }

    /**
     * Appends a plain system / status message in the given colour.
     */
    public void addSystemMessage(String text, Color color) {
        Platform.runLater(() -> {
            Text t = new Text(text + "\n");
            t.setFont(MONO_10);
            t.setFill(color);
            TextFlow flow = new TextFlow(t);
            flow.setPadding(new Insets(0, 2, 0, 2));
            chatBox.getChildren().add(flow);
        });
    }

    /** Shows or hides the "Thinking…" indicator. */
    public void setThinking(boolean thinking) {
        Platform.runLater(() -> {
            thinkingLabel.setVisible(thinking);
            thinkingLabel.setManaged(thinking);
        });
    }

    /** Returns the current contents of the input field. */
    public String getInputText() {
        return inputField != null ? inputField.getText() : "";
    }

    /** Clears the input field (safe to call from any thread). */
    public void clearInput() {
        if (inputField != null) {
            Platform.runLater(() -> inputField.clear());
        }
    }

    /**
     * Registers a callback invoked when the user presses Enter.
     * The consumer receives the trimmed input text.
     * The panel does <em>not</em> clear the field automatically;
     * call {@link #clearInput()} from within the handler.
     */
    public void setOnMessageSent(Consumer<String> handler) {
        this.onMessageSent = handler;
    }

    /** Returns true when the avatar is in DIVINE mode. */
    public boolean isDivineMode() {
        return avatar != null && avatar.isDivineMode();
    }

    // =========================================================================
    // Private formatting helpers
    // =========================================================================

    private TextFlow buildSlayerFlow(String text) {
        Text prefix = new Text("SLAYER> ");
        prefix.setFont(MONO_11B);
        prefix.setFill(COLOR_SLAYER);

        Text body = new Text(text + "\n");
        body.setFont(MONO_11);
        body.setFill(Color.web("#ccddee"));

        TextFlow flow = new TextFlow(prefix, body);
        flow.setPadding(new Insets(1, 2, 1, 2));
        return flow;
    }

    private TextFlow buildPrefixedFlow(String prefixLabel, Color prefixColor, String text) {
        Text prefix = new Text(prefixLabel + " ");
        prefix.setFont(MONO_11B);
        prefix.setFill(prefixColor);

        Text body = styledBody(text);

        TextFlow flow = new TextFlow(prefix, body);
        flow.setPadding(new Insets(1, 2, 1, 2));
        return flow;
    }

    private TextFlow buildContinuationFlow(String line) {
        String trimmed = line.trim();
        Text content;

        if (trimmed.startsWith("\u251C\u2500") || trimmed.startsWith("\u2514\u2500")) {
            // ├─ or └─  tree branch
            content = new Text("      " + line + "\n");
            content.setFont(MONO_10);
            content.setFill(COLOR_TREE);
        } else if (trimmed.startsWith("\u26A1")) {  // ⚡
            content = new Text("      " + line + "\n");
            content.setFont(MONO_10);
            content.setFill(ForgeColors.ARGENT_AMBER);
        } else if (trimmed.startsWith("\u2713") || trimmed.startsWith("\u2714")) {  // ✓ ✔
            content = new Text("      " + line + "\n");
            content.setFont(MONO_10);
            content.setFill(Color.web("#44cc66"));
        } else if (trimmed.startsWith("\u2746")) {  // ✦
            content = new Text("      " + line + "\n");
            content.setFont(MONO_10);
            content.setFill(ForgeColors.DIVINE_GOLD);
        } else {
            content = styledBody("      " + line);
        }

        TextFlow flow = new TextFlow(content);
        flow.setPadding(new Insets(0, 2, 0, 2));
        return flow;
    }

    private Text styledBody(String text) {
        Text t = new Text(text + "\n");
        t.setFont(MONO_11);
        t.setFill(Color.web("#8abbcc"));
        return t;
    }
}
