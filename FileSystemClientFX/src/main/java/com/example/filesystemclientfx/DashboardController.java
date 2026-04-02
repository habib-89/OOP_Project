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
    @FXML private Button navGroupsBtn;
    @FXML private TextField searchField;
    @FXML private javafx.scene.layout.HBox fileView;
    @FXML private javafx.scene.layout.HBox searchBar;
    @FXML private javafx.scene.layout.HBox actionBar;
    @FXML private javafx.scene.layout.HBox bookmarkButtonsBox;
    @FXML private Button sortBtn;
    @FXML private javafx.scene.layout.HBox tabButtonsBox;
    @FXML private javafx.scene.layout.VBox tableContainer;

    // Video player
    @FXML private javafx.scene.layout.VBox videoView;
    @FXML private MediaView mediaView;
    @FXML private Button playPauseBtn;
    @FXML private Slider seekSlider;
    @FXML private Slider volumeSlider;
    @FXML private Label currentTimeLabel;
    @FXML private Label totalTimeLabel;
    @FXML private Label videoTitleLabel;
    @FXML private Button muteBtn;

    // Image viewer (injected from FXML)
    @FXML private javafx.scene.layout.VBox imageView;
    @FXML private ImageView imageViewFull;
    @FXML private Label imageTitleLabel;
    @FXML private Slider zoomSlider;
    @FXML private Label zoomLabel;

    // PDF viewer (injected from FXML)
    @FXML private javafx.scene.layout.VBox pdfView;
    @FXML private javafx.scene.web.WebView pdfWebView;
    @FXML private Label pdfTitleLabel;
    @FXML private Label pdfPageLabel;

    private MediaPlayer mediaPlayer;
    private boolean videoPlaying = false;
    private boolean isMuted = false;
    private boolean seekDragging = false;

    // Currently open image file (for zoom)
    private File currentImageFile = null;

    // Currently open PDF file path (for system open fallback)
    private File currentPdfFile = null;

    private NetworkManager network;
    private String username;
    private String currentPath = "";
    private boolean isDarkTheme = true;
    private boolean inRecycleBin = false;
    private boolean inSharedView = false;
    private List<FileItem> allItems = new ArrayList<>();

    private List<TabState> tabs = new ArrayList<>();
    private TabState activeTab;
    private int tabCounter = 1;

    private boolean sortAscending = true;
    private String sortColumn = "name";

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg","jpeg","png","gif","bmp","webp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4","avi","mkv","mov");
    private static final Set<String> PDF_EXTENSIONS   = Set.of("pdf");
    private long lastClickTime = 0;

    @FXML
    public void initialize() {
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

        // Zoom slider for image viewer
        if (zoomSlider != null) {
            zoomSlider.setMin(10);
            zoomSlider.setMax(400);
            zoomSlider.setValue(100);
            zoomSlider.valueProperty().addListener((obs, o, n) -> {
                applyZoom(n.doubleValue());
                if (zoomLabel != null) zoomLabel.setText((int) n.doubleValue() + "%");
            });
        }
    }

    public void setUsername(String username) {
        this.username = username;
        welcomeLabel.setText(username);
        if (username.equals("admin")) {
            navAdminBtn.setVisible(true);
            navAdminBtn.setManaged(true);
        }
    }

    public void setNetwork(NetworkManager network) {
        this.network = network;
        openNewTab("");
        loadBookmarksBar();
    }

    // ===================== TABS =====================

    @FXML
    private void handleNewTab() {
        String startPath = activeTab != null ? activeTab.currentPath : "";
        openNewTab(startPath);
    }

    private void openNewTab(String startPath) {
        TableView<FileItem> tv = new TableView<>();
        tv.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        tv.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        javafx.scene.layout.VBox.setVgrow(tv, javafx.scene.layout.Priority.ALWAYS);

        TableColumn<FileItem, String> nameCol = new TableColumn<>("Name");
        nameCol.setPrefWidth(260);
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDisplayName()));
        nameCol.setSortable(true);

        TableColumn<FileItem, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setPrefWidth(100);
        sizeCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSize()));
        sizeCol.setSortable(true);

        TableColumn<FileItem, String> dateCol = new TableColumn<>("Modified");
        dateCol.setPrefWidth(160);
        dateCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDate()));
        dateCol.setSortable(true);

        tv.getColumns().addAll(nameCol, sizeCol, dateCol);
        tv.getStyleClass().add("file-table");

        String tabName = "Tab " + tabCounter++;
        Button tabBtn = new Button(tabName);
        TabState state = new TabState(startPath, tabBtn, tv, nameCol, sizeCol, dateCol);

        tv.setSortPolicy(table -> {
            if (table.getSortOrder().isEmpty()) return true;
            TableColumn<FileItem, ?> col = table.getSortOrder().get(0);
            if (col == nameCol) sortColumn = "name";
            else if (col == sizeCol) sortColumn = "size";
            else if (col == dateCol) sortColumn = "date";
            sortAscending = col.getSortType() == TableColumn.SortType.ASCENDING;
            applySorting();
            return true;
        });

        tabBtn.setStyle(inactiveTabStyle());
        tabBtn.setOnAction(e -> switchToTab(state));
        tabBtn.setOnContextMenuRequested(e -> {
            if (tabs.size() == 1) return;
            ContextMenu menu = new ContextMenu();
            MenuItem close = new MenuItem("✕ Close Tab");
            close.setOnAction(ev -> closeTab(state));
            menu.getItems().add(close);
            menu.show(tabBtn, e.getScreenX(), e.getScreenY());
        });

        tv.setOnMouseClicked(e -> handleFileClick());

        tabs.add(state);
        tabButtonsBox.getChildren().add(tabBtn);
        switchToTab(state);
        refreshFileList();
    }

    private void switchToTab(TabState state) {
        if (activeTab != null) activeTab.tabButton.setStyle(inactiveTabStyle());
        activeTab = state;
        currentPath = state.currentPath;
        inRecycleBin = state.inRecycleBin;
        inSharedView = state.inSharedView;
        allItems = state.allItems;
        tableContainer.getChildren().clear();
        tableContainer.getChildren().add(state.tableView);
        state.tabButton.setStyle(activeTabStyle());
        pathLabel.setText("/ " + (currentPath.isEmpty() ? "Home" : currentPath.replace("/", " / ")));
        loadBookmarksBar();
    }

    private void closeTab(TabState state) {
        int idx = tabs.indexOf(state);
        tabs.remove(state);
        tabButtonsBox.getChildren().remove(state.tabButton);
        if (!tabs.isEmpty()) {
            switchToTab(tabs.get(Math.min(idx, tabs.size() - 1)));
            refreshFileList();
        }
    }

    private String inactiveTabStyle() {
        return "-fx-background-color: rgba(0,212,255,0.05);" +
                "-fx-text-fill: rgba(255,255,255,0.6); -fx-font-size: 11px;" +
                "-fx-font-family: 'Courier New'; -fx-cursor: hand;" +
                "-fx-background-radius: 6 6 0 0; -fx-padding: 5 12 5 12;" +
                "-fx-border-color: rgba(0,212,255,0.2) rgba(0,212,255,0.2) transparent rgba(0,212,255,0.2);" +
                "-fx-border-width: 1; -fx-border-radius: 6 6 0 0;";
    }

    private String activeTabStyle() {
        return "-fx-background-color: rgba(0,212,255,0.15);" +
                "-fx-text-fill: #00d4ff; -fx-font-size: 11px;" +
                "-fx-font-family: 'Courier New'; -fx-cursor: hand;" +
                "-fx-background-radius: 6 6 0 0; -fx-padding: 5 12 5 12;" +
                "-fx-border-color: #00d4ff #00d4ff transparent #00d4ff;" +
                "-fx-border-width: 1; -fx-border-radius: 6 6 0 0;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,212,255,0.3), 6, 0, 0, 0);";
    }

    // ===================== BOOKMARKS =====================

    private void loadBookmarksBar() {
        bookmarkButtonsBox.getChildren().clear();
        List<BookmarkManager.Bookmark> allBookmarks = BookmarkManager.loadBookmarks(username);
        List<BookmarkManager.Bookmark> filtered = new ArrayList<>();
        for (BookmarkManager.Bookmark bm : allBookmarks) {
            String parentPath = bm.path.contains("/") ?
                    bm.path.substring(0, bm.path.lastIndexOf("/")) : "";
            if (parentPath.equals(currentPath)) filtered.add(bm);
        }
        for (BookmarkManager.Bookmark bm : filtered) {
            Button btn = new Button(bm.getDisplayName());
            btn.setStyle("-fx-background-color: rgba(0,212,255,0.08);" +
                    "-fx-text-fill: rgba(255,255,255,0.8); -fx-font-size: 11px;" +
                    "-fx-font-family: 'Courier New'; -fx-cursor: hand;" +
                    "-fx-background-radius: 5; -fx-padding: 3 10 3 10;" +
                    "-fx-border-color: rgba(0,212,255,0.2); -fx-border-width: 1;" +
                    "-fx-border-radius: 5;");
            btn.setOnAction(e -> {
                inRecycleBin = false; inSharedView = false;
                currentPath = bm.isFolder ? bm.path :
                        (bm.path.contains("/") ? bm.path.substring(0, bm.path.lastIndexOf("/")) : "");
                if (activeTab != null) { activeTab.currentPath = currentPath; activeTab.inRecycleBin = false; activeTab.inSharedView = false; }
                navFilesBtn.getStyleClass().add("nav-btn-active");
                navRecycleBtn.getStyleClass().remove("nav-btn-active");
                navSharedBtn.getStyleClass().remove("nav-btn-active");
                refreshFileList();
                statusLabel.setText("⭐ Jumped to: " + bm.name);
            });
            btn.setOnContextMenuRequested(e -> {
                ContextMenu menu = new ContextMenu();
                MenuItem remove = new MenuItem("✕ Remove Bookmark");
                remove.setOnAction(ev -> { BookmarkManager.deleteBookmark(username, bm.path); loadBookmarksBar(); statusLabel.setText("Bookmark removed: " + bm.name); });
                menu.getItems().add(remove);
                menu.show(btn, e.getScreenX(), e.getScreenY());
            });
            bookmarkButtonsBox.getChildren().add(btn);
        }
        if (filtered.isEmpty()) {
            Label hint = new Label("No bookmarks here — select a file or folder and click + Bookmark");
            hint.setStyle("-fx-text-fill: rgba(0,212,255,0.3); -fx-font-size: 10px; -fx-font-family: 'Courier New';");
            bookmarkButtonsBox.getChildren().add(hint);
        }
    }

    @FXML
    private void handleAddBookmark() {
        if (activeTab == null) return;
        FileItem selected = activeTab.tableView.getSelectionModel().getSelectedItem();
        String name, path; boolean isFolder;
        if (selected != null) {
            name = selected.getRawName();
            path = currentPath.isEmpty() ? name : currentPath + "/" + name;
            isFolder = selected.isFolder();
        } else if (!currentPath.isEmpty()) {
            name = currentPath.contains("/") ? currentPath.substring(currentPath.lastIndexOf("/") + 1) : currentPath;
            path = currentPath; isFolder = true;
        } else { statusLabel.setText("Select a file or folder to bookmark."); return; }
        if (BookmarkManager.isBookmarked(username, path)) { statusLabel.setText("Already bookmarked: " + name); return; }
        BookmarkManager.saveBookmark(username, new BookmarkManager.Bookmark(name, path, isFolder));
        loadBookmarksBar();
        statusLabel.setText("⭐ Bookmarked: " + name);
    }

    // ===================== SORTING =====================

    @FXML
    private void handleSort() {
        ContextMenu menu = new ContextMenu();
        MenuItem sortByName = new MenuItem("📝 Name");
        MenuItem sortBySize = new MenuItem("📦 Size");
        MenuItem sortByDate = new MenuItem("📅 Date");
        sortByName.setOnAction(e -> { sortColumn = "name"; sortAscending = true; applySorting(); statusLabel.setText("↕ Sorted by: Name"); });
        sortBySize.setOnAction(e -> { sortColumn = "size"; sortAscending = true; applySorting(); statusLabel.setText("↕ Sorted by: Size"); });
        sortByDate.setOnAction(e -> { sortColumn = "date"; sortAscending = true; applySorting(); statusLabel.setText("↕ Sorted by: Date"); });
        menu.getItems().addAll(sortByName, sortBySize, sortByDate);
        menu.show(sortBtn, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private void applySorting() {
        if (activeTab == null) return;
        java.util.Comparator<FileItem> comparator = switch (sortColumn) {
            case "size" -> java.util.Comparator.comparing(FileItem::getSize);
            case "date" -> java.util.Comparator.comparing(FileItem::getDate);
            default -> java.util.Comparator.comparing(item -> item.getRawName().toLowerCase());
        };
        if (!sortAscending) comparator = comparator.reversed();
        java.util.Comparator<FileItem> fc = comparator;
        java.util.Comparator<FileItem> withFolders = (a, b) -> {
            if (a.isFolder() && !b.isFolder()) return -1;
            if (!a.isFolder() && b.isFolder()) return 1;
            return fc.compare(a, b);
        };
        List<FileItem> sorted = new ArrayList<>(allItems);
        sorted.sort(withFolders);
        activeTab.tableView.setItems(FXCollections.observableArrayList(sorted));
    }

    // ===================== VIEWER SHOW/HIDE HELPERS =====================

    /** Hide all viewers, show normal file browsing UI */
    private void showFileBrowser() {
        fileView.setVisible(true);   fileView.setManaged(true);
        searchBar.setVisible(true);  searchBar.setManaged(true);
        actionBar.setVisible(true);  actionBar.setManaged(true);
        videoView.setVisible(false); videoView.setManaged(false);
        if (imageView != null) { imageView.setVisible(false); imageView.setManaged(false); }
        if (pdfView  != null) { pdfView.setVisible(false);  pdfView.setManaged(false); }
    }

    private void hideFileBrowserForViewer() {
        fileView.setVisible(false);  fileView.setManaged(false);
        searchBar.setVisible(false); searchBar.setManaged(false);
        actionBar.setVisible(false); actionBar.setManaged(false);
        videoView.setVisible(false); videoView.setManaged(false);
        if (imageView != null) { imageView.setVisible(false); imageView.setManaged(false); }
        if (pdfView  != null) { pdfView.setVisible(false);  pdfView.setManaged(false); }
    }

    // ===================== VIDEO PLAYER =====================

    private void showVideoPlayer(File videoFile, String filename) {
        hideFileBrowserForViewer();
        videoView.setVisible(true); videoView.setManaged(true);
        videoTitleLabel.setText(filename);
        statusLabel.setText("Loading: " + filename);

        mediaView.fitWidthProperty().unbind();
        mediaView.fitHeightProperty().unbind();
        mediaView.fitWidthProperty().bind(videoView.widthProperty().subtract(20));
        mediaView.setFitHeight(380);

        try {
            if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.dispose(); mediaPlayer = null; }
            playPauseBtn.setText("▶"); seekSlider.setValue(0);
            currentTimeLabel.setText("00:00"); totalTimeLabel.setText("00:00");
            videoPlaying = false;

            Media media = new Media(videoFile.toURI().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);
            mediaView.setPreserveRatio(true);

            media.setOnError(() -> javafx.application.Platform.runLater(() -> {
                statusLabel.setText("❌ Unsupported format: " + filename);
                handleBackFromVideo();
            }));

            mediaPlayer.setOnReady(() -> {
                Duration total = mediaPlayer.getTotalDuration();
                totalTimeLabel.setText(formatDuration(total));
                seekSlider.setMax(total.toSeconds());
                mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
                mediaPlayer.play(); videoPlaying = true; playPauseBtn.setText("⏸");
                statusLabel.setText("▶ Playing: " + filename);
            });

            mediaPlayer.currentTimeProperty().addListener((obs, o, n) -> {
                if (!seekDragging) seekSlider.setValue(n.toSeconds());
                currentTimeLabel.setText(formatDuration(n));
            });

            mediaPlayer.setOnEndOfMedia(() -> {
                videoPlaying = false; playPauseBtn.setText("▶");
                statusLabel.setText("Finished: " + filename);
                mediaPlayer.stop();
            });

            mediaPlayer.setOnError(() -> javafx.application.Platform.runLater(() -> {
                String err = mediaPlayer.getError() != null ? mediaPlayer.getError().getMessage() : "Unknown error";
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Video Error");
                alert.setHeaderText("Cannot play this video inside FileVault");
                alert.setContentText("Format may not be supported.\n\nError: " + err);
                ButtonType openExt = new ButtonType("Open with System Player");
                ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                alert.getButtonTypes().setAll(openExt, cancel);
                alert.showAndWait().ifPresent(r -> {
                    if (r == openExt) {
                        try { java.awt.Desktop.getDesktop().open(videoFile); }
                        catch (Exception ex) { statusLabel.setText("Could not open: " + ex.getMessage()); }
                    }
                });
                handleBackFromVideo();
            }));

        } catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); handleBackFromVideo(); }
    }

    @FXML private void handleBackFromVideo() {
        if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.dispose(); mediaPlayer = null; }
        videoPlaying = false; playPauseBtn.setText("▶");
        seekSlider.setValue(0); currentTimeLabel.setText("00:00"); totalTimeLabel.setText("00:00");
        showFileBrowser();
        statusLabel.setText("Back to files.");
    }

    @FXML private void handleVideoPlayPause() {
        if (mediaPlayer == null) return;
        if (videoPlaying) { mediaPlayer.pause(); playPauseBtn.setText("▶"); }
        else              { mediaPlayer.play();  playPauseBtn.setText("⏸"); }
        videoPlaying = !videoPlaying;
    }
    @FXML private void handleVideoRewind()  { if (mediaPlayer != null) mediaPlayer.seek(mediaPlayer.getCurrentTime().subtract(Duration.seconds(10))); }
    @FXML private void handleVideoForward() { if (mediaPlayer != null) mediaPlayer.seek(mediaPlayer.getCurrentTime().add(Duration.seconds(10))); }
    @FXML private void handleVideoMute() {
        if (mediaPlayer == null) return;
        isMuted = !isMuted; mediaPlayer.setMute(isMuted); muteBtn.setText(isMuted ? "🔇" : "🔊");
    }

    private String formatDuration(Duration d) {
        int s = (int) d.toSeconds(), h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
    }

    // ===================== IMAGE VIEWER =====================

    private void showImageViewer(File imageFile, String filename) {
        if (imageView == null) {
            // Fallback: show in preview panel if FXML not updated yet
            try {
                imagePreview.setImage(new Image(new FileInputStream(imageFile)));
                imagePreview.setVisible(true);
                previewLabel.setText(filename);
            } catch (Exception e) { showNoPreview("Could not display image."); }
            return;
        }

        hideFileBrowserForViewer();
        imageView.setVisible(true); imageView.setManaged(true);
        imageTitleLabel.setText(filename);
        currentImageFile = imageFile;

        try {
            Image img = new Image(new FileInputStream(imageFile));
            imageViewFull.setImage(img);
            imageViewFull.setPreserveRatio(true);
            // Fit to viewer width/height
            imageViewFull.fitWidthProperty().bind(imageView.widthProperty().subtract(40));
            imageViewFull.fitHeightProperty().bind(imageView.heightProperty().subtract(120));
            if (zoomSlider != null) { zoomSlider.setValue(100); zoomLabel.setText("100%"); }
            statusLabel.setText("🖼 " + filename + "  (" + (int) img.getWidth() + " × " + (int) img.getHeight() + " px)");
        } catch (Exception e) {
            statusLabel.setText("Could not display image: " + e.getMessage());
            handleBackFromImage();
        }
    }

    private void applyZoom(double percent) {
        if (imageViewFull == null || currentImageFile == null) return;
        // Unbind so we can set explicit size
        imageViewFull.fitWidthProperty().unbind();
        imageViewFull.fitHeightProperty().unbind();
        double base = 600;
        imageViewFull.setFitWidth(base * percent / 100.0);
        imageViewFull.setFitHeight(base * percent / 100.0);
    }

    @FXML private void handleBackFromImage() {
        currentImageFile = null;
        if (imageViewFull != null) {
            imageViewFull.fitWidthProperty().unbind();
            imageViewFull.fitHeightProperty().unbind();
            imageViewFull.setImage(null);
        }
        showFileBrowser();
        statusLabel.setText("Back to files.");
    }

    @FXML private void handleZoomIn() {
        if (zoomSlider != null) zoomSlider.setValue(Math.min(400, zoomSlider.getValue() + 25));
    }

    @FXML private void handleZoomOut() {
        if (zoomSlider != null) zoomSlider.setValue(Math.max(10, zoomSlider.getValue() - 25));
    }

    @FXML private void handleZoomReset() {
        if (zoomSlider != null) zoomSlider.setValue(100);
    }

    @FXML private void handleOpenImageExternal() {
        if (currentImageFile != null) {
            try { java.awt.Desktop.getDesktop().open(currentImageFile); }
            catch (Exception e) { statusLabel.setText("Could not open: " + e.getMessage()); }
        }
    }

    // ===================== PDF VIEWER =====================

    private void showPdfViewer(File pdfFile, String filename) {
        if (pdfView == null || pdfWebView == null) {
            // Fallback: open with system app
            try { java.awt.Desktop.getDesktop().open(pdfFile); statusLabel.setText("Opened PDF: " + filename); }
            catch (Exception e) { statusLabel.setText("Cannot open PDF: " + e.getMessage()); }
            return;
        }

        hideFileBrowserForViewer();
        pdfView.setVisible(true); pdfView.setManaged(true);
        pdfTitleLabel.setText(filename);
        currentPdfFile = pdfFile;

        // Use WebView to render the PDF via browser's built-in PDF renderer
        String url = pdfFile.toURI().toString();
        pdfWebView.getEngine().load(url);
        pdfWebView.getEngine().setOnError(e -> javafx.application.Platform.runLater(() -> {
            statusLabel.setText("Could not render PDF in viewer.");
        }));

        statusLabel.setText("📄 " + filename);
    }

    @FXML private void handleBackFromPdf() {
        if (pdfWebView != null) pdfWebView.getEngine().load("about:blank");
        currentPdfFile = null;
        showFileBrowser();
        statusLabel.setText("Back to files.");
    }

    @FXML private void handleOpenPdfExternal() {
        if (currentPdfFile != null) {
            try { java.awt.Desktop.getDesktop().open(currentPdfFile); }
            catch (Exception e) { statusLabel.setText("Could not open: " + e.getMessage()); }
        }
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
                allItems.add(new FileItem("📁  " + f, f, true, sz, dt));
            }
            for (String f : result.files) {
                String sz = si < result.sizes.size() ? result.sizes.get(si++) : "—";
                String dt = di < result.dates.size() ? result.dates.get(di++) : "—";
                allItems.add(new FileItem(iconFor(f) + "  " + f, f, false, sz, dt));
            }
            if (activeTab != null) {
                activeTab.currentPath = currentPath;
                activeTab.allItems = allItems;
                activeTab.tableView.setItems(FXCollections.observableArrayList(allItems));
            }
            pathLabel.setText("/ " + (currentPath.isEmpty() ? "Home" : currentPath.replace("/", " / ")));
            statusLabel.setText(allItems.isEmpty() ? "This folder is empty." :
                    result.folders.size() + " folder(s)  •  " + result.files.size() + " file(s)");
        } catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); }
        loadBookmarksBar();
        applySorting();
    }

    /** Returns the right emoji icon for a file based on its extension */
    private String iconFor(String filename) {
        String ext = getExtension(filename);
        if (IMAGE_EXTENSIONS.contains(ext)) return "🖼";
        if (VIDEO_EXTENSIONS.contains(ext)) return "🎬";
        if (PDF_EXTENSIONS.contains(ext))   return "📕";
        return "📄";
    }

    private void refreshBin() {
        try {
            List<String> binFiles = network.listBin();
            allItems = new ArrayList<>();
            for (String r : binFiles) {
                String dn = r.contains("##") ? r.substring(r.lastIndexOf("##") + 2) : r;
                allItems.add(new FileItem("🗑  " + dn, r, false, "—", "—"));
            }
            if (activeTab != null) { activeTab.allItems = allItems; activeTab.tableView.setItems(FXCollections.observableArrayList(allItems)); }
            statusLabel.setText(allItems.isEmpty() ? "Recycle Bin is empty." : allItems.size() + " item(s) in Recycle Bin");
        } catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); }
        applySorting();
    }

    private void refreshShared() {
        try {
            List<NetworkManager.SharedFileInfo> sf = network.listSharedWithMe();
            allItems = new ArrayList<>();
            for (NetworkManager.SharedFileInfo s : sf)
                allItems.add(new FileItem("📤  " + s.filename + "  (from: " + s.sharedBy + ")", s.sharedBy + "|" + s.filePath, false, "—", "—"));
            if (activeTab != null) { activeTab.allItems = allItems; activeTab.tableView.setItems(FXCollections.observableArrayList(allItems)); }
            statusLabel.setText(allItems.isEmpty() ? "No files shared with you yet." : allItems.size() + " file(s) shared with you.");
        } catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); }
        applySorting();
    }

    @FXML private void handleSearch() {
        if (activeTab == null) return;
        String q = searchField.getText().toLowerCase().trim();
        if (q.isEmpty()) { activeTab.tableView.setItems(FXCollections.observableArrayList(allItems)); return; }
        List<FileItem> f = new ArrayList<>();
        for (FileItem i : allItems) if (i.getRawName().toLowerCase().contains(q)) f.add(i);
        activeTab.tableView.setItems(FXCollections.observableArrayList(f));
        statusLabel.setText("Found " + f.size() + " result(s) for \"" + q + "\"");
    }

    // ===================== NAV =====================

    @FXML private void handleNavFiles() {
        inRecycleBin = false; inSharedView = false; currentPath = "";
        if (activeTab != null) { activeTab.inRecycleBin = false; activeTab.inSharedView = false; activeTab.currentPath = ""; }
        navFilesBtn.getStyleClass().add("nav-btn-active");
        navRecycleBtn.getStyleClass().remove("nav-btn-active");
        navSharedBtn.getStyleClass().remove("nav-btn-active");
        showNoPreview("Select a file to preview"); refreshFileList();
    }

    @FXML private void handleNavRecycleBin() {
        inRecycleBin = true; inSharedView = false;
        if (activeTab != null) { activeTab.inRecycleBin = true; activeTab.inSharedView = false; }
        navRecycleBtn.getStyleClass().add("nav-btn-active");
        navFilesBtn.getStyleClass().remove("nav-btn-active");
        navSharedBtn.getStyleClass().remove("nav-btn-active");
        pathLabel.setText("/ Recycle Bin"); showNoPreview("Select a file to preview"); refreshBin();
    }

    @FXML private void handleNavShared() {
        inSharedView = true; inRecycleBin = false;
        if (activeTab != null) { activeTab.inSharedView = true; activeTab.inRecycleBin = false; }
        navSharedBtn.getStyleClass().add("nav-btn-active");
        navFilesBtn.getStyleClass().remove("nav-btn-active");
        navRecycleBtn.getStyleClass().remove("nav-btn-active");
        pathLabel.setText("/ Shared with Me"); showNoPreview("Select a file to preview"); refreshShared();
    }

    @FXML private void handleNavGroups() {
        navFilesBtn.getStyleClass().remove("nav-btn-active");
        navRecycleBtn.getStyleClass().remove("nav-btn-active");
        navSharedBtn.getStyleClass().remove("nav-btn-active");
        if (navGroupsBtn != null && !navGroupsBtn.getStyleClass().contains("nav-btn-active"))
            navGroupsBtn.getStyleClass().add("nav-btn-active");
        showNoPreview("Groups feature is not available in this build.");
        statusLabel.setText("Groups feature is not available in this build.");
    }

    @FXML private void handleCreateGroup()       { statusLabel.setText("Groups feature is not available in this build."); }
    @FXML private void handleAddGroupMember()    { statusLabel.setText("Groups feature is not available in this build."); }
    @FXML private void handleRemoveGroupMember() { statusLabel.setText("Groups feature is not available in this build."); }
    @FXML private void handleViewGroupMembers()  { statusLabel.setText("Groups feature is not available in this build."); }

    @FXML private void handleNavAdmin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Admin.fxml"));
            Scene scene = new Scene(loader.load(), 800, 550);
            var css = getClass().getResource("dashboard.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            AdminController c = loader.getController(); c.setNetwork(network);
            Stage s = new Stage(); s.setTitle("FileVault — Admin Panel"); s.setScene(scene); s.show();
        } catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); }
    }

    @FXML private void handleThemeToggle() {
        Scene scene = welcomeLabel.getScene();
        if (isDarkTheme) { scene.getRoot().getStyleClass().remove("root-dark"); scene.getRoot().getStyleClass().add("root-light"); themeToggleBtn.setText("🌙 Dark Mode"); isDarkTheme = false; }
        else             { scene.getRoot().getStyleClass().remove("root-light"); scene.getRoot().getStyleClass().add("root-dark"); themeToggleBtn.setText("☀ Light Mode"); isDarkTheme = true; }
    }

    // ===================== FILE CLICK =====================

    @FXML private void handleFileClick() {
        if (activeTab == null) return;
        FileItem selected = activeTab.tableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        boolean dc = isDoubleClick();

        if (selected.isFolder()) {
            if (dc) {
                currentPath = currentPath.isEmpty() ? selected.getRawName() : currentPath + "/" + selected.getRawName();
                if (activeTab != null) activeTab.currentPath = currentPath;
                showNoPreview("Select a file to preview"); refreshFileList();
            }
        } else {
            if (inSharedView) {
                String[] p = selected.getRawName().split("\\|", 2);
                if (p.length < 2) return;
                String fp = p[1], fn = fp.contains("/") ? fp.substring(fp.lastIndexOf("/") + 1) : fp;
                String ext = getExtension(fn);
                if (dc) {
                    if (VIDEO_EXTENSIONS.contains(ext))       downloadAndPlaySharedVideo(selected);
                    else if (IMAGE_EXTENSIONS.contains(ext))  downloadAndOpenSharedImage(selected);
                    else if (PDF_EXTENSIONS.contains(ext))    downloadAndOpenSharedPdf(selected);
                    else                                       openSharedWithSystemApp(selected);
                } else {
                    updateSingleClickPreview(ext, fn);
                }
            } else {
                String fn = selected.getRawName(), ext = getExtension(fn);
                if (dc) {
                    if (VIDEO_EXTENSIONS.contains(ext))       downloadAndPlayVideo(fn);
                    else if (IMAGE_EXTENSIONS.contains(ext))  downloadAndOpenImage(fn);
                    else if (PDF_EXTENSIONS.contains(ext))    downloadAndOpenPdf(fn);
                    else                                       openWithSystemApp(fn, ext);
                } else {
                    if (IMAGE_EXTENSIONS.contains(ext)) previewImage(fn);
                    else updateSingleClickPreview(ext, fn);
                }
            }
        }
    }

    private void updateSingleClickPreview(String ext, String filename) {
        if (VIDEO_EXTENSIONS.contains(ext))      showNoPreview("🎬 Double-click to play: " + filename);
        else if (PDF_EXTENSIONS.contains(ext))   showNoPreview("📕 Double-click to open PDF: " + filename);
        else if (IMAGE_EXTENSIONS.contains(ext)) showNoPreview("🖼 Double-click to open image: " + filename);
        else                                     showNoPreview("Double-click to open: " + filename);
    }

    private boolean isDoubleClick() {
        long now = System.currentTimeMillis(); boolean d = (now - lastClickTime) < 400; lastClickTime = now; return d;
    }

    // ===================== VIDEO HELPERS =====================

    private void downloadAndPlayVideo(String filename) {
        String rel = currentPath.isEmpty() ? filename : currentPath + "/" + filename;
        File tmp = new File(new File(System.getProperty("java.io.tmpdir"), "filevault"), filename);
        tmp.getParentFile().mkdirs();
        showProgress("Loading video...");
        Thread t = new Thread(() -> {
            try {
                String r = network.downloadFile(rel, tmp, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Loading video...")));
                javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("DOWNLOAD_SUCCESS")) showVideoPlayer(tmp, filename); else statusLabel.setText("Failed: " + r); });
            } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); }
        }); t.setDaemon(true); t.start();
    }

    private void downloadAndPlaySharedVideo(FileItem selected) {
        String[] p = selected.getRawName().split("\\|", 2); String owner = p[0], fp = p[1];
        String fn = fp.contains("/") ? fp.substring(fp.lastIndexOf("/") + 1) : fp;
        File tmp = new File(new File(System.getProperty("java.io.tmpdir"), "filevault"), fn);
        tmp.getParentFile().mkdirs();
        showProgress("Loading video...");
        Thread t = new Thread(() -> {
            try {
                String r = network.downloadSharedFile(owner, fp, tmp, prog -> javafx.application.Platform.runLater(() -> updateProgress(prog, "Loading video...")));
                javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("DOWNLOAD_SUCCESS")) showVideoPlayer(tmp, fn); else statusLabel.setText("Failed: " + r); });
            } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); }
        }); t.setDaemon(true); t.start();
    }

    // ===================== IMAGE OPEN =====================

    private void downloadAndOpenImage(String filename) {
        String rel = currentPath.isEmpty() ? filename : currentPath + "/" + filename;
        File tmp = new File(new File(System.getProperty("java.io.tmpdir"), "filevault"), filename);
        tmp.getParentFile().mkdirs();
        showProgress("Loading image...");
        Thread t = new Thread(() -> {
            try {
                String r = network.downloadFile(rel, tmp, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Loading image...")));
                javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("DOWNLOAD_SUCCESS")) showImageViewer(tmp, filename); else statusLabel.setText("Failed: " + r); });
            } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); }
        }); t.setDaemon(true); t.start();
    }

    private void downloadAndOpenSharedImage(FileItem selected) {
        String[] p = selected.getRawName().split("\\|", 2); String owner = p[0], fp = p[1];
        String fn = fp.contains("/") ? fp.substring(fp.lastIndexOf("/") + 1) : fp;
        File tmp = new File(new File(System.getProperty("java.io.tmpdir"), "filevault"), fn);
        tmp.getParentFile().mkdirs();
        showProgress("Loading image...");
        Thread t = new Thread(() -> {
            try {
                String r = network.downloadSharedFile(owner, fp, tmp, prog -> javafx.application.Platform.runLater(() -> updateProgress(prog, "Loading image...")));
                javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("DOWNLOAD_SUCCESS")) showImageViewer(tmp, fn); else statusLabel.setText("Failed: " + r); });
            } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); }
        }); t.setDaemon(true); t.start();
    }

    // ===================== PDF OPEN =====================

    private void downloadAndOpenPdf(String filename) {
        String rel = currentPath.isEmpty() ? filename : currentPath + "/" + filename;
        File tmp = new File(new File(System.getProperty("java.io.tmpdir"), "filevault"), filename);
        tmp.getParentFile().mkdirs();
        showProgress("Loading PDF...");
        Thread t = new Thread(() -> {
            try {
                String r = network.downloadFile(rel, tmp, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Loading PDF...")));
                javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("DOWNLOAD_SUCCESS")) showPdfViewer(tmp, filename); else statusLabel.setText("Failed: " + r); });
            } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); }
        }); t.setDaemon(true); t.start();
    }

    private void downloadAndOpenSharedPdf(FileItem selected) {
        String[] p = selected.getRawName().split("\\|", 2); String owner = p[0], fp = p[1];
        String fn = fp.contains("/") ? fp.substring(fp.lastIndexOf("/") + 1) : fp;
        File tmp = new File(new File(System.getProperty("java.io.tmpdir"), "filevault"), fn);
        tmp.getParentFile().mkdirs();
        showProgress("Loading PDF...");
        Thread t = new Thread(() -> {
            try {
                String r = network.downloadSharedFile(owner, fp, tmp, prog -> javafx.application.Platform.runLater(() -> updateProgress(prog, "Loading PDF...")));
                javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("DOWNLOAD_SUCCESS")) showPdfViewer(tmp, fn); else statusLabel.setText("Failed: " + r); });
            } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); }
        }); t.setDaemon(true); t.start();
    }

    // ===================== IMAGE PREVIEW (sidebar) =====================

    private void previewImage(String filename) {
        try {
            String rel = currentPath.isEmpty() ? filename : currentPath + "/" + filename;
            File tmp = File.createTempFile("preview_", "." + getExtension(filename)); tmp.deleteOnExit();
            if (network.downloadFile(rel, tmp).equals("DOWNLOAD_SUCCESS")) {
                imagePreview.setImage(new Image(new FileInputStream(tmp)));
                imagePreview.setVisible(true);
                previewLabel.setText(filename);
            } else showNoPreview("Could not load preview.");
        } catch (Exception e) { showNoPreview("Preview error."); }
    }

    // ===================== OPEN WITH SYSTEM APP =====================

    private void openWithSystemApp(String filename, String ext) {
        String rel = currentPath.isEmpty() ? filename : currentPath + "/" + filename;
        File tmp = new File(new File(System.getProperty("java.io.tmpdir"), "filevault"), filename);
        tmp.getParentFile().mkdirs();
        statusLabel.setText("Opening " + filename + "...");
        Thread t = new Thread(() -> {
            try {
                String r = network.downloadFile(rel, tmp);
                javafx.application.Platform.runLater(() -> {
                    if (r.equals("DOWNLOAD_SUCCESS")) { try { java.awt.Desktop.getDesktop().open(tmp); statusLabel.setText("Opened: " + filename); } catch (Exception e) { statusLabel.setText("Could not open: " + e.getMessage()); } }
                    else statusLabel.setText("Failed: " + r);
                });
            } catch (Exception e) { javafx.application.Platform.runLater(() -> statusLabel.setText("Error: " + e.getMessage())); }
        }); t.setDaemon(true); t.start();
    }

    private void openSharedWithSystemApp(FileItem selected) {
        String[] p = selected.getRawName().split("\\|", 2); String owner = p[0], fp = p[1];
        String fn = fp.contains("/") ? fp.substring(fp.lastIndexOf("/") + 1) : fp;
        File tmp = new File(new File(System.getProperty("java.io.tmpdir"), "filevault"), fn);
        tmp.getParentFile().mkdirs();
        statusLabel.setText("Opening " + fn + "...");
        Thread t = new Thread(() -> {
            try {
                String r = network.downloadSharedFile(owner, fp, tmp);
                javafx.application.Platform.runLater(() -> {
                    if (r.equals("DOWNLOAD_SUCCESS")) { try { java.awt.Desktop.getDesktop().open(tmp); statusLabel.setText("Opened: " + fn); } catch (Exception e) { statusLabel.setText("Could not open: " + e.getMessage()); } }
                    else statusLabel.setText("Failed: " + r);
                });
            } catch (Exception e) { javafx.application.Platform.runLater(() -> statusLabel.setText("Error: " + e.getMessage())); }
        }); t.setDaemon(true); t.start();
    }

    // ===================== PROGRESS HELPERS =====================

    private void showProgress(String msg) {
        uploadProgressBar.setProgress(0); uploadProgressBar.setVisible(true);
        progressLabel.setVisible(true); progressLabel.setText(msg);
    }

    private void updateProgress(double p, String prefix) {
        uploadProgressBar.setProgress(p);
        progressLabel.setText(prefix + " " + (int)(p * 100) + "%");
    }

    private void hideProgress() {
        uploadProgressBar.setVisible(false); progressLabel.setVisible(false);
    }

    // ===================== FILE OPERATIONS =====================

    @FXML private void handleGoUp() {
        if (currentPath.isEmpty()) { statusLabel.setText("Already at root."); return; }
        int i = currentPath.lastIndexOf("/"); currentPath = i == -1 ? "" : currentPath.substring(0, i);
        if (activeTab != null) activeTab.currentPath = currentPath;
        showNoPreview("Select a file to preview"); refreshFileList();
    }

    @FXML private void handleCreateFolder() {
        TextInputDialog d = new TextInputDialog(); d.setTitle("New Folder"); d.setHeaderText("Create a new folder"); d.setContentText("Folder name:");
        d.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) { statusLabel.setText("Folder name cannot be empty."); return; }
            try { String r = network.makeDir(currentPath.isEmpty() ? name : currentPath + "/" + name); if (r.equals("MKDIR_SUCCESS")) { statusLabel.setText("✅ Folder created: " + name); refreshFileList(); } else statusLabel.setText("Failed: " + r); }
            catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); }
        });
    }

    @FXML private void handleRename() {
        if (activeTab == null) return;
        FileItem sel = activeTab.tableView.getSelectionModel().getSelectedItem();
        if (sel == null) { statusLabel.setText("Please select a file or folder to rename."); return; }
        String old = sel.getRawName(), oldP = currentPath.isEmpty() ? old : currentPath + "/" + old;
        TextInputDialog d = new TextInputDialog(old); d.setTitle("Rename"); d.setHeaderText("Rename " + (sel.isFolder() ? "folder" : "file")); d.setContentText("New name:");
        d.showAndWait().ifPresent(newName -> {
            if (newName.trim().isEmpty()) { statusLabel.setText("Name cannot be empty."); return; }
            try { String r = network.renameDir(oldP, currentPath.isEmpty() ? newName : currentPath + "/" + newName); if (r.equals("RENAME_SUCCESS")) { statusLabel.setText("✅ Renamed to: " + newName); refreshFileList(); } else statusLabel.setText("Failed: " + r); }
            catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); }
        });
    }

    @FXML private void handleMoveFile() {
        if (activeTab == null) return;
        List<FileItem> sel = new ArrayList<>(activeTab.tableView.getSelectionModel().getSelectedItems());
        List<String> files = new ArrayList<>(); for (FileItem i : sel) if (!i.isFolder()) files.add(i.getRawName());
        if (files.isEmpty()) { statusLabel.setText("Please select one or more files to move."); return; }
        TextInputDialog d = new TextInputDialog(); d.setTitle("Move Files"); d.setHeaderText("Move " + files.size() + " file(s)"); d.setContentText("Destination folder:");
        d.showAndWait().ifPresent(dest -> {
            int ok = 0, fail = 0;
            for (String f : files) { try { if (network.moveFile(currentPath.isEmpty() ? f : currentPath + "/" + f, dest).equals("MOVEFILE_SUCCESS")) ok++; else fail++; } catch (Exception e) { fail++; } }
            statusLabel.setText(fail == 0 ? "✅ Moved " + ok + " file(s) to: " + dest : "Moved " + ok + ", failed " + fail); refreshFileList();
        });
    }

    @FXML private void handleShare() {
        if (inRecycleBin || inSharedView) { statusLabel.setText("Can only share files from My Files."); return; }
        if (activeTab == null) return;
        FileItem sel = activeTab.tableView.getSelectionModel().getSelectedItem();
        if (sel == null || sel.isFolder()) { statusLabel.setText("Please select a file to share."); return; }
        String fn = sel.getRawName(), fp = currentPath.isEmpty() ? fn : currentPath + "/" + fn;
        TextInputDialog d = new TextInputDialog(); d.setTitle("Share File"); d.setHeaderText("Share \"" + fn + "\""); d.setContentText("Enter username:");
        d.showAndWait().ifPresent(u -> {
            if (u.trim().isEmpty()) { statusLabel.setText("Username cannot be empty."); return; }
            try { String r = network.shareFile(u.trim(), fp); statusLabel.setText(r.equals("SHARE_SUCCESS") ? "✅ Shared \"" + fn + "\" with " + u : "Failed: " + r.replace("ERROR ", "")); }
            catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); }
        });
    }

    @FXML private void handleRefresh() {
        if (inRecycleBin) refreshBin(); else if (inSharedView) refreshShared(); else refreshFileList();
        statusLabel.setText("✅ Refreshed.");
    }

    @FXML private void handleUpload() {
        FileChooser fc = new FileChooser(); fc.setTitle("Select File to Upload");
        File f = fc.showOpenDialog((Stage) welcomeLabel.getScene().getWindow());
        if (f != null) {
            showProgress("Uploading...");
            Thread t = new Thread(() -> {
                try {
                    String r = network.uploadFile(f, currentPath, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Uploading...")));
                    javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("UPLOAD_SUCCESS")) { statusLabel.setText("✅ Uploaded: " + f.getName()); refreshFileList(); } else statusLabel.setText("Upload failed: " + r); });
                } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); }
            }); t.setDaemon(true); t.start();
        }
    }

    @FXML private void handleDownload() {
        if (activeTab == null) return;
        FileItem sel = activeTab.tableView.getSelectionModel().getSelectedItem();
        if (sel == null || sel.isFolder()) { statusLabel.setText("Please select a file to download."); return; }
        String fn, ext, owner = null, fp = null, rel = null;
        if (inSharedView) { String[] p = sel.getRawName().split("\\|", 2); owner = p[0]; fp = p[1]; fn = fp.contains("/") ? fp.substring(fp.lastIndexOf("/") + 1) : fp; ext = getExtension(fn); }
        else { fn = sel.getRawName(); rel = currentPath.isEmpty() ? fn : currentPath + "/" + fn; ext = getExtension(fn); }
        FileChooser fc = new FileChooser(); fc.setTitle("Save File As"); fc.setInitialFileName(fn);
        if (!ext.isEmpty()) fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(ext.toUpperCase() + " files", "*." + ext));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        File saveTo = fc.showSaveDialog((Stage) welcomeLabel.getScene().getWindow());
        if (saveTo == null) return;
        if (!ext.isEmpty() && !saveTo.getAbsolutePath().endsWith("." + ext)) saveTo = new File(saveTo.getAbsolutePath() + "." + ext);
        final File fSave = saveTo; final String fFn = fn, fOwner = owner, fFp = fp, fRel = rel;
        showProgress("Downloading...");
        Thread t = new Thread(() -> {
            try {
                String r = inSharedView
                        ? network.downloadSharedFile(fOwner, fFp, fSave, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Downloading...")))
                        : network.downloadFile(fRel, fSave, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Downloading...")));
                javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText(r.equals("DOWNLOAD_SUCCESS") ? "✅ Downloaded: " + fFn : "Download failed: " + r); });
            } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); }
        }); t.setDaemon(true); t.start();
    }

    @FXML private void handleDelete() {
        if (activeTab == null) return;
        FileItem sel = activeTab.tableView.getSelectionModel().getSelectedItem();
        if (sel == null) { statusLabel.setText("Please select a file or folder to delete."); return; }
        if (inRecycleBin) {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION); a.setTitle("Recycle Bin"); a.setHeaderText("What do you want to do?"); a.setContentText("File: " + sel.getDisplayName());
            ButtonType restore = new ButtonType("♻ Restore"), del = new ButtonType("🗑 Delete Forever"), cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            a.getButtonTypes().setAll(restore, del, cancel);
            a.showAndWait().ifPresent(r -> { try {
                if (r == restore) { String res = network.restoreFile(sel.getRawName()); statusLabel.setText(res.equals("RESTORE_SUCCESS") ? "✅ Restored: " + sel.getDisplayName() : "Failed: " + res); if (res.equals("RESTORE_SUCCESS")) refreshBin(); }
                else if (r == del) { String res = network.permanentDelete(sel.getRawName()); statusLabel.setText(res.equals("PERMANENTDELETE_SUCCESS") ? "🗑 Permanently deleted." : "Failed: " + res); if (res.equals("PERMANENTDELETE_SUCCESS")) refreshBin(); }
            } catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); } });
        } else if (inSharedView) {
            statusLabel.setText("Cannot delete shared files.");
        } else {
            String name = sel.getRawName(), path = currentPath.isEmpty() ? name : currentPath + "/" + name;
            Alert a = new Alert(Alert.AlertType.CONFIRMATION); a.setTitle("Delete"); a.setHeaderText("Move to Recycle Bin?"); a.setContentText("\"" + name + "\" will be moved to the Recycle Bin.");
            a.showAndWait().ifPresent(r -> { if (r == ButtonType.OK) { try {
                String res = sel.isFolder() ? network.deleteDir(path) : network.deleteFile(path);
                if (res.equals("DELETE_SUCCESS") || res.equals("DELETEDIR_SUCCESS")) { statusLabel.setText("🗑 Moved to Recycle Bin: " + name); showNoPreview("Select a file to preview"); refreshFileList(); }
                else statusLabel.setText("Failed: " + res);
            } catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); } } });
        }
    }

    @FXML private void handleLogout() {
        if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.dispose(); }
        SessionManager.clearSession();
        ((Stage) welcomeLabel.getScene().getWindow()).close();
    }

    private void showNoPreview(String msg) { imagePreview.setVisible(false); imagePreview.setImage(null); previewLabel.setText(msg); }
    private String getExtension(String f) { return f.contains(".") ? f.substring(f.lastIndexOf(".") + 1).toLowerCase() : ""; }

    // ===================== FileItem =====================

    public static class FileItem {
        private final String displayName, rawName, size, date;
        private final boolean isFolder;
        public FileItem(String dn, String rn, boolean isFolder, String sz, String dt) { displayName = dn; rawName = rn; this.isFolder = isFolder; size = sz; date = dt; }
        public String getDisplayName() { return displayName; }
        public String getRawName()     { return rawName; }
        public boolean isFolder()      { return isFolder; }
        public String getSize()        { return size; }
        public String getDate()        { return date; }
    }

    // ===================== TabState =====================

    private static class TabState {
        String currentPath;
        boolean inRecycleBin = false, inSharedView = false;
        List<FileItem> allItems = new ArrayList<>();
        Button tabButton;
        TableView<FileItem> tableView;
        TableColumn<FileItem, String> nameCol, sizeCol, dateCol;

        TabState(String path, Button btn, TableView<FileItem> tv,
                 TableColumn<FileItem, String> nameCol,
                 TableColumn<FileItem, String> sizeCol,
                 TableColumn<FileItem, String> dateCol) {
            this.currentPath = path; this.tabButton = btn; this.tableView = tv;
            this.nameCol = nameCol; this.sizeCol = sizeCol; this.dateCol = dateCol;
        }
    }
}