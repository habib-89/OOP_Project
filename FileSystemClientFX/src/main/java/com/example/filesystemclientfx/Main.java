package com.example.filesystemclientfx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("Login.fxml"));
        Scene scene = new Scene(loader.load(), 580, 650);

        // Safely load CSS
        try {
            var cssUrl = getClass().getResource("login.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            } else {
                System.out.println("WARNING: login.css not found!");
            }
        } catch (Exception e) {
            System.out.println("CSS load error: " + e.getMessage());
        }

        stage.setTitle("FileVault");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}