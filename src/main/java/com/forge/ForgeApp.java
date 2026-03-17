package com.forge;

import com.forge.ui.theme.CrtOverlay;
import com.forge.ui.theme.ForgeColors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * FORGE.EXE — 3-panel DOOM shell.
 *
 * Layout:
 *   StackPane (root)
 *   ├── BorderPane (main)
 *   │   ├── Top:    title bar + menu bar
 *   │   ├── Center: HBox (left synth | center visualizer+drums | right VEGA)
 *   │   └── Bottom: status bar
 *   └── CrtOverlay (scanlines + vignette, mouse-transparent)
 */
public class ForgeApp extends Application {

    private static final int WIDTH  = 1280;
    private static final int HEIGHT = 780;

    // Drag state for custom title bar
    private double dragOffsetX, dragOffsetY;

    @Override
    public void start(Stage stage) {
        BorderPane main = new BorderPane();
        main.setStyle("-fx-background-color: #080808;");

        // Top section: title bar + menu bar
        VBox topSection = new VBox();
        topSection.getChildren().addAll(buildTitleBar(stage), buildMenuBar());
        main.setTop(topSection);

        // Center: 3 panels
        main.setCenter(buildCenterPanels());

        // Bottom: status bar
        main.setBottom(buildStatusBar());

        // CRT overlay on top of everything
        CrtOverlay crt = new CrtOverlay();

        StackPane root = new StackPane(main, crt);
        root.setStyle("-fx-background-color: #080808;");

        // Bind CRT overlay to root size
        crt.prefWidthProperty() .bind(root.widthProperty());
        crt.prefHeightProperty().bind(root.heightProperty());

        Scene scene = new Scene(root, WIDTH, HEIGHT, Color.web("#080808"));

        // Load CSS
        String css = getClass().getResource("/css/forge-theme.css") != null
            ? getClass().getResource("/css/forge-theme.css").toExternalForm()
            : null;
        if (css != null) {
            scene.getStylesheets().add(css);
        }

        stage.setTitle("FORGE.EXE — Sound Terminal v2.016");
        stage.setScene(scene);
        stage.show();
    }

    // =========================================================================
    // Title bar
    // =========================================================================

    private HBox buildTitleBar(Stage stage) {
        // Win98-style: gradient #1a0000 → #330000, white bold text, orange glow
        HBox bar = new HBox();
        bar.setStyle(
            "-fx-background-color: linear-gradient(to right, #1a0000, #330000, #1a0000);" +
            "-fx-padding: 3 6 3 8;" +
            "-fx-border-color: transparent transparent #441100 transparent;" +
            "-fx-border-width: 0 0 1 0;"
        );
        bar.setAlignment(Pos.CENTER_LEFT);

        // Icon + title
        Label title = new Label("\u2B21 FORGE.EXE \u2014 Sound Terminal v2.016");
        title.setFont(Font.font("Monospace", FontWeight.BOLD, 12));
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-effect: dropshadow(gaussian, #ff6600, 6, 0.5, 0, 0);");

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Win98 window control buttons
        Button btnMin   = makeWinBtn("_",  false);
        Button btnMax   = makeWinBtn("\u25A1", false);
        Button btnClose = makeWinBtn("\u00D7", true);

        btnClose.setOnAction(e -> Platform.exit());

        bar.getChildren().addAll(title, spacer, btnMin, btnMax, btnClose);

        // Drag window with custom title bar
        bar.setOnMousePressed(e -> {
            dragOffsetX = e.getSceneX();
            dragOffsetY = e.getSceneY();
        });
        bar.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        });

        return bar;
    }

    private Button makeWinBtn(String text, boolean isClose) {
        Button btn = new Button(text);
        String base =
            "-fx-background-color: linear-gradient(to bottom, #3a3a3a, #1a1a1a);" +
            "-fx-border-color: #555555 #1a1a1a #1a1a1a #555555;" +
            "-fx-border-width: 1px;" +
            "-fx-text-fill: #cccccc;" +
            "-fx-font-family: Monospace;" +
            "-fx-font-size: 10px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 0 5 0 5;" +
            "-fx-min-width: 16px;" +
            "-fx-min-height: 14px;" +
            "-fx-max-height: 14px;";
        btn.setStyle(base);

        String hoverStyle = isClose
            ? base + "-fx-background-color: #660000; -fx-text-fill: #ff4444;"
            : base + "-fx-background-color: #441100; -fx-text-fill: #ff6600;";

        btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(e -> btn.setStyle(base));
        return btn;
    }

    // =========================================================================
    // Menu bar
    // =========================================================================

    private HBox buildMenuBar() {
        HBox bar = new HBox(0);
        bar.setStyle(
            "-fx-background-color: #0d0d0d;" +
            "-fx-border-color: transparent transparent #222222 transparent;" +
            "-fx-border-width: 0 0 1 0;" +
            "-fx-padding: 0 4 0 4;"
        );
        bar.setAlignment(Pos.CENTER_LEFT);

        String[] menus = {
            "Protocol", "Edit", "Synth.Array", "Drum.Seq",
            "VEGA", "Export", "Diagnostics"
        };

        for (String m : menus) {
            Label lbl = new Label(m);
            lbl.setFont(Font.font("Monospace", FontWeight.NORMAL, 11));
            lbl.setTextFill(Color.web("#aaaaaa"));
            lbl.setPadding(new Insets(2, 8, 2, 8));
            lbl.setStyle("-fx-cursor: hand;");
            lbl.setOnMouseEntered(e -> {
                lbl.setTextFill(Color.web("#ff8800"));
                lbl.setStyle("-fx-cursor: hand; -fx-background-color: #1a0800;");
            });
            lbl.setOnMouseExited(e -> {
                lbl.setTextFill(Color.web("#aaaaaa"));
                lbl.setStyle("-fx-cursor: hand; -fx-background-color: transparent;");
            });
            bar.getChildren().add(lbl);
        }

        return bar;
    }

    // =========================================================================
    // Center — 3 panels
    // =========================================================================

    private HBox buildCenterPanels() {
        HBox center = new HBox(0);

        VBox synthPanel     = buildPanel("SYNTH PANEL",         240, -1, ForgeColors.ARGENT_AMBER);
        VBox visPanel       = buildPanel("VISUALIZER + DRUMS",  -1,  -1, ForgeColors.VEGA_BLUE);
        VBox vegaPanel      = buildPanel("VEGA TERMINAL",       220, -1, ForgeColors.VEGA_CYAN);

        // Center panel gets all remaining width
        HBox.setHgrow(visPanel, Priority.ALWAYS);

        center.getChildren().addAll(synthPanel, visPanel, vegaPanel);
        return center;
    }

    private VBox buildPanel(String name, double prefW, double prefH, Color accentColor) {
        VBox panel = new VBox();
        panel.setStyle(
            "-fx-background-color: #0a0a0a;" +
            "-fx-border-color: #222222;" +
            "-fx-border-width: 0 1 0 0;"  // right border only (divider)
        );

        if (prefW > 0) {
            panel.setPrefWidth(prefW);
            panel.setMinWidth(prefW);
            panel.setMaxWidth(prefW);
        }
        if (prefH > 0) {
            panel.setPrefHeight(prefH);
        }

        // Panel header strip
        HBox header = new HBox();
        header.setStyle(
            "-fx-background-color: #0d0d0d;" +
            "-fx-border-color: transparent transparent #1a1a1a transparent;" +
            "-fx-border-width: 0 0 1 0;" +
            "-fx-padding: 3 8 3 8;"
        );
        header.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(name);
        nameLabel.setFont(Font.font("Monospace", FontWeight.BOLD, 10));
        nameLabel.setTextFill(accentColor.deriveColor(0, 1, 0.5, 0.7));

        // Small accent dot
        Region dot = new Region();
        dot.setPrefSize(4, 4);
        dot.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 2;",
            ForgeColors.hex(accentColor)
        ));
        HBox.setMargin(dot, new Insets(0, 6, 0, 0));

        header.getChildren().addAll(dot, nameLabel);

        // Placeholder content
        VBox content = new VBox();
        VBox.setVgrow(content, Priority.ALWAYS);
        content.setAlignment(Pos.CENTER);

        Label placeholder = new Label(name);
        placeholder.setFont(Font.font("Monospace", FontWeight.NORMAL, 11));
        placeholder.setTextFill(Color.web("#2a2a2a"));

        content.getChildren().add(placeholder);

        panel.getChildren().addAll(header, content);
        VBox.setVgrow(panel, Priority.ALWAYS);

        return panel;
    }

    // =========================================================================
    // Status bar
    // =========================================================================

    private HBox buildStatusBar() {
        HBox bar = new HBox();
        bar.setStyle(
            "-fx-background-color: #111111;" +
            "-fx-border-color: #222222 transparent transparent transparent;" +
            "-fx-border-width: 1 0 0 0;" +
            "-fx-padding: 3 10 3 10;"
        );
        bar.setAlignment(Pos.CENTER_LEFT);

        Label left = makeStatusLabel(
            "\u25C7 VEGA:ONLINE  \u25CF AUDIO:ACTIVE  VERSE[4/8]  Dm"
        );
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label right = makeStatusLabel(
            "CPU:23%  |  5.8ms  |  44.1kHz  |  MIDI:CH1"
        );

        bar.getChildren().addAll(left, spacer, right);
        return bar;
    }

    private Label makeStatusLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Monospace", FontWeight.NORMAL, 10));
        l.setTextFill(Color.web("#666666"));
        return l;
    }

    // =========================================================================

    public static void main(String[] args) {
        launch(args);
    }
}
