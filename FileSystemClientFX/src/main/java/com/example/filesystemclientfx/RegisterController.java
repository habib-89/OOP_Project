package com.example.filesystemclientfx;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class RegisterController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label messageLabel;

    private NetworkManager network = new NetworkManager();

    @FXML
    private void handleRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();

        // Validation
        if (username.isEmpty() || password.isEmpty()) {
            showError("Username and password cannot be empty.");
            return;
        }

        if (username.contains(" ")) {
            showError("Username cannot contain spaces.");
            return;
        }

        if (password.length() < 4) {
            showError("Password must be at least 4 characters.");
            return;
        }

        if (!password.equals(confirm)) {
            showError("Passwords do not match.");
            return;
        }

        try {
            network.connect();
            String response = network.sendMessage(
                    "REGISTER " + username + " " + password);

            if (response.equals("REGISTER_SUCCESS")) {
                messageLabel.setStyle("-fx-text-fill: #10b981;");
                messageLabel.setText("✅ Account created! You can now sign in.");

                // Auto close after 2 seconds and go back to login
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        javafx.application.Platform.runLater(this::handleBackToLogin);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();

            } else {
                showError("❌ " + response.replace("REGISTER_FAIL ", ""));
            }

        } catch (Exception e) {
            showError("⚠ Connection error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBackToLogin() {
        try {
            Stage currentStage = (Stage) usernameField.getScene().getWindow();
            currentStage.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showError(String message) {
        messageLabel.setStyle("-fx-text-fill: #ef4444;");
        messageLabel.setText(message);
    }
}