package com.example.filesystemclientfx;

import java.io.*;

public class SessionManager {

    private static final String SESSION_FILE = "session.txt";

    public static void saveSession(String username, String password) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(SESSION_FILE))) {
            writer.println(username);
            writer.println(password);
        } catch (IOException e) {
            System.out.println("Could not save session: " + e.getMessage());
        }
    }

    public static String[] loadSession() {
        File file = new File(SESSION_FILE);
        if (!file.exists()) return null;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String username = reader.readLine();
            String password = reader.readLine();
            if (username != null && password != null) {
                return new String[]{username, password};
            }
        } catch (IOException e) {
            System.out.println("Could not load session: " + e.getMessage());
        }
        return null;
    }

    public static void clearSession() {
        new File(SESSION_FILE).delete();
    }
}