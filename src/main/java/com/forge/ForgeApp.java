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
