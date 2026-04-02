package com.example.filesystemclientfx;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class NetworkManager {

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private String serverHost = "localhost";

    public void setServerHost(String host) {
        this.serverHost = host.trim();
    }

    public String getServerHost() {
        return serverHost;
    }

    public void connect() throws Exception {
        socket = new Socket(serverHost, 5001);
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }

    public String sendMessage(String message) throws Exception {
        out.writeUTF(message);
        return in.readUTF();
    }

    public ListResult listDir(String path) throws Exception {
        out.writeUTF("LISTDIR " + path);
        return parseListResult(in.readUTF());
    }

    public List<String> listBin() throws Exception {
        out.writeUTF("LISTBIN");
        String response = in.readUTF();

        List<String> files = new ArrayList<>();
        if (response.equals("EMPTY")) return files;

        String[] items = response.split(",");
        for (String item : items) {
            if (!item.trim().isEmpty()) files.add(item.trim());
        }
        return files;
    }

    public String restoreFile(String recycleName) throws Exception {
        out.writeUTF("RESTORE " + recycleName);
        return in.readUTF();
    }

    public String permanentDelete(String recycleName) throws Exception {
        out.writeUTF("PERMANENTDELETE " + recycleName);
        return in.readUTF();
    }

    public String makeDir(String path) throws Exception {
        out.writeUTF("MKDIR " + path);
        return in.readUTF();
    }

    public String deleteDir(String path) throws Exception {
        out.writeUTF("DELETEDIR " + path);
        return in.readUTF();
    }

    public String renameDir(String oldPath, String newPath) throws Exception {
        out.writeUTF("RENAMEDIR " + oldPath + "|" + newPath);
        return in.readUTF();
    }

    public String moveFile(String filePath, String destFolder) throws Exception {
        out.writeUTF("MOVEFILE " + filePath + "|" + destFolder);
        return in.readUTF();
    }

    public String uploadFile(File file, String currentPath,
                             Consumer<Double> progressCallback) throws Exception {
        String remotePath = currentPath.isEmpty()
                ? file.getName()
                : currentPath + "/" + file.getName();
        out.writeUTF("UPLOAD " + remotePath + " " + file.length());
        return sendFileBytes(file, progressCallback);
    }

    public String downloadFile(String relativePath, File saveTo,
                               Consumer<Double> progressCallback) throws Exception {
        out.writeUTF("DOWNLOAD " + relativePath);
        return receiveFileTo(saveTo, progressCallback);
    }

    public String downloadFile(String relativePath, File saveTo) throws Exception {
        return downloadFile(relativePath, saveTo, null);
    }

    public String deleteFile(String relativePath) throws Exception {
        out.writeUTF("DELETE " + relativePath);
        return in.readUTF();
    }

    public List<UserInfo> listUsers() throws Exception {
        out.writeUTF("LISTUSERS");
        String response = in.readUTF();

        List<UserInfo> users = new ArrayList<>();
        if (response.equals("EMPTY")) return users;

        String[] items = response.split(",");
        for (String item : items) {
            String[] parts = item.split(":", 2);
            if (parts.length >= 2) users.add(new UserInfo(parts[0].trim(), parts[1].trim()));
        }
        return users;
    }

    public String deleteUser(String username) throws Exception {
        out.writeUTF("DELETEUSER " + username);
        return in.readUTF();
    }

    public ListResult viewUserFiles(String username) throws Exception {
        out.writeUTF("VIEWUSERFILES " + username);
        return parseListResult(in.readUTF());
    }

    public String shareFile(String targetUser, String filePath) throws Exception {
        out.writeUTF("SHARE " + targetUser + "|" + filePath);
        return in.readUTF();
    }

    public String unshareFile(String targetUser, String filePath) throws Exception {
        out.writeUTF("UNSHARE " + targetUser + "|" + filePath);
        return in.readUTF();
    }

    public List<SharedFileInfo> listSharedWithMe() throws Exception {
        out.writeUTF("LISTSHARED");
        String response = in.readUTF();

        List<SharedFileInfo> files = new ArrayList<>();
        if (response.equals("EMPTY")) return files;

        String[] items = response.split(",");
        for (String item : items) {
            String[] parts = item.split("\\|", 3);
            if (parts.length >= 3) {
                files.add(new SharedFileInfo(parts[0].trim(), parts[1].trim(), parts[2].trim()));
            }
        }
        return files;
    }

    public String downloadSharedFile(String ownerUsername, String filePath, File saveTo) throws Exception {
        return downloadSharedFile(ownerUsername, filePath, saveTo, null);
    }

    public String downloadSharedFile(String ownerUsername, String filePath, File saveTo,
                                     Consumer<Double> progressCallback) throws Exception {
        out.writeUTF("DOWNLOADSHARED " + ownerUsername + "|" + filePath);
        return receiveFileTo(saveTo, progressCallback);
    }

    // ===== GROUPS =====

    public String createGroup(String groupName) throws Exception {
        out.writeUTF("CREATEGROUP " + groupName);
        return in.readUTF();
    }

    public String addGroupMember(String groupId, String username) throws Exception {
        out.writeUTF("ADDGROUPMEMBER " + groupId + "|" + username);
        return in.readUTF();
    }

    public List<GroupInfo> listGroups() throws Exception {
        out.writeUTF("LISTGROUPS");
        String response = in.readUTF();

        List<GroupInfo> groups = new ArrayList<>();
        if (response.equals("EMPTY")) return groups;

        String[] items = response.split(",");
        for (String item : items) {
            String[] parts = item.split("\\|", 3);
            if (parts.length >= 3) {
                groups.add(new GroupInfo(parts[0].trim(), parts[1].trim(), parts[2].trim()));
            }
        }
        return groups;
    }

    public ListResult listGroupFiles(String groupId, String relativePath) throws Exception {
        String payload = relativePath == null || relativePath.isBlank()
                ? groupId
                : groupId + "|" + relativePath;
        out.writeUTF("LISTGROUPFILES " + payload);
        return parseListResult(in.readUTF());
    }

    public String uploadGroupFile(File file, String groupId, String currentGroupPath,
                                  Consumer<Double> progressCallback) throws Exception {
        String remotePath = (currentGroupPath == null || currentGroupPath.isBlank())
                ? file.getName()
                : currentGroupPath + "/" + file.getName();
        out.writeUTF("UPLOADGROUP " + groupId + "|" + remotePath + " " + file.length());
        return sendFileBytes(file, progressCallback);
    }

    public String downloadGroupFile(String groupId, String relativePath, File saveTo) throws Exception {
        return downloadGroupFile(groupId, relativePath, saveTo, null);
    }

    public String downloadGroupFile(String groupId, String relativePath, File saveTo,
                                    Consumer<Double> progressCallback) throws Exception {
        out.writeUTF("DOWNLOADGROUP " + groupId + "|" + relativePath);
        return receiveFileTo(saveTo, progressCallback);
    }

    public String deleteGroupFile(String groupId, String relativePath) throws Exception {
        out.writeUTF("DELETEGROUPFILE " + groupId + "|" + relativePath);
        return in.readUTF();
    }

    // ===== HELPERS =====

    private String sendFileBytes(File file, Consumer<Double> progressCallback) throws Exception {
        byte[] buffer = new byte[4096];
        long totalBytes = file.length();
        long uploadedBytes = 0;
        int bytesRead;

        try (FileInputStream fis = new FileInputStream(file)) {
            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                uploadedBytes += bytesRead;
                if (progressCallback != null && totalBytes > 0) {
                    progressCallback.accept((double) uploadedBytes / totalBytes);
                }
            }
        }
        out.flush();
        return in.readUTF();
    }

    private String receiveFileTo(File saveTo, Consumer<Double> progressCallback) throws Exception {
        String response = in.readUTF();
        if (response.startsWith("ERROR")) return response;

        long fileSize = Long.parseLong(response.split(" ", 2)[1].trim());
        byte[] buffer = new byte[4096];
        long remaining = fileSize;
        long downloaded = 0;

        try (FileOutputStream fos = new FileOutputStream(saveTo)) {
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) break;
                fos.write(buffer, 0, read);
                remaining -= read;
                downloaded += read;
                if (progressCallback != null && fileSize > 0) {
                    progressCallback.accept((double) downloaded / fileSize);
                }
            }
        }
        return "DOWNLOAD_SUCCESS";
    }

    /**
     * FIX: sizes and dates lists now track a single global index across both
     * folders and files, so AdminController can correctly map them.
     * Folders are added first (as the server sends them first), then files.
     */
    private ListResult parseListResult(String response) {
        List<String> folders = new ArrayList<>();
        List<String> files = new ArrayList<>();
        List<String> sizes = new ArrayList<>();
        List<String> dates = new ArrayList<>();

        if (response.equals("EMPTY") || response.startsWith("ERROR")) {
            return new ListResult(folders, files, sizes, dates);
        }

        // Two-pass: collect folders first, then files, preserving order for size/date alignment
        List<String[]> folderEntries = new ArrayList<>();
        List<String[]> fileEntries = new ArrayList<>();

        String[] items = response.split(",");
        for (String item : items) {
            if (item.trim().isEmpty()) continue;
            String[] parts = item.split(":", 4);
            if (parts.length >= 4) {
                if (parts[0].equals("DIR")) folderEntries.add(parts);
                else fileEntries.add(parts);
            }
        }

        for (String[] parts : folderEntries) {
            folders.add(parts[1]);
            sizes.add(parts[2]);
            dates.add(parts[3]);
        }
        for (String[] parts : fileEntries) {
            files.add(parts[1]);
            sizes.add(parts[2]);
            dates.add(parts[3]);
        }

        return new ListResult(folders, files, sizes, dates);
    }

    // ===== DATA CLASSES =====

    public static class UserInfo {
        public String username;
        public String storageUsed;

        public UserInfo(String username, String storageUsed) {
            this.username = username;
            this.storageUsed = storageUsed;
        }
    }

    public static class ListResult {
        public List<String> folders;
        public List<String> files;
        public List<String> sizes;
        public List<String> dates;

        public ListResult(List<String> folders, List<String> files,
                          List<String> sizes, List<String> dates) {
            this.folders = folders;
            this.files = files;
            this.sizes = sizes;
            this.dates = dates;
        }
    }

    public static class SharedFileInfo {
        public String sharedBy;
        public String filePath;
        public String filename;

        public SharedFileInfo(String sharedBy, String filePath, String filename) {
            this.sharedBy = sharedBy;
            this.filePath = filePath;
            this.filename = filename;
        }
    }

    public static class GroupInfo {
        public String groupId;
        public String groupName;
        public String owner;

        public GroupInfo(String groupId, String groupName, String owner) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.owner = owner;
        }
    }
}