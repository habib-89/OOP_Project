package com.example.filesystemclientfx;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class BookmarkManager {

    private static final String BOOKMARKS_FILE = "bookmarks.txt";

    public static class Bookmark {
        public String name;
        public String path;
        public boolean isFolder;

        public Bookmark(String name, String path, boolean isFolder) {
            this.name = name;
            this.path = path;
            this.isFolder = isFolder;
        }

        public String getIcon() {
            return isFolder ? "📁  " : "📄  ";
        }

        public String getDisplayName() {
            return getIcon() + name;
        }
    }

    public static List<Bookmark> loadBookmarks(String username) {
        List<Bookmark> bookmarks = new ArrayList<>();
        File file = new File(BOOKMARKS_FILE);
        if (!file.exists()) return bookmarks;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                // Format: username|name|path|isFolder
                if (parts.length == 4 && parts[0].equals(username)) {
                    bookmarks.add(new Bookmark(
                            parts[1], parts[2],
                            parts[3].equals("true")));
                }
            }
        } catch (IOException e) {
            System.out.println("Error loading bookmarks: " + e.getMessage());
        }
        return bookmarks;
    }

    public static void saveBookmark(String username, Bookmark bookmark) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(BOOKMARKS_FILE, true))) {
            writer.println(username + "|" + bookmark.name + "|" +
                    bookmark.path + "|" + bookmark.isFolder);
        } catch (IOException e) {
            System.out.println("Error saving bookmark: " + e.getMessage());
        }
    }

    public static void deleteBookmark(String username, String path) {
        File file = new File(BOOKMARKS_FILE);
        if (!file.exists()) return;

        List<String> remaining = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (!(parts.length == 4 &&
                        parts[0].equals(username) &&
                        parts[2].equals(path))) {
                    remaining.add(line);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading bookmarks: " + e.getMessage());
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(file, false))) {
            for (String line : remaining) writer.println(line);
        } catch (IOException e) {
            System.out.println("Error saving bookmarks: " + e.getMessage());
        }
    }

    public static boolean isBookmarked(String username, String path) {
        for (Bookmark b : loadBookmarks(username)) {
            if (b.path.equals(path)) return true;
        }
        return false;
    }
}