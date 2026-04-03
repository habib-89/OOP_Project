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
    private String serverHost = "localhost"; // default
    private String sessionUsername;
    private String sessionPassword;
    private Socket liveSocket;
    private DataOutputStream liveOut;
    private DataInputStream liveIn;
    private Thread liveListenerThread;

    public void setServerHost(String host) {
        this.serverHost = host.trim();
    }

    public String getServerHost() {
        return serverHost;
    }

    public void connect() throws Exception {
        if (socket != null && socket.isConnected() && !socket.isClosed()) return;
        socket = new Socket(serverHost, 5001);
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());
    }


    public String login(String username, String password) throws Exception {
        connect();
        out.writeUTF("LOGIN " + username + " " + password);
        String response = in.readUTF();
        if ("SUCCESS".equals(response)) {
            this.sessionUsername = username;
            this.sessionPassword = password;
        }
        return response;
    }

    public String sendMessage(String message) throws Exception {
        out.writeUTF(message);
        return in.readUTF();
    }

    public ListResult listDir(String path) throws Exception {
        out.writeUTF("LISTDIR " + path);
        String response = in.readUTF();

        List<String> folders = new ArrayList<>();
        List<String> files = new ArrayList<>();
        List<String> sizes = new ArrayList<>();
        List<String> dates = new ArrayList<>();

        if (response.equals("EMPTY"))
            return new ListResult(folders, files, sizes, dates);

        String[] items = response.split(",");
        for (String item : items) {
            String[] parts = item.split(":");
            if (parts.length >= 4) {
                boolean isDir = parts[0].equals("DIR");
                String name = parts[1];
                String size = parts[2];
                String date = parts[3];

                if (isDir) folders.add(name);
                else files.add(name);
                sizes.add(size);
                dates.add(date);
            } else if (parts.length == 1) {
                if (item.startsWith("DIR:")) folders.add(item.substring(4));
                else if (item.startsWith("FILE:")) files.add(item.substring(5));
                sizes.add("—");
                dates.add("—");
            }
        }

        return new ListResult(folders, files, sizes, dates);
    }

    public List<String> listBin() throws Exception {
        out.writeUTF("LISTBIN");
        String response = in.readUTF();

        List<String> files = new ArrayList<>();
        if (response.equals("EMPTY")) return files;

        String[] items = response.split(",");
        for (String item : items) {
            if (!item.trim().isEmpty()) {
                files.add(item.trim());
            }
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
        String remotePath = currentPath.isEmpty() ?
                file.getName() : currentPath + "/" + file.getName();
        out.writeUTF("UPLOAD " + remotePath + " " + file.length());

        byte[] buffer = new byte[4096];
        FileInputStream fis = new FileInputStream(file);
        long totalBytes = file.length();
        long uploadedBytes = 0;
        int bytesRead;

        while ((bytesRead = fis.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
            uploadedBytes += bytesRead;
            if (progressCallback != null) {
                double progress = (double) uploadedBytes / totalBytes;
                progressCallback.accept(progress);
            }
        }
        fis.close();
        out.flush();
        return in.readUTF();
    }

    public String downloadFile(String relativePath, File saveTo,
                               Consumer<Double> progressCallback) throws Exception {
        out.writeUTF("DOWNLOAD " + relativePath);
        String response = in.readUTF();

        if (response.startsWith("ERROR")) return response;

        long fileSize = Long.parseLong(response.split(" ")[1]);
        byte[] buffer = new byte[4096];
        long remaining = fileSize;
        long downloaded = 0;
        FileOutputStream fos = new FileOutputStream(saveTo);

        while (remaining > 0) {
            int read = in.read(buffer, 0,
                    (int) Math.min(buffer.length, remaining));
            fos.write(buffer, 0, read);
            remaining -= read;
            downloaded += read;
            if (progressCallback != null) {
                double progress = (double) downloaded / fileSize;
                progressCallback.accept(progress);
            }
        }
        fos.close();
        return "DOWNLOAD_SUCCESS";
    }

    // Overload for backward compatibility (no progress callback)
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
            String[] parts = item.split(":");
            if (parts.length >= 2) {
                users.add(new UserInfo(parts[0], parts[1]));
            }
        }
        return users;
    }

    public String deleteUser(String username) throws Exception {
        out.writeUTF("DELETEUSER " + username);
        return in.readUTF();
    }

    public ListResult viewUserFiles(String username) throws Exception {
        out.writeUTF("VIEWUSERFILES " + username);
        String response = in.readUTF();

        List<String> folders = new ArrayList<>();
        List<String> files = new ArrayList<>();
        List<String> sizes = new ArrayList<>();
        List<String> dates = new ArrayList<>();

        if (response.equals("EMPTY"))
            return new ListResult(folders, files, sizes, dates);

        String[] items = response.split(",");
        for (String item : items) {
            String[] parts = item.split(":");
            if (parts.length >= 4) {
                boolean isDir = parts[0].equals("DIR");
                if (isDir) folders.add(parts[1]);
                else files.add(parts[1]);
                sizes.add(parts[2]);
                dates.add(parts[3]);
            }
        }
        return new ListResult(folders, files, sizes, dates);
    }

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
            String[] parts = item.split("\\|");
            if (parts.length >= 3) {
                files.add(new SharedFileInfo(parts[0], parts[1], parts[2]));
            }
        }
        return files;
    }

    public String downloadSharedFile(String ownerUsername,
                                     String filePath, File saveTo) throws Exception {
        return downloadSharedFile(ownerUsername, filePath, saveTo, null);
    }

    public String downloadSharedFile(String ownerUsername,
                                     String filePath, File saveTo,
                                     Consumer<Double> progressCallback) throws Exception {
        out.writeUTF("DOWNLOADSHARED " + ownerUsername + "|" + filePath);
        String response = in.readUTF();

        if (response.startsWith("ERROR")) return response;

        long fileSize = Long.parseLong(response.split(" ")[1]);
        byte[] buffer = new byte[4096];
        long remaining = fileSize;
        long downloaded = 0;
        FileOutputStream fos = new FileOutputStream(saveTo);

        while (remaining > 0) {
            int read = in.read(buffer, 0,
                    (int) Math.min(buffer.length, remaining));
            fos.write(buffer, 0, read);
            remaining -= read;
            downloaded += read;
            if (progressCallback != null) {
                double progress = (double) downloaded / fileSize;
                progressCallback.accept(progress);
            }
        }
        fos.close();
        return "DOWNLOAD_SUCCESS";
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



    public String uploadProfilePicture(File file, Consumer<Double> progressCallback) throws Exception {
        out.writeUTF("UPLOAD_PROFILE " + file.length());

        byte[] buffer = new byte[4096];
        FileInputStream fis = new FileInputStream(file);
        long totalBytes = file.length();
        long uploadedBytes = 0;
        int bytesRead;

        while ((bytesRead = fis.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
            uploadedBytes += bytesRead;
            if (progressCallback != null) {
                double progress = (double) uploadedBytes / totalBytes;
                progressCallback.accept(progress);
            }
        }

        fis.close();
        out.flush();
        return in.readUTF();
    }

    public String downloadProfilePicture(File saveTo) throws Exception {
        out.writeUTF("DOWNLOAD_PROFILE");
        String response = in.readUTF();

        if (response.startsWith("ERROR")) return response;

        long fileSize = Long.parseLong(response.split(" ")[1]);
        byte[] buffer = new byte[4096];
        long remaining = fileSize;

        FileOutputStream fos = new FileOutputStream(saveTo);
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read == -1) break;
            fos.write(buffer, 0, read);
            remaining -= read;
        }
        fos.close();

        return "DOWNLOAD_SUCCESS";
    }


    public void startDiscussionListener(String groupId, String fileName, Consumer<String> onUpdate) throws Exception {
        stopDiscussionListener();

        if (sessionUsername == null || sessionPassword == null) {
            throw new IllegalStateException("Live discussion requires a logged-in session.");
        }

        liveSocket = new Socket(serverHost, 5001);
        liveOut = new DataOutputStream(liveSocket.getOutputStream());
        liveIn = new DataInputStream(liveSocket.getInputStream());

        liveOut.writeUTF("LOGIN " + sessionUsername + " " + sessionPassword);
        String loginResponse = liveIn.readUTF();
        if (!"SUCCESS".equals(loginResponse)) {
            stopDiscussionListener();
            throw new IOException("Live discussion login failed.");
        }

        liveOut.writeUTF("SUBSCRIBE_DISCUSSION " + groupId + "|" + fileName);
        String subscribeResponse = liveIn.readUTF();
        if (!"SUBSCRIBE_SUCCESS".equals(subscribeResponse)) {
            stopDiscussionListener();
            throw new IOException(subscribeResponse);
        }

        liveListenerThread = new Thread(() -> {
            try {
                while (liveSocket != null && !liveSocket.isClosed()) {
                    String message = liveIn.readUTF();
                    if (message != null && message.startsWith("DISCUSSION_UPDATE ")) {
                        if (onUpdate != null) {
                            onUpdate.accept(message);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        });
        liveListenerThread.setDaemon(true);
        liveListenerThread.start();
    }

    public void stopDiscussionListener() {
        try {
            if (liveOut != null) {
                liveOut.writeUTF("UNSUBSCRIBE_DISCUSSION");
                liveOut.flush();
            }
        } catch (Exception ignored) {
        }

        try { if (liveIn != null) liveIn.close(); } catch (Exception ignored) {}
        try { if (liveOut != null) liveOut.close(); } catch (Exception ignored) {}
        try { if (liveSocket != null) liveSocket.close(); } catch (Exception ignored) {}

        liveIn = null;
        liveOut = null;
        liveSocket = null;
        liveListenerThread = null;
    }

    // ===================== GROUP FEATURES =====================

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
            String[] parts = item.split("\\|");
            if (parts.length >= 3) {
                groups.add(new GroupInfo(parts[0], parts[1], parts[2]));
            }
        }
        return groups;
    }

    public ListResult listGroupFiles(String groupId, String relativePath) throws Exception {
        String command = "LISTGROUPFILES " + groupId;
        if (relativePath != null && !relativePath.trim().isEmpty()) {
            command += "|" + relativePath.trim();
        }

        out.writeUTF(command);
        String response = in.readUTF();

        List<String> folders = new ArrayList<>();
        List<String> files = new ArrayList<>();
        List<String> sizes = new ArrayList<>();
        List<String> dates = new ArrayList<>();

        if (response.equals("EMPTY"))
            return new ListResult(folders, files, sizes, dates);

        String[] items = response.split(",");
        for (String item : items) {
            String[] parts = item.split(":");
            if (parts.length >= 4) {
                boolean isDir = parts[0].equals("DIR");
                String name = parts[1];
                String size = parts[2];
                String date = parts[3];

                if (isDir) folders.add(name);
                else files.add(name);
                sizes.add(size);
                dates.add(date);
            }
        }

        return new ListResult(folders, files, sizes, dates);
    }

    public String uploadGroupFile(File file, String groupId, String currentPath,
                                  Consumer<Double> progressCallback) throws Exception {
        String remotePath = (currentPath == null || currentPath.isEmpty())
                ? file.getName()
                : currentPath + "/" + file.getName();

        out.writeUTF("UPLOADGROUP " + groupId + "|" + remotePath + " " + file.length());

        byte[] buffer = new byte[4096];
        FileInputStream fis = new FileInputStream(file);
        long totalBytes = file.length();
        long uploadedBytes = 0;
        int bytesRead;

        while ((bytesRead = fis.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
            uploadedBytes += bytesRead;
            if (progressCallback != null) {
                double progress = (double) uploadedBytes / totalBytes;
                progressCallback.accept(progress);
            }
        }
        fis.close();
        out.flush();
        return in.readUTF();
    }

    public String downloadGroupFile(String groupId, String filePath, File saveTo,
                                    Consumer<Double> progressCallback) throws Exception {
        out.writeUTF("DOWNLOADGROUP " + groupId + "|" + filePath);
        String response = in.readUTF();

        if (response.startsWith("ERROR")) return response;

        long fileSize = Long.parseLong(response.split(" ")[1]);
        byte[] buffer = new byte[4096];
        long remaining = fileSize;
        long downloaded = 0;
        FileOutputStream fos = new FileOutputStream(saveTo);

        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            fos.write(buffer, 0, read);
            remaining -= read;
            downloaded += read;
            if (progressCallback != null) {
                double progress = (double) downloaded / fileSize;
                progressCallback.accept(progress);
            }
        }
        fos.close();
        return "DOWNLOAD_SUCCESS";
    }

    public String downloadGroupFile(String groupId, String filePath, File saveTo) throws Exception {
        return downloadGroupFile(groupId, filePath, saveTo, null);
    }

    public String deleteGroupFile(String groupId, String filePath) throws Exception {
        out.writeUTF("DELETEGROUPFILE " + groupId + "|" + filePath);
        return in.readUTF();
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
