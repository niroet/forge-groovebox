package com.forge;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * Minimal layout test — no audio, no animations, no overlays.
 * Just the 3-panel structure to verify layout works.
 */
public class ForgeMinimal extends Application {

    @Override
    public void start(Stage stage) {
        BorderPane main = new BorderPane();
        main.setStyle("-fx-background-color: #080808;");

        // Top: simple title + menu
        VBox top = new VBox();
        HBox titleBar = new HBox();
        titleBar.setStyle("-fx-background-color: linear-gradient(to right, #1a0000, #330000, #1a0000); -fx-padding: 6;");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("FORGE.EXE — Minimal Layout Test");
        title.setFont(Font.font("Monospace", FontWeight.BOLD, 13));
        title.setTextFill(Color.web("#ff6600"));
        titleBar.getChildren().add(title);

        HBox menuBar = new HBox(12);
        menuBar.setStyle("-fx-background-color: #111; -fx-padding: 3 8;");
        for (String m : new String[]{"Protocol", "Edit", "Synth", "Drums", "VEGA"}) {
            Label l = new Label(m);
            l.setFont(Font.font("Monospace", 11));
            l.setTextFill(Color.web("#aaa"));
            menuBar.getChildren().add(l);
        }
        top.getChildren().addAll(titleBar, menuBar);
        main.setTop(top);

        // Center: 3 panels
        HBox center = new HBox(0);
        center.setFillHeight(true);

        // Left panel (synth) - fixed width
        VBox left = makePanel("SYNTH PANEL", 240, "#ff4400");
        left.setMaxHeight(Double.MAX_VALUE);

        // Center column
        VBox mid = new VBox(0);
        mid.setStyle("-fx-background-color: #0a0a0a;");
        mid.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(mid, Priority.ALWAYS);

        VBox vizArea = new VBox();
        vizArea.setStyle("-fx-background-color: #050505;");
        vizArea.setAlignment(Pos.CENTER);
        Label vizLabel = new Label("VISUALIZER AREA");
        vizLabel.setFont(Font.font("Monospace", 16));
        vizLabel.setTextFill(Color.web("#ff440033"));
        vizArea.getChildren().add(vizLabel);
        VBox.setVgrow(vizArea, Priority.ALWAYS);

        VBox drumArea = makePanel("DRUM GRID + TRANSPORT", 0, "#ff8800");
        drumArea.setPrefHeight(220);
        drumArea.setMinHeight(180);
        drumArea.setMaxHeight(260);

        mid.getChildren().addAll(vizArea, drumArea);

        // Right panel (VEGA) - fixed width
        VBox right = makePanel("VEGA TERMINAL", 220, "#44bbff");
        right.setMaxHeight(Double.MAX_VALUE);

        center.getChildren().addAll(left, mid, right);
        main.setCenter(center);

        // Bottom: status bar
        HBox status = new HBox();
        status.setStyle("-fx-background-color: #111; -fx-padding: 3 8;");
        Label sl = new Label("VEGA:ONLINE | AUDIO:ACTIVE | 128 BPM | 5.8ms | 44.1kHz");
        sl.setFont(Font.font("Monospace", 9));
        sl.setTextFill(Color.web("#666"));
        status.getChildren().add(sl);
        main.setBottom(status);

        Scene scene = new Scene(main, 1200, 700, Color.web("#080808"));
        stage.setTitle("FORGE — Minimal Layout Test");
        stage.setScene(scene);
        stage.show();
    }

    private VBox makePanel(String name, double width, String color) {
        VBox p = new VBox(8);
        p.setPadding(new Insets(10));
        p.setStyle("-fx-background-color: #0a0a0a; -fx-border-color: #222; -fx-border-width: 0 1 0 0;");
        if (width > 0) {
            p.setPrefWidth(width);
            p.setMinWidth(width);
            p.setMaxWidth(width);
        }
        Label l = new Label(name);
        l.setFont(Font.font("Monospace", FontWeight.BOLD, 11));
        l.setTextFill(Color.web(color));
        p.getChildren().add(l);
        return p;
    }

    public static void main(String[] args) { launch(args); }
}
