package com.example.filesystemclientfx;

import java.io.*;
import java.time.Instant;
import java.util.UUID;

public class SessionManager {

    private static final String SESSION_FILE = "session.txt";
    // Session expires after 7 days
    private static final long SESSION_EXPIRY_SECONDS = 7L * 24 * 60 * 60;

    /**
     * Saves session with a random token. The password is NEVER stored on disk.
     * The token is used only to auto-fill the username on next launch.
     * The user still needs to re-authenticate with the real password on session expiry.
     */
    public static void saveSession(String username) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(SESSION_FILE))) {
            String token = UUID.randomUUID().toString();
            long expiry = Instant.now().getEpochSecond() + SESSION_EXPIRY_SECONDS;
            writer.println(username);
            writer.println(token);
            writer.println(expiry);
        } catch (IOException e) {
            System.err.println("Could not save session: " + e.getMessage());
        }
    }

    /**
     * Returns the saved username if session is valid and not expired.
     * Returns null if session is missing, expired, or corrupt.
     */
    public static String loadSessionUsername() {
        File file = new File(SESSION_FILE);
        if (!file.exists()) return null;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String username = reader.readLine();
            String token = reader.readLine();
            String expiryStr = reader.readLine();

            if (username == null || token == null || expiryStr == null) return null;

            long expiry = Long.parseLong(expiryStr.trim());
            if (Instant.now().getEpochSecond() > expiry) {
                clearSession(); // Expired
                return null;
            }

            return username.trim();
        } catch (IOException | NumberFormatException e) {
            System.err.println("Could not load session: " + e.getMessage());
        }
        return null;
    }

    public static void clearSession() {
        new File(SESSION_FILE).delete();
    }
}