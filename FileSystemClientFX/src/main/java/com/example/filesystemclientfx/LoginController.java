package com.example.filesystemclientfx;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;
    @FXML private Canvas bgCanvas;
    @FXML private Label subtitleLabel;

    private NetworkManager network = new NetworkManager();
    private AnimationTimer particleTimer;
    private List<Particle> particles = new ArrayList<>();
    private Random random = new Random();

    private double scanlineY = 0;
    private String fullText = "// SECURE FILE MANAGEMENT SYSTEM v2.0";
    private int typingIndex = 0;

    @FXML
    public void initialize() {
        // FIX: only restore username, never the password
        if (System.getProperty("filevault.nosession") == null) {
            String savedUsername = SessionManager.loadSessionUsername();
            if (savedUsername != null) {
                usernameField.setText(savedUsername);
                // Let user type their own password
            }
        }

        for (int i = 0; i < 60; i++) {
            particles.add(new Particle(420, 580));
        }

        javafx.application.Platform.runLater(() -> {
            startParticleAnimation();
            startTypingEffect();
        });
    }

    private void startParticleAnimation() {
        GraphicsContext gc = bgCanvas.getGraphicsContext2D();

        particleTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                gc.setFill(Color.web("#020408"));
                gc.fillRect(0, 0, 420, 580);

                gc.setStroke(Color.web("#00d4ff", 0.04));
                gc.setLineWidth(0.5);
                for (int x = 0; x < 420; x += 30) gc.strokeLine(x, 0, x, 580);
                for (int y = 0; y < 580; y += 30) gc.strokeLine(0, y, 420, y);

                for (Particle p : particles) {
                    p.update();

                    double glowSize = p.size * 4;
                    gc.setFill(Color.web("#00d4ff", p.opacity * 0.15));
                    gc.fillOval(p.x - glowSize / 2, p.y - glowSize / 2, glowSize, glowSize);

                    gc.setFill(Color.web("#00d4ff", p.opacity * 0.4));
                    gc.fillOval(p.x - p.size, p.y - p.size, p.size * 2, p.size * 2);

                    for (Particle other : particles) {
                        double dist = Math.sqrt(Math.pow(p.x - other.x, 2) + Math.pow(p.y - other.y, 2));
                        if (dist < 80 && dist > 0) {
                            double lineOpacity = (1 - dist / 80) * 0.12;
                            gc.setStroke(Color.web("#00d4ff", lineOpacity));
                            gc.setLineWidth(0.5);
                            gc.strokeLine(p.x, p.y, other.x, other.y);
                        }
                    }
                }

                scanlineY += 1.5;
                if (scanlineY > 580) scanlineY = 0;
                gc.setFill(Color.web("#00d4ff", 0.06));
                gc.fillRect(0, scanlineY, 420, 3);
                gc.setFill(Color.web("#00d4ff", 0.03));
                gc.fillRect(0, scanlineY - 10, 420, 10);
            }
        };
        particleTimer.start();
    }

    private void startTypingEffect() {
        Timeline typingTimeline = new Timeline(
                new KeyFrame(Duration.millis(60), e -> {
                    if (typingIndex < fullText.length()) {
                        subtitleLabel.setText(fullText.substring(0, ++typingIndex) + "█");
                    } else {
                        subtitleLabel.setText(fullText);
                        blinkCursor();
                    }
                })
        );
        typingTimeline.setCycleCount(fullText.length() + 1);
        typingTimeline.play();
    }

    private void blinkCursor() {
        Timeline blink = new Timeline(
                new KeyFrame(Duration.millis(500), e -> {
                    String current = subtitleLabel.getText();
                    subtitleLabel.setText(current.endsWith("█") ? fullText : fullText + "█");
                })
        );
        blink.setCycleCount(Timeline.INDEFINITE);
        blink.play();
    }

    private class Particle {
        double x, y, vx, vy, size, opacity, maxOpacity;
        double width, height;

        Particle(double width, double height) {
            this.width = width;
            this.height = height;
            reset();
            x = random.nextDouble() * width;
            y = random.nextDouble() * height;
        }

        void reset() {
            x = random.nextDouble() * width;
            y = random.nextDouble() * height;
            vx = (random.nextDouble() - 0.5) * 0.6;
            vy = (random.nextDouble() - 0.5) * 0.6;
            size = 1 + random.nextDouble() * 2.5;
            maxOpacity = 0.3 + random.nextDouble() * 0.7;
            opacity = random.nextDouble() * maxOpacity;
        }

        void update() {
            x += vx;
            y += vy;
            if (x < 0 || x > width) vx *= -1;
            if (y < 0 || y > height) vy *= -1;
            opacity += (random.nextDouble() - 0.5) * 0.05;
            opacity = Math.max(0.05, Math.min(maxOpacity, opacity));
        }
    }

    private void stopAnimations() {
        if (particleTimer != null) particleTimer.stop();
    }

    @FXML
    private void handleLogin() {
        try {
            network.setServerHost("192.168.1.4");
            network.connect();
            String username = usernameField.getText().trim();
            String password = passwordField.getText();

            if (username.isEmpty() || password.isEmpty()) {
                messageLabel.setText("Please enter username and password.");
                return;
            }

            String response = network.login(username, password);

            if (response.equals("SUCCESS")) {
                // FIX: save only username, not password
                SessionManager.saveSession(username);
                stopAnimations();
                openDashboard(username);
            } else {
                messageLabel.setText("❌ Invalid username or password.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            messageLabel.setText("⚠ Connection error: " + e.getMessage());
        }
    }

    @FXML
    private void handleShowRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Register.fxml"));
            Scene scene = new Scene(loader.load(), 420, 520);

            var cssUrl = getClass().getResource("login.css");
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());

            Stage stage = new Stage();
            stage.setTitle("FileVault — Register");
            stage.setScene(scene);
            stage.show();

        } catch (Exception e) {
            e.printStackTrace();
            messageLabel.setText("Could not open register screen.");
        }
    }

    private void openDashboard(String username) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("Dashboard.fxml"));
        Scene scene = new Scene(loader.load(), 950, 650);

        var cssUrl = getClass().getResource("dashboard.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());

        DashboardController controller = loader.getController();
        controller.setUsername(username);
        controller.setNetwork(network);

        Stage stage = new Stage();
        stage.setTitle("FileVault");
        stage.setScene(scene);
        stage.show();

        Stage currentStage = (Stage) usernameField.getScene().getWindow();
        currentStage.close();
    }
}