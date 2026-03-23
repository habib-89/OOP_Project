package com.example.filesystemclientfx;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.ButtonBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DashboardController {

    @FXML private Label welcomeLabel;
    @FXML private TableView<FileItem> fileTable;
    @FXML private TableColumn<FileItem, String> fileNameColumn;
    @FXML private TableColumn<FileItem, String> fileSizeColumn;
    @FXML private TableColumn<FileItem, String> fileDateColumn;
    @FXML private Label statusLabel;
    @FXML private ImageView imagePreview;
    @FXML private Label previewLabel;
    @FXML private Label pathLabel;
    @FXML private ProgressBar uploadProgressBar;
    @FXML private Label progressLabel;
    @FXML private Button themeToggleBtn;
    @FXML private Button navFilesBtn;
    @FXML private Button navRecycleBtn;
    @FXML private Button navAdminBtn;
    @FXML private Button navSharedBtn;
    @FXML private TextField searchField;
    @FXML private javafx.scene.layout.HBox fileView;
    @FXML private javafx.scene.layout.HBox searchBar;
    @FXML private javafx.scene.layout.HBox actionBar;

    // Video player controls (embedded in Dashboard.fxml)
    @FXML private javafx.scene.layout.VBox videoView;
    @FXML private MediaView mediaView;
    @FXML private Button playPauseBtn;
    @FXML private Slider seekSlider;
    @FXML private Slider volumeSlider;
    @FXML private Label currentTimeLabel;
    @FXML private Label totalTimeLabel;
    @FXML private Label videoTitleLabel;
    @FXML private Button muteBtn;

    private MediaPlayer mediaPlayer;
    private boolean videoPlaying = false;
    private boolean isMuted = false;
    private boolean seekDragging = false;

    private NetworkManager network;
    private String username;
    private String currentPath = "";
    private boolean isDarkTheme = true;
    private boolean inRecycleBin = false;
    private boolean inSharedView = false;
    private List<FileItem> allItems = new ArrayList<>();

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg","jpeg","png","gif","bmp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4","avi","mkv","mov");
    private long lastClickTime = 0;

    @FXML
    public void initialize() {
        fileNameColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDisplayName()));
        fileSizeColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSize()));
        fileDateColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDate()));
        fileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        seekSlider.setOnMousePressed(e -> seekDragging = true);
        seekSlider.setOnMouseReleased(e -> {
            seekDragging = false;
            if (mediaPlayer != null) mediaPlayer.seek(Duration.seconds(seekSlider.getValue()));
        });
        volumeSlider.valueProperty().addListener((obs, o, n) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(n.doubleValue() / 100.0);
                muteBtn.setText(n.doubleValue() == 0 ? "🔇" : "🔊");
            }
        });
    }

    public void setUsername(String username) {
        this.username = username;
        welcomeLabel.setText(username);
        if (username.equals("admin")) { navAdminBtn.setVisible(true); navAdminBtn.setManaged(true); }
    }

    public void setNetwork(NetworkManager network) {
        this.network = network;
        refreshFileList();
    }

    // ===================== VIDEO PLAYER =====================

    private void showVideoPlayer(File videoFile, String filename) {
        fileView.setVisible(false);   fileView.setManaged(false);
        searchBar.setVisible(false);  searchBar.setManaged(false);
        actionBar.setVisible(false);  actionBar.setManaged(false);
        videoView.setVisible(true);   videoView.setManaged(true);

        videoTitleLabel.setText(filename);
        statusLabel.setText("Loading: " + filename);

        // FIX: unbind before rebinding to avoid crash on second video
        mediaView.fitWidthProperty().unbind();
        mediaView.fitHeightProperty().unbind();
        mediaView.fitWidthProperty().bind(videoView.widthProperty().subtract(20));
        mediaView.setFitHeight(380);

        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null;
            }

            // FIX: reset controls
            playPauseBtn.setText("▶");
            seekSlider.setValue(0);
            currentTimeLabel.setText("00:00");
            totalTimeLabel.setText("00:00");
            videoPlaying = false;

            Media media = new Media(videoFile.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);
            mediaView.setPreserveRatio(true);

            // FIX: handle media errors before player is ready
            media.setOnError(() -> {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("❌ Unsupported format or corrupted file: " + filename);
                    handleBackFromVideo();
                });
            });

            mediaPlayer.setOnReady(() -> {
                Duration total = mediaPlayer.getTotalDuration();
                totalTimeLabel.setText(formatDuration(total));
                seekSlider.setMax(total.toSeconds());
                mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
                mediaPlayer.play();
                videoPlaying = true;
                playPauseBtn.setText("⏸");
                statusLabel.setText("▶ Playing: " + filename);
            });

            mediaPlayer.currentTimeProperty().addListener((obs, o, n) -> {
                if (!seekDragging) seekSlider.setValue(n.toSeconds());
                currentTimeLabel.setText(formatDuration(n));
            });

            mediaPlayer.setOnEndOfMedia(() -> {
                videoPlaying = false;
                playPauseBtn.setText("▶");
                statusLabel.setText("Finished: " + filename);
                mediaPlayer.stop();
            });

            // FIX: detailed error handling
            mediaPlayer.setOnError(() -> {
                javafx.application.Platform.runLater(() -> {
                    String err = mediaPlayer.getError() != null ?
                            mediaPlayer.getError().getMessage() : "Unknown error";
                    statusLabel.setText("❌ Cannot play video: " + err);

                    // Offer to open with system app instead
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Video Error");
                    alert.setHeaderText("Cannot play this video inside FileVault");
                    alert.setContentText(
                            "Format may not be supported by JavaFX.\n\n" +
                                    "Error: " + err + "\n\n" +
                                    "Try opening it with your system video player instead.");

                    ButtonType openExternal = new ButtonType("Open with System Player");
                    ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                    alert.getButtonTypes().setAll(openExternal, cancel);

                    alert.showAndWait().ifPresent(response -> {
                        if (response == openExternal) {
                            try {
                                java.awt.Desktop.getDesktop().open(videoFile);
                            } catch (Exception ex) {
                                statusLabel.setText("Could not open: " + ex.getMessage());
                            }
                        }
                    });
                    handleBackFromVideo();
                });
            });

        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
            handleBackFromVideo();
        }
    }

    @FXML
    private void handleBackFromVideo() {
        if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.dispose(); mediaPlayer = null; }
        videoPlaying = false; playPauseBtn.setText("▶");
        seekSlider.setValue(0); currentTimeLabel.setText("00:00"); totalTimeLabel.setText("00:00");
        videoView.setVisible(false);  videoView.setManaged(false);
        fileView.setVisible(true);    fileView.setManaged(true);
        searchBar.setVisible(true);   searchBar.setManaged(true);
        actionBar.setVisible(true);   actionBar.setManaged(true);
        statusLabel.setText("Back to files.");
    }

    @FXML private void handleVideoPlayPause() {
        if (mediaPlayer == null) return;
        if (videoPlaying) { mediaPlayer.pause(); playPauseBtn.setText("▶"); }
        else { mediaPlayer.play(); playPauseBtn.setText("⏸"); }
        videoPlaying = !videoPlaying;
    }
    @FXML private void handleVideoRewind() {
        if (mediaPlayer != null) mediaPlayer.seek(mediaPlayer.getCurrentTime().subtract(Duration.seconds(10)));
    }
    @FXML private void handleVideoForward() {
        if (mediaPlayer != null) mediaPlayer.seek(mediaPlayer.getCurrentTime().add(Duration.seconds(10)));
    }
    @FXML private void handleVideoMute() {
        if (mediaPlayer == null) return;
        isMuted = !isMuted; mediaPlayer.setMute(isMuted); muteBtn.setText(isMuted ? "🔇" : "🔊");
    }

    private String formatDuration(Duration d) {
        int s = (int) d.toSeconds(), h = s/3600, m = (s%3600)/60, sec = s%60;
        return h > 0 ? String.format("%d:%02d:%02d",h,m,sec) : String.format("%02d:%02d",m,sec);
    }

    // ===================== FILE LIST =====================

    private void refreshFileList() {
        try {
            NetworkManager.ListResult result = network.listDir(currentPath);
            allItems = new ArrayList<>();
            int si = 0, di = 0;
            for (String f : result.folders) {
                String sz = si < result.sizes.size() ? result.sizes.get(si++) : "—";
                String dt = di < result.dates.size() ? result.dates.get(di++) : "—";
                allItems.add(new FileItem("📁  "+f, f, true, sz, dt));
            }
            for (String f : result.files) {
                String sz = si < result.sizes.size() ? result.sizes.get(si++) : "—";
                String dt = di < result.dates.size() ? result.dates.get(di++) : "—";
                allItems.add(new FileItem("📄  "+f, f, false, sz, dt));
            }
            fileTable.setItems(FXCollections.observableArrayList(allItems));
            pathLabel.setText("/ " + (currentPath.isEmpty() ? "Home" : currentPath.replace("/", " / ")));
            statusLabel.setText(allItems.isEmpty() ? "This folder is empty." :
                    result.folders.size()+" folder(s)  •  "+result.files.size()+" file(s)");
        } catch (Exception e) { statusLabel.setText("Error: "+e.getMessage()); }
    }

    private void refreshBin() {
        try {
            List<String> binFiles = network.listBin();
            List<FileItem> items = new ArrayList<>();
            for (String r : binFiles) {
                String dn = r.contains("##") ? r.substring(r.lastIndexOf("##")+2) : r;
                items.add(new FileItem("🗑  "+dn, r, false, "—", "—"));
            }
            allItems = items;
            fileTable.setItems(FXCollections.observableArrayList(items));
            statusLabel.setText(items.isEmpty() ? "Recycle Bin is empty." : items.size()+" item(s) in Recycle Bin");
        } catch (Exception e) { statusLabel.setText("Error: "+e.getMessage()); }
    }

    private void refreshShared() {
        try {
            List<NetworkManager.SharedFileInfo> sf = network.listSharedWithMe();
            List<FileItem> items = new ArrayList<>();
            for (NetworkManager.SharedFileInfo s : sf)
                items.add(new FileItem("📤  "+s.filename+"  (from: "+s.sharedBy+")", s.sharedBy+"|"+s.filePath, false, "—", "—"));
            allItems = items;
            fileTable.setItems(FXCollections.observableArrayList(items));
            statusLabel.setText(items.isEmpty() ? "No files shared with you yet." : items.size()+" file(s) shared with you.");
        } catch (Exception e) { statusLabel.setText("Error: "+e.getMessage()); }
    }

    @FXML private void handleSearch() {
        String q = searchField.getText().toLowerCase().trim();
        if (q.isEmpty()) { fileTable.setItems(FXCollections.observableArrayList(allItems)); return; }
        List<FileItem> f = new ArrayList<>();
        for (FileItem i : allItems) if (i.getRawName().toLowerCase().contains(q)) f.add(i);
        fileTable.setItems(FXCollections.observableArrayList(f));
        statusLabel.setText("Found "+f.size()+" result(s) for \""+q+"\"");
    }

    // ===================== NAV =====================

    @FXML private void handleNavFiles() {
        inRecycleBin=false; inSharedView=false; currentPath="";
        navFilesBtn.getStyleClass().add("nav-btn-active");
        navRecycleBtn.getStyleClass().remove("nav-btn-active");
        navSharedBtn.getStyleClass().remove("nav-btn-active");
        showNoPreview("Select a file to preview"); refreshFileList();
    }
    @FXML private void handleNavRecycleBin() {
        inRecycleBin=true; inSharedView=false;
        navRecycleBtn.getStyleClass().add("nav-btn-active");
        navFilesBtn.getStyleClass().remove("nav-btn-active");
        navSharedBtn.getStyleClass().remove("nav-btn-active");
        pathLabel.setText("/ Recycle Bin"); showNoPreview("Select a file to preview"); refreshBin();
    }
    @FXML private void handleNavShared() {
        inSharedView=true; inRecycleBin=false;
        navSharedBtn.getStyleClass().add("nav-btn-active");
        navFilesBtn.getStyleClass().remove("nav-btn-active");
        navRecycleBtn.getStyleClass().remove("nav-btn-active");
        pathLabel.setText("/ Shared with Me"); showNoPreview("Select a file to preview"); refreshShared();
    }
    @FXML private void handleNavAdmin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Admin.fxml"));
            Scene scene = new Scene(loader.load(), 800, 550);
            var css = getClass().getResource("dashboard.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            AdminController c = loader.getController(); c.setNetwork(network);
            Stage s = new Stage(); s.setTitle("FileVault — Admin Panel"); s.setScene(scene); s.show();
        } catch (Exception e) { statusLabel.setText("Error: "+e.getMessage()); }
    }
    @FXML private void handleThemeToggle() {
        Scene scene = welcomeLabel.getScene();
        if (isDarkTheme) {
            scene.getRoot().getStyleClass().remove("root-dark");
            scene.getRoot().getStyleClass().add("root-light");
            themeToggleBtn.setText("🌙 Dark Mode"); isDarkTheme=false;
        } else {
            scene.getRoot().getStyleClass().remove("root-light");
            scene.getRoot().getStyleClass().add("root-dark");
            themeToggleBtn.setText("☀ Light Mode"); isDarkTheme=true;
        }
    }

    // ===================== FILE CLICK =====================

    @FXML private void handleFileClick() {
        FileItem selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        boolean dc = isDoubleClick();

        if (selected.isFolder()) {
            if (dc) { currentPath = currentPath.isEmpty() ? selected.getRawName() : currentPath+"/"+selected.getRawName(); showNoPreview("Select a file to preview"); refreshFileList(); }
        } else {
            if (inSharedView) {
                String[] p = selected.getRawName().split("\\|");
                String fp = p[1], fn = fp.contains("/") ? fp.substring(fp.lastIndexOf("/")+1) : fp;
                String ext = getExtension(fn);
                if (dc) { if (VIDEO_EXTENSIONS.contains(ext)) downloadAndPlaySharedVideo(selected); else openSharedWithSystemApp(selected); }
                else { if (IMAGE_EXTENSIONS.contains(ext)) previewSharedImage(selected); else if (VIDEO_EXTENSIONS.contains(ext)) showNoPreview("🎬 Double click to play video."); else showNoPreview("Double click to open this file."); }
            } else {
                String fn = selected.getRawName(), ext = getExtension(fn);
                if (dc) { if (VIDEO_EXTENSIONS.contains(ext)) downloadAndPlayVideo(fn); else openWithSystemApp(fn, ext); }
                else { if (IMAGE_EXTENSIONS.contains(ext)) previewImage(fn); else if (VIDEO_EXTENSIONS.contains(ext)) showNoPreview("🎬 Double click to play video."); else showNoPreview("Double click to open this file."); }
            }
        }
    }

    private boolean isDoubleClick() {
        long now = System.currentTimeMillis();
        boolean d = (now - lastClickTime) < 400;
        lastClickTime = now; return d;
    }

    // ===================== VIDEO HELPERS =====================

    private void downloadAndPlayVideo(String filename) {
        String rel = currentPath.isEmpty() ? filename : currentPath+"/"+filename;
        File tmp = new File(new File(System.getProperty("java.io.tmpdir"),"filevault"), filename);
        tmp.getParentFile().mkdirs();
        statusLabel.setText("Loading video...");
        uploadProgressBar.setProgress(0); uploadProgressBar.setVisible(true);
        progressLabel.setVisible(true); progressLabel.setText("Loading video... 0%");
        Thread t = new Thread(() -> {
            try {
                String r = network.downloadFile(rel, tmp, p -> javafx.application.Platform.runLater(() -> { uploadProgressBar.setProgress(p); progressLabel.setText("Loading video... "+(int)(p*100)+"%"); }));
                javafx.application.Platform.runLater(() -> { uploadProgressBar.setVisible(false); progressLabel.setVisible(false); if (r.equals("DOWNLOAD_SUCCESS")) showVideoPlayer(tmp, filename); else statusLabel.setText("Failed: "+r); });
            } catch (Exception e) { javafx.application.Platform.runLater(() -> { uploadProgressBar.setVisible(false); progressLabel.setVisible(false); statusLabel.setText("Error: "+e.getMessage()); }); }
        }); t.setDaemon(true); t.start();
    }

    private void downloadAndPlaySharedVideo(FileItem selected) {
        String[] p = selected.getRawName().split("\\|");
        String owner=p[0], fp=p[1], fn=fp.contains("/") ? fp.substring(fp.lastIndexOf("/")+1) : fp;
        File tmp = new File(new File(System.getProperty("java.io.tmpdir"),"filevault"), fn);
        tmp.getParentFile().mkdirs();
        statusLabel.setText("Loading video...");
        uploadProgressBar.setProgress(0); uploadProgressBar.setVisible(true);
        progressLabel.setVisible(true); progressLabel.setText("Loading video... 0%");
        Thread t = new Thread(() -> {
            try {
                String r = network.downloadSharedFile(owner, fp, tmp, prog -> javafx.application.Platform.runLater(() -> { uploadProgressBar.setProgress(prog); progressLabel.setText("Loading video... "+(int)(prog*100)+"%"); }));
                javafx.application.Platform.runLater(() -> { uploadProgressBar.setVisible(false); progressLabel.setVisible(false); if (r.equals("DOWNLOAD_SUCCESS")) showVideoPlayer(tmp, fn); else statusLabel.setText("Failed: "+r); });
            } catch (Exception e) { javafx.application.Platform.runLater(() -> { uploadProgressBar.setVisible(false); progressLabel.setVisible(false); statusLabel.setText("Error: "+e.getMessage()); }); }
        }); t.setDaemon(true); t.start();
    }

    // ===================== IMAGE PREVIEW =====================

    private void previewImage(String filename) {
        try {
            String rel = currentPath.isEmpty() ? filename : currentPath+"/"+filename;
            File tmp = File.createTempFile("preview_", "."+getExtension(filename)); tmp.deleteOnExit();
            if (network.downloadFile(rel, tmp).equals("DOWNLOAD_SUCCESS")) { imagePreview.setImage(new Image(new FileInputStream(tmp))); imagePreview.setVisible(true); previewLabel.setText(filename); }
            else showNoPreview("Could not load preview.");
        } catch (Exception e) { showNoPreview("Preview error."); }
    }

    private void previewSharedImage(FileItem selected) {
        try {
            String[] p = selected.getRawName().split("\\|");
            String owner=p[0], fp=p[1], fn=fp.contains("/") ? fp.substring(fp.lastIndexOf("/")+1) : fp;
            File tmp = File.createTempFile("preview_", "."+getExtension(fn)); tmp.deleteOnExit();
            if (network.downloadSharedFile(owner, fp, tmp).equals("DOWNLOAD_SUCCESS")) { imagePreview.setImage(new Image(new FileInputStream(tmp))); imagePreview.setVisible(true); previewLabel.setText(fn); }
            else showNoPreview("Could not load preview.");
        } catch (Exception e) { showNoPreview("Preview error."); }
    }

    // ===================== OPEN WITH SYSTEM APP =====================

    private void openWithSystemApp(String filename, String ext) {
        String rel = currentPath.isEmpty() ? filename : currentPath+"/"+filename;
        File tmp = new File(new File(System.getProperty("java.io.tmpdir"),"filevault"), filename);
        tmp.getParentFile().mkdirs();
        statusLabel.setText("Opening "+filename+"...");
        Thread t = new Thread(() -> {
            try {
                String r = network.downloadFile(rel, tmp);
                javafx.application.Platform.runLater(() -> { if (r.equals("DOWNLOAD_SUCCESS")) { try { java.awt.Desktop.getDesktop().open(tmp); statusLabel.setText("Opened: "+filename); } catch (Exception e) { statusLabel.setText("Could not open: "+e.getMessage()); } } else statusLabel.setText("Failed: "+r); });
            } catch (Exception e) { javafx.application.Platform.runLater(() -> statusLabel.setText("Error: "+e.getMessage())); }
        }); t.setDaemon(true); t.start();
    }

    private void openSharedWithSystemApp(FileItem selected) {
        String[] p = selected.getRawName().split("\\|");
        String owner=p[0], fp=p[1], fn=fp.contains("/") ? fp.substring(fp.lastIndexOf("/")+1) : fp;
        File tmp = new File(new File(System.getProperty("java.io.tmpdir"),"filevault"), fn);
        tmp.getParentFile().mkdirs();
        statusLabel.setText("Opening "+fn+"...");
        Thread t = new Thread(() -> {
            try {
                String r = network.downloadSharedFile(owner, fp, tmp);
                javafx.application.Platform.runLater(() -> { if (r.equals("DOWNLOAD_SUCCESS")) { try { java.awt.Desktop.getDesktop().open(tmp); statusLabel.setText("Opened: "+fn); } catch (Exception e) { statusLabel.setText("Could not open: "+e.getMessage()); } } else statusLabel.setText("Failed: "+r); });
            } catch (Exception e) { javafx.application.Platform.runLater(() -> statusLabel.setText("Error: "+e.getMessage())); }
        }); t.setDaemon(true); t.start();
    }

    // ===================== FILE OPERATIONS =====================

    @FXML private void handleGoUp() {
        if (currentPath.isEmpty()) { statusLabel.setText("Already at root."); return; }
        int i = currentPath.lastIndexOf("/");
        currentPath = i==-1 ? "" : currentPath.substring(0,i);
        showNoPreview("Select a file to preview"); refreshFileList();
    }

    @FXML private void handleCreateFolder() {
        TextInputDialog d = new TextInputDialog(); d.setTitle("New Folder"); d.setHeaderText("Create a new folder"); d.setContentText("Folder name:");
        d.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) { statusLabel.setText("Folder name cannot be empty."); return; }
            try { String r = network.makeDir(currentPath.isEmpty() ? name : currentPath+"/"+name); if (r.equals("MKDIR_SUCCESS")) { statusLabel.setText("✅ Folder created: "+name); refreshFileList(); } else statusLabel.setText("Failed: "+r); }
            catch (Exception e) { statusLabel.setText("Error: "+e.getMessage()); }
        });
    }

    @FXML private void handleRename() {
        FileItem sel = fileTable.getSelectionModel().getSelectedItem();
        if (sel == null) { statusLabel.setText("Please select a file or folder to rename."); return; }
        String old = sel.getRawName(), oldP = currentPath.isEmpty() ? old : currentPath+"/"+old;
        TextInputDialog d = new TextInputDialog(old); d.setTitle("Rename"); d.setHeaderText("Rename "+(sel.isFolder()?"folder":"file")); d.setContentText("New name:");
        d.showAndWait().ifPresent(newName -> {
            if (newName.trim().isEmpty()) { statusLabel.setText("Name cannot be empty."); return; }
            try { String r = network.renameDir(oldP, currentPath.isEmpty() ? newName : currentPath+"/"+newName); if (r.equals("RENAME_SUCCESS")) { statusLabel.setText("✅ Renamed to: "+newName); refreshFileList(); } else statusLabel.setText("Failed: "+r); }
            catch (Exception e) { statusLabel.setText("Error: "+e.getMessage()); }
        });
    }

    @FXML private void handleMoveFile() {
        List<FileItem> sel = new ArrayList<>(fileTable.getSelectionModel().getSelectedItems());
        List<String> files = new ArrayList<>();
        for (FileItem i : sel) if (!i.isFolder()) files.add(i.getRawName());
        if (files.isEmpty()) { statusLabel.setText("Please select one or more files to move."); return; }
        TextInputDialog d = new TextInputDialog(); d.setTitle("Move Files"); d.setHeaderText("Move "+files.size()+" file(s)"); d.setContentText("Destination folder:");
        d.showAndWait().ifPresent(dest -> {
            int ok=0, fail=0;
            for (String f : files) { try { if (network.moveFile(currentPath.isEmpty()?f:currentPath+"/"+f, dest).equals("MOVEFILE_SUCCESS")) ok++; else fail++; } catch (Exception e) { fail++; } }
            statusLabel.setText(fail==0 ? "✅ Moved "+ok+" file(s) to: "+dest : "Moved "+ok+", failed "+fail);
            refreshFileList();
        });
    }

    @FXML private void handleShare() {
        if (inRecycleBin||inSharedView) { statusLabel.setText("Can only share files from My Files."); return; }
        FileItem sel = fileTable.getSelectionModel().getSelectedItem();
        if (sel==null||sel.isFolder()) { statusLabel.setText("Please select a file to share."); return; }
        String fn=sel.getRawName(), fp=currentPath.isEmpty()?fn:currentPath+"/"+fn;
        TextInputDialog d = new TextInputDialog(); d.setTitle("Share File"); d.setHeaderText("Share \""+fn+"\""); d.setContentText("Enter username:");
        d.showAndWait().ifPresent(u -> {
            if (u.trim().isEmpty()) { statusLabel.setText("Username cannot be empty."); return; }
            try { String r=network.shareFile(u.trim(),fp); statusLabel.setText(r.equals("SHARE_SUCCESS") ? "✅ Shared \""+fn+"\" with "+u : "Failed: "+r.replace("ERROR ","")); }
            catch (Exception e) { statusLabel.setText("Error: "+e.getMessage()); }
        });
    }

    @FXML private void handleRefresh() {
        if (inRecycleBin) refreshBin(); else if (inSharedView) refreshShared(); else refreshFileList();
        statusLabel.setText("✅ Refreshed.");
    }

    @FXML private void handleUpload() {
        FileChooser fc = new FileChooser(); fc.setTitle("Select File to Upload");
        File f = fc.showOpenDialog((Stage)welcomeLabel.getScene().getWindow());
        if (f != null) {
            uploadProgressBar.setProgress(0); uploadProgressBar.setVisible(true); progressLabel.setVisible(true); progressLabel.setText("Uploading... 0%");
            Thread t = new Thread(() -> {
                try {
                    String r = network.uploadFile(f, currentPath, p -> javafx.application.Platform.runLater(() -> { uploadProgressBar.setProgress(p); progressLabel.setText("Uploading... "+(int)(p*100)+"%"); }));
                    javafx.application.Platform.runLater(() -> { uploadProgressBar.setVisible(false); progressLabel.setVisible(false); if (r.equals("UPLOAD_SUCCESS")) { statusLabel.setText("✅ Uploaded: "+f.getName()); refreshFileList(); } else statusLabel.setText("Upload failed: "+r); });
                } catch (Exception e) { javafx.application.Platform.runLater(() -> { uploadProgressBar.setVisible(false); progressLabel.setVisible(false); statusLabel.setText("Error: "+e.getMessage()); }); }
            }); t.setDaemon(true); t.start();
        }
    }

    @FXML private void handleDownload() {
        FileItem sel = fileTable.getSelectionModel().getSelectedItem();
        if (sel==null||sel.isFolder()) { statusLabel.setText("Please select a file to download."); return; }
        String fn, ext, owner=null, fp=null, rel=null;
        if (inSharedView) { String[] p=sel.getRawName().split("\\|"); owner=p[0]; fp=p[1]; fn=fp.contains("/")?fp.substring(fp.lastIndexOf("/")+1):fp; ext=getExtension(fn); }
        else { fn=sel.getRawName(); rel=currentPath.isEmpty()?fn:currentPath+"/"+fn; ext=getExtension(fn); }
        FileChooser fc = new FileChooser(); fc.setTitle("Save File As"); fc.setInitialFileName(fn);
        if (!ext.isEmpty()) fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(ext.toUpperCase()+" files","*."+ext));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files","*.*"));
        File saveTo = fc.showSaveDialog((Stage)welcomeLabel.getScene().getWindow());
        if (saveTo==null) return;
        if (!ext.isEmpty()&&!saveTo.getAbsolutePath().endsWith("."+ext)) saveTo=new File(saveTo.getAbsolutePath()+"."+ext);
        final File fSave=saveTo; final String fFn=fn, fOwner=owner, fFp=fp, fRel=rel;
        uploadProgressBar.setProgress(0); uploadProgressBar.setVisible(true); progressLabel.setVisible(true); progressLabel.setText("Downloading... 0%");
        Thread t = new Thread(() -> {
            try {
                String r = inSharedView ? network.downloadSharedFile(fOwner,fFp,fSave,p->javafx.application.Platform.runLater(()->{uploadProgressBar.setProgress(p);progressLabel.setText("Downloading... "+(int)(p*100)+"%");}))
                        : network.downloadFile(fRel,fSave,p->javafx.application.Platform.runLater(()->{uploadProgressBar.setProgress(p);progressLabel.setText("Downloading... "+(int)(p*100)+"%");}));
                javafx.application.Platform.runLater(()->{uploadProgressBar.setVisible(false);progressLabel.setVisible(false);statusLabel.setText(r.equals("DOWNLOAD_SUCCESS")?"✅ Downloaded: "+fFn:"Download failed: "+r);});
            } catch (Exception e) { javafx.application.Platform.runLater(()->{uploadProgressBar.setVisible(false);progressLabel.setVisible(false);statusLabel.setText("Error: "+e.getMessage());});}
        }); t.setDaemon(true); t.start();
    }

    @FXML private void handleDelete() {
        FileItem sel = fileTable.getSelectionModel().getSelectedItem();
        if (sel==null) { statusLabel.setText("Please select a file or folder to delete."); return; }
        if (inRecycleBin) {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION); a.setTitle("Recycle Bin"); a.setHeaderText("What do you want to do?"); a.setContentText("File: "+sel.getDisplayName());
            ButtonType restore=new ButtonType("♻ Restore"), del=new ButtonType("🗑 Delete Forever"), cancel=new ButtonType("Cancel",ButtonBar.ButtonData.CANCEL_CLOSE);
            a.getButtonTypes().setAll(restore,del,cancel);
            a.showAndWait().ifPresent(r -> { try {
                if (r==restore) { String res=network.restoreFile(sel.getRawName()); statusLabel.setText(res.equals("RESTORE_SUCCESS")?"✅ Restored: "+sel.getDisplayName():"Failed: "+res); if (res.equals("RESTORE_SUCCESS")) refreshBin(); }
                else if (r==del) { String res=network.permanentDelete(sel.getRawName()); statusLabel.setText(res.equals("PERMANENTDELETE_SUCCESS")?"🗑 Permanently deleted.":"Failed: "+res); if (res.equals("PERMANENTDELETE_SUCCESS")) refreshBin(); }
            } catch (Exception e) { statusLabel.setText("Error: "+e.getMessage()); }});
        } else if (inSharedView) {
            statusLabel.setText("Cannot delete shared files.");
        } else {
            String name=sel.getRawName(), path=currentPath.isEmpty()?name:currentPath+"/"+name;
            Alert a = new Alert(Alert.AlertType.CONFIRMATION); a.setTitle("Delete"); a.setHeaderText("Move to Recycle Bin?"); a.setContentText("\""+name+"\" will be moved to the Recycle Bin.");
            a.showAndWait().ifPresent(r -> { if (r==ButtonType.OK) { try {
                String res=sel.isFolder()?network.deleteDir(path):network.deleteFile(path);
                if (res.equals("DELETE_SUCCESS")||res.equals("DELETEDIR_SUCCESS")) { statusLabel.setText("🗑 Moved to Recycle Bin: "+name); showNoPreview("Select a file to preview"); refreshFileList(); }
                else statusLabel.setText("Failed: "+res);
            } catch (Exception e) { statusLabel.setText("Error: "+e.getMessage()); }}});
        }
    }

    @FXML private void handleLogout() {
        if (mediaPlayer!=null) { mediaPlayer.stop(); mediaPlayer.dispose(); }
        SessionManager.clearSession();
        ((Stage)welcomeLabel.getScene().getWindow()).close();
    }

    private void showNoPreview(String msg) { imagePreview.setVisible(false); imagePreview.setImage(null); previewLabel.setText(msg); }
    private String getExtension(String f) { return f.contains(".") ? f.substring(f.lastIndexOf(".")+1).toLowerCase() : ""; }

    public static class FileItem {
        private final String displayName, rawName, size, date;
        private final boolean isFolder;
        public FileItem(String dn, String rn, boolean isFolder, String sz, String dt) { displayName=dn; rawName=rn; this.isFolder=isFolder; size=sz; date=dt; }
        public String getDisplayName() { return displayName; }
        public String getRawName() { return rawName; }
        public boolean isFolder() { return isFolder; }
        public String getSize() { return size; }
        public String getDate() { return date; }
    }
}