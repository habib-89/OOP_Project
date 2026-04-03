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
import javafx.scene.shape.Circle;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Rectangle2D;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class DashboardController {


    @FXML private Label welcomeLabel;
    @FXML private Label statusLabel;
    @FXML private ImageView imagePreview;
    @FXML private ImageView profileImageView;
    @FXML private Circle profileBorderCircle;
    @FXML private Button changeProfilePicBtn;
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
    @FXML private Button createGroupBtn;
    @FXML private Button addGroupMemberBtn;
    @FXML private javafx.scene.layout.HBox tabButtonsBox;
    @FXML private javafx.scene.layout.VBox tableContainer;

    // ── Video player ──────────────────────────────────────────────────────────
    @FXML private javafx.scene.layout.VBox videoView;
    @FXML private MediaView mediaView;
    @FXML private Button playPauseBtn;
    @FXML private Slider seekSlider;
    @FXML private Slider volumeSlider;
    @FXML private Label currentTimeLabel;
    @FXML private Label totalTimeLabel;
    @FXML private Label videoTitleLabel;
    @FXML private Button muteBtn;

    // ── Image viewer ──────────────────────────────────────────────────────────
    @FXML private javafx.scene.layout.VBox imageView;
    @FXML private ImageView imageViewFull;
    @FXML private Label imageTitleLabel;
    @FXML private Slider zoomSlider;
    @FXML private Label zoomLabel;

    // ── PDF viewer ────────────────────────────────────────────────────────────
    @FXML private javafx.scene.layout.VBox pdfView;
    @FXML private javafx.scene.web.WebView pdfWebView;
    @FXML private Label pdfTitleLabel;
    @FXML private Label pdfPageLabel;

    @FXML private javafx.scene.layout.VBox previewPanel;
    @FXML private javafx.scene.layout.VBox discussionPanel;
    @FXML private Label discussionTitleLabel;
    @FXML private TextArea discussionDescArea;
    @FXML private javafx.scene.layout.VBox commentsBox;
    @FXML private TextField commentInputField;
    private String currentDiscussionFile = null;
    private boolean discussionListenerActive = false;

    // ── Media state ───────────────────────────────────────────────────────────
    private MediaPlayer mediaPlayer;
    private boolean videoPlaying  = false;
    private boolean isMuted       = false;
    private boolean seekDragging  = false;

    // Currently open image / PDF file (for zoom / external open)
    private File currentImageFile = null;
    private File currentPdfFile   = null;

    // ── App state ─────────────────────────────────────────────────────────────
    private NetworkManager network;
    private String username;
    private String currentPath      = "";
    private boolean isDarkTheme     = true;
    private boolean inRecycleBin    = false;
    private boolean inSharedView    = false;
    private boolean inGroupView     = false;
    private String  currentGroupId   = null;
    private String  currentGroupName = null;
    private String  currentGroupPath = "";
    private List<FileItem> allItems  = new ArrayList<>();

    private List<TabState> tabs = new ArrayList<>();
    private TabState activeTab;
    private int tabCounter = 1;

    private boolean sortAscending = true;
    private String  sortColumn    = "name";

    private static final Set<String> IMAGE_EXTENSIONS =
            Set.of("jpg","jpeg","png","gif","bmp","webp");
    private static final Set<String> VIDEO_EXTENSIONS =
            Set.of("mp4","avi","mkv","mov");
    private static final Set<String> PDF_EXTENSIONS =
            Set.of("pdf");

    private long lastClickTime = 0;


    private void openDiscussionInPanel(String fileName) {
        if (currentGroupId == null) return;
        currentDiscussionFile = fileName;

        previewPanel.setVisible(false);
        previewPanel.setManaged(false);
        discussionPanel.setVisible(true);
        discussionPanel.setManaged(true);

        discussionTitleLabel.setText("Discussion — " + fileName);
        commentInputField.clear();

        loadDiscussionInPanel(fileName);
        startDiscussionListener();
        statusLabel.setText("Discussion loaded for: " + fileName);
    }

    private void loadDiscussionInPanel(String fileName) {
        try {
            String res = network.sendMessage("GETFILEDISCUSSION " + currentGroupId + "|" + fileName);
            discussionDescArea.clear();
            commentsBox.getChildren().clear();

            if (res.equals("EMPTY")) return;

            String[] parts = res.split(";;");
            for (String line : parts) {
                if (line.startsWith("description:")) {
                    discussionDescArea.setText(line.substring(12));
                } else if (line.startsWith("comment:")) {
                    String raw = line.substring(8); // "username|comment text"
                    addCommentBubble(raw);
                }
            }
        } catch (Exception e) {
            statusLabel.setText("Error loading discussion: " + e.getMessage());
        }
    }

    private void addCommentBubble(String raw) {
        // raw format: "username|comment text"
        String[] parts = raw.split("\\|", 2);
        String user = parts.length > 0 ? parts[0] : "?";
        String text = parts.length > 1 ? parts[1] : raw;

        VBox bubble = new VBox(2);
        bubble.setStyle("-fx-background-color: rgba(0,212,255,0.08);" +
                "-fx-background-radius: 8;" +
                "-fx-padding: 6 10 6 10;" +
                "-fx-border-color: rgba(0,212,255,0.15);" +
                "-fx-border-width: 1; -fx-border-radius: 8;");

        Label userLabel = new Label("User");
        userLabel.setStyle("-fx-text-fill: #00d4ff; -fx-font-size: 9px;" +
                "-fx-font-family: 'Courier New'; -fx-font-weight: bold;");

        Label textLabel = new Label(user + " | " + text);
        textLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.85); -fx-font-size: 11px;" +
                "-fx-font-family: 'Courier New';");
        textLabel.setWrapText(true);

        bubble.getChildren().addAll(userLabel, textLabel);
        commentsBox.getChildren().add(bubble);
    }

    @FXML
    private void handleSendComment() {
        if (currentDiscussionFile == null || currentGroupId == null) return;
        String text = commentInputField.getText().trim();
        if (text.isEmpty()) return;

        try {
            network.sendMessage("ADDCOMMENT " + currentGroupId + "|" +
                    currentDiscussionFile + "|" + text);
            commentInputField.clear();
            loadDiscussionInPanel(currentDiscussionFile);
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleSaveDescription() {
        if (currentDiscussionFile == null || currentGroupId == null) return;
        String desc = discussionDescArea.getText().trim();

        try {
            network.sendMessage("SETFILEDESCRIPTION " + currentGroupId + "|" +
                    currentDiscussionFile + "|" + desc);
            statusLabel.setText("✅ Description saved.");
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    @FXML
    private void handleCloseDiscussion() {
        stopDiscussionListener();
        currentDiscussionFile = null;
        discussionPanel.setVisible(false);
        discussionPanel.setManaged(false);
        previewPanel.setVisible(true);
        previewPanel.setManaged(true);
        showNoPreview("Select a file to preview");
        statusLabel.setText("");
    }

    // =========================================================================
    //  INIT
    // =========================================================================

    @FXML
    public void initialize() {
        seekSlider.setOnMousePressed(e -> seekDragging = true);
        seekSlider.setOnMouseReleased(e -> {
            seekDragging = false;
            if (mediaPlayer != null)
                mediaPlayer.seek(Duration.seconds(seekSlider.getValue()));
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

        initializeProfileAvatar();
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
        updateActionButtons();
        loadProfilePicture();
    }


    // =========================================================================
    //  PROFILE PICTURE
    // =========================================================================

    private void initializeProfileAvatar() {
        if (profileImageView != null) {
            Circle clip = new Circle(32, 32, 32);
            profileImageView.setClip(clip);
            profileImageView.setFitWidth(64);
            profileImageView.setFitHeight(64);
            profileImageView.setPreserveRatio(false);
            profileImageView.setSmooth(true);
            profileImageView.setCache(true);
        }
        if (profileBorderCircle != null) {
            profileBorderCircle.setStyle("-fx-fill: transparent; -fx-stroke: #00d4ff; -fx-stroke-width: 3;");
        }
    }

    private File convertImageToPng(File source) throws IOException {
        BufferedImage original = ImageIO.read(source);
        if (original == null) {
            throw new IOException("Unsupported image format.");
        }

        File temp = File.createTempFile("profile_upload_", ".png");
        temp.deleteOnExit();
        ImageIO.write(original, "png", temp);
        return temp;
    }

    private void applyProfileImage(Image image) {
        if (profileImageView == null) return;

        profileImageView.setImage(image);
        profileImageView.setViewport(null);

        if (image == null) return;

        double width = image.getWidth();
        double height = image.getHeight();
        if (width <= 0 || height <= 0) return;

        double side = Math.min(width, height);
        double x = (width - side) / 2.0;
        double y = (height - side) / 2.0;

        profileImageView.setViewport(new Rectangle2D(x, y, side, side));
    }

    private void loadProfilePicture() {
        if (network == null) return;

        Thread t = new Thread(() -> {
            try {
                File temp = File.createTempFile("profile_view_", ".png");
                temp.deleteOnExit();

                String result = network.downloadProfilePicture(temp);

                javafx.application.Platform.runLater(() -> {
                    if ("DOWNLOAD_SUCCESS".equals(result)) {
                        try {
                            applyProfileImage(new Image(new FileInputStream(temp)));
                        } catch (Exception e) {
                            applyProfileImage(null);
                        }
                    } else {
                        applyProfileImage(null);
                    }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> applyProfileImage(null));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void handleChangeProfilePicture() {
        if (network == null) {
            statusLabel.setText("Connection not ready.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Profile Picture");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.webp")
        );

        File selected = chooser.showOpenDialog(welcomeLabel.getScene().getWindow());
        if (selected == null) return;

        showProgress("Uploading profile...");
        Thread t = new Thread(() -> {
            try {
                File pngFile = convertImageToPng(selected);
                String uploadResult = network.uploadProfilePicture(
                        pngFile,
                        p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Uploading profile..."))
                );

                javafx.application.Platform.runLater(() -> {
                    hideProgress();
                    if ("UPLOAD_PROFILE_SUCCESS".equals(uploadResult)) {
                        try {
                            applyProfileImage(new Image(new FileInputStream(pngFile)));
                            statusLabel.setText("✅ Profile picture updated.");
                        } catch (Exception ex) {
                            statusLabel.setText("Profile uploaded, but preview refresh failed: " + ex.getMessage());
                        }
                    } else {
                        statusLabel.setText("Upload failed: " + uploadResult);
                    }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    hideProgress();
                    statusLabel.setText("Error: " + e.getMessage());
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // =========================================================================
    //  VIEWER SHOW / HIDE HELPERS
    // =========================================================================

    /** Restore the normal file-browser layout (hide all viewers). */
    private void showFileBrowser() {
        fileView.setVisible(true);   fileView.setManaged(true);
        searchBar.setVisible(true);  searchBar.setManaged(true);
        actionBar.setVisible(true);  actionBar.setManaged(true);
        videoView.setVisible(false); videoView.setManaged(false);
        if (imageView != null) { imageView.setVisible(false); imageView.setManaged(false); }
        if (pdfView   != null) { pdfView.setVisible(false);   pdfView.setManaged(false); }
    }

    /** Hide everything before showing a viewer. */
    private void hideFileBrowserForViewer() {
        fileView.setVisible(false);  fileView.setManaged(false);
        searchBar.setVisible(false); searchBar.setManaged(false);
        actionBar.setVisible(false); actionBar.setManaged(false);
        videoView.setVisible(false); videoView.setManaged(false);
        if (imageView != null) { imageView.setVisible(false); imageView.setManaged(false); }
        if (pdfView   != null) { pdfView.setVisible(false);   pdfView.setManaged(false); }
    }

    // =========================================================================
    //  TABS
    // =========================================================================

    @FXML


    private void handleNewTab() {
        // Save current tab's viewer state before opening new tab
        saveViewerStateToActiveTab();

        // Clear the preview panel for the new tab
        showNoPreview("Select a file to preview");

        openNewTab("");
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
            if      (col == nameCol) sortColumn = "name";
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
        // Save current viewer state before leaving
        saveViewerStateToActiveTab();

        // Stop video if playing (don't dispose — we won't restore video state)
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
            videoPlaying = false;
            playPauseBtn.setText("▶");
            seekSlider.setValue(0);
            currentTimeLabel.setText("00:00");
            totalTimeLabel.setText("00:00");
        }

        if (activeTab != null) activeTab.tabButton.setStyle(inactiveTabStyle());
        activeTab        = state;
        currentPath      = state.currentPath;
        inRecycleBin     = state.inRecycleBin;
        inSharedView     = state.inSharedView;
        inGroupView      = state.inGroupView;
        currentGroupId   = state.currentGroupId;
        currentGroupName = state.currentGroupName;
        currentGroupPath = state.currentGroupPath;
        allItems         = state.allItems;

        tableContainer.getChildren().clear();
        tableContainer.getChildren().add(state.tableView);
        state.tabButton.setStyle(activeTabStyle());
        updateNavigationStyles();
        updateActionButtons();

        if (inGroupView) {
            if (currentGroupId == null) {
                pathLabel.setText("/ Groups");
            } else {
                String pretty = (currentGroupPath == null || currentGroupPath.isEmpty())
                        ? currentGroupName
                        : currentGroupName + " / " + currentGroupPath.replace("/", " / ");
                pathLabel.setText("/ Groups / " + pretty);
            }
        } else {
            pathLabel.setText("/ " + (currentPath.isEmpty() ? "Home" : currentPath.replace("/", " / ")));
        }

        // Restore viewer state for this tab
        switch (state.viewerMode) {
            case IMAGE -> {
                if (state.viewerFile != null) {
                    showImageViewer(state.viewerFile, state.viewerTitle);
                } else {
                    showFileBrowser();
                    showNoPreview("Select a file to preview");
                }
            }
            case PDF -> {
                if (!state.pdfPages.isEmpty()) {
                    pdfPageFiles = new ArrayList<>(state.pdfPages);
                    currentPdfPage = state.pdfPage;
                    currentImageFile = null;
                    showImageViewer(pdfPageFiles.get(currentPdfPage), state.viewerTitle);
                    updatePdfPageControls(state.viewerTitle);
                } else {
                    showFileBrowser();
                    showNoPreview("Select a file to preview");
                }
            }
            case VIDEO, FILES -> {
                showFileBrowser();
                showNoPreview("Select a file to preview");
            }

        }

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

    private void updateNavigationStyles() {
        setNavActive(navFilesBtn,   !inRecycleBin && !inSharedView && !inGroupView);
        setNavActive(navRecycleBtn, inRecycleBin);
        setNavActive(navSharedBtn,  inSharedView);
        if (navGroupsBtn != null) setNavActive(navGroupsBtn, inGroupView);
    }

    private void setNavActive(Button btn, boolean active) {
        if (btn == null) return;
        if (active) { if (!btn.getStyleClass().contains("nav-btn-active")) btn.getStyleClass().add("nav-btn-active"); }
        else          btn.getStyleClass().remove("nav-btn-active");
    }

    private void updateActionButtons() {
        boolean insideGroup = inGroupView && currentGroupId != null;
        if (createGroupBtn != null) { createGroupBtn.setVisible(inGroupView);   createGroupBtn.setManaged(inGroupView); }
        if (addGroupMemberBtn != null) { addGroupMemberBtn.setVisible(insideGroup); addGroupMemberBtn.setManaged(insideGroup); }
        if (sortBtn != null) sortBtn.setDisable(inGroupView && currentGroupId == null);
    }

    private void syncActiveTabState() {
        if (activeTab == null) return;
        activeTab.currentPath      = currentPath;
        activeTab.inRecycleBin     = inRecycleBin;
        activeTab.inSharedView     = inSharedView;
        activeTab.inGroupView      = inGroupView;
        activeTab.currentGroupId   = currentGroupId;
        activeTab.currentGroupName = currentGroupName;
        activeTab.currentGroupPath = currentGroupPath;
        activeTab.allItems         = allItems;
    }

    // =========================================================================
    //  GROUPS
    // =========================================================================

    private void refreshGroupView() {
        if (currentGroupId == null) refreshGroups(); else refreshGroupFiles();
    }

    private void refreshGroups() {
//        removeCommentColumn();
        try {
            List<NetworkManager.GroupInfo> groups = network.listGroups();
            allItems = new ArrayList<>();
            for (NetworkManager.GroupInfo g : groups) {
                String title = "👥  " + g.groupName + "  (owner: " + g.owner + ")";
                allItems.add(new FileItem(title, g.groupId, true, "group", g.owner));
            }
            if (activeTab != null) activeTab.tableView.setItems(FXCollections.observableArrayList(allItems));
            pathLabel.setText("/ Groups");
            statusLabel.setText(allItems.isEmpty() ? "No groups yet." : allItems.size() + " group(s)");
            syncActiveTabState();
        } catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); }
        showNoPreview("Select a group or file.");
        loadBookmarksBar();
        updateActionButtons();
    }

    private void refreshGroupFiles() {
        try {
            NetworkManager.ListResult result = network.listGroupFiles(currentGroupId, currentGroupPath);
            allItems = new ArrayList<>();
            int idx = 0;
            for (String f : result.folders) {
                String sz = idx < result.sizes.size() ? result.sizes.get(idx) : "—";
                String dt = idx < result.dates.size() ? result.dates.get(idx) : "—";
                allItems.add(new FileItem("📁  " + f, f, true, sz, dt));
                idx++;
            }
            for (String f : result.files) {
                String sz = idx < result.sizes.size() ? result.sizes.get(idx) : "—";
                String dt = idx < result.dates.size() ? result.dates.get(idx) : "—";
                allItems.add(new FileItem(iconFor(f) + "  " + f, f, false, sz, dt));
                idx++;
            }

            if (currentGroupId != null) {
                addCommentColumnIfNeeded();
            }

            if (activeTab != null)
                activeTab.tableView.setItems(FXCollections.observableArrayList(allItems));

            String pretty = (currentGroupPath == null || currentGroupPath.isEmpty())
                    ? currentGroupName
                    : currentGroupName + " / " + currentGroupPath.replace("/", " / ");
            pathLabel.setText("/ Groups / " + pretty);
            statusLabel.setText(allItems.isEmpty() ? "This group folder is empty."
                    : result.folders.size() + " folder(s)  •  " + result.files.size() + " file(s)");
            syncActiveTabState();
        } catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); }
        loadBookmarksBar();
        applySorting();
        updateActionButtons();
    }

    private void removeCommentColumn() {
        if (activeTab == null) return;
        activeTab.tableView.getColumns().removeIf(col -> "Comment".equals(col.getText()));
    }

    private boolean commentColumnAdded = false;

    private void addCommentColumnIfNeeded() {
        if (activeTab == null) return;

        // Remove existing comment column first to prevent duplicates on refresh
        activeTab.tableView.getColumns().removeIf(col -> "Comment".equals(col.getText()));

        // Only add comment column when inside a group folder
        if (!inGroupView || currentGroupId == null) return;

        TableColumn<FileItem, Void> commentCol = new TableColumn<>("Comment");
        commentCol.setPrefWidth(90);
        commentCol.setSortable(false);
        commentCol.setStyle("-fx-background-color: transparent;");
        commentCol.getStyleClass().add("comment-column");

        commentCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            private final Button btn = new Button("💬 Com...");
            {
                btn.setStyle("-fx-background-color: rgba(0,212,255,0.08);" +
                        "-fx-text-fill: #00d4ff; -fx-font-size: 10px;" +
                        "-fx-font-family: 'Courier New'; -fx-background-radius: 5;" +
                        "-fx-padding: 4 8 4 8; -fx-cursor: hand;" +
                        "-fx-border-color: rgba(0,212,255,0.3);" +
                        "-fx-border-width: 1; -fx-border-radius: 5;");
                btn.setOnAction(e -> {
                    FileItem item = getTableView().getItems().get(getIndex());
                    if (!item.isFolder()) {
                        openDiscussionInPanel(item.getRawName());
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    FileItem fi = getTableView().getItems().get(getIndex());
                    setGraphic(fi.isFolder() ? null : btn);
                }
            }
        });

        activeTab.tableView.getColumns().add(commentCol);
    }

    // =========================================================================
    //  BOOKMARKS
    // =========================================================================

    private void loadBookmarksBar() {
        bookmarkButtonsBox.getChildren().clear();
        if (inGroupView) {
            Label hint = new Label("Bookmarks are available only in My Files.");
            hint.setStyle("-fx-text-fill: rgba(0,212,255,0.3); -fx-font-size: 10px; -fx-font-family: 'Courier New';");
            bookmarkButtonsBox.getChildren().add(hint);
            return;
        }
        List<BookmarkManager.Bookmark> allBookmarks = BookmarkManager.loadBookmarks(username);
        List<BookmarkManager.Bookmark> filtered = new ArrayList<>();
        for (BookmarkManager.Bookmark bm : allBookmarks) {
            String parentPath = bm.path.contains("/") ? bm.path.substring(0, bm.path.lastIndexOf("/")) : "";
            if (parentPath.equals(currentPath)) filtered.add(bm);
        }
        for (BookmarkManager.Bookmark bm : filtered) {
            Button btn = new Button(bm.getDisplayName());
            btn.setStyle("-fx-background-color: rgba(0,212,255,0.08);" +
                    "-fx-text-fill: rgba(255,255,255,0.8); -fx-font-size: 11px;" +
                    "-fx-font-family: 'Courier New'; -fx-cursor: hand;" +
                    "-fx-background-radius: 5; -fx-padding: 3 10 3 10;" +
                    "-fx-border-color: rgba(0,212,255,0.2); -fx-border-width: 1; -fx-border-radius: 5;");
            btn.setOnAction(e -> {
                inRecycleBin = false; inSharedView = false; inGroupView = false;
                currentGroupId = null; currentGroupName = null; currentGroupPath = "";
                currentPath = bm.isFolder ? bm.path :
                        (bm.path.contains("/") ? bm.path.substring(0, bm.path.lastIndexOf("/")) : "");
                if (activeTab != null) { activeTab.currentPath = currentPath; activeTab.inRecycleBin = false; activeTab.inSharedView = false; activeTab.inGroupView = false; }
                updateNavigationStyles(); updateActionButtons(); refreshFileList();
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
        if (inGroupView) { statusLabel.setText("Bookmarks are only for My Files."); return; }
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

    // =========================================================================
    //  SORTING
    // =========================================================================

    @FXML
    private void handleSort() {
        ContextMenu menu = new ContextMenu();
        MenuItem sortByName = new MenuItem("📝 Name");
        MenuItem sortBySize = new MenuItem("📦 Size");
        MenuItem sortByDate = new MenuItem("📅 Date");
        sortByName.setOnAction(e -> { sortColumn="name"; sortAscending=true; applySorting(); statusLabel.setText("↕ Sorted by: Name"); });
        sortBySize.setOnAction(e -> { sortColumn="size"; sortAscending=true; applySorting(); statusLabel.setText("↕ Sorted by: Size"); });
        sortByDate.setOnAction(e -> { sortColumn="date"; sortAscending=true; applySorting(); statusLabel.setText("↕ Sorted by: Date"); });
        menu.getItems().addAll(sortByName, sortBySize, sortByDate);
        menu.show(sortBtn, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private void applySorting() {
        if (activeTab == null) return;
        java.util.Comparator<FileItem> comparator = switch (sortColumn) {
            case "size" -> java.util.Comparator.comparing(FileItem::getSize);
            case "date" -> java.util.Comparator.comparing(FileItem::getDate);
            default     -> java.util.Comparator.comparing(item -> item.getRawName().toLowerCase());
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

    // =========================================================================
    //  VIDEO PLAYER
    // =========================================================================

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
                ButtonType cancel  = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                alert.getButtonTypes().setAll(openExt, cancel);
                alert.showAndWait().ifPresent(r -> {
                    if (r == openExt) { try { java.awt.Desktop.getDesktop().open(videoFile); } catch (Exception ex) { statusLabel.setText("Could not open: " + ex.getMessage()); } }
                });
                handleBackFromVideo();
            }));

        } catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); handleBackFromVideo(); }
    }

    @FXML
    private void handleBackFromVideo() {
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

    // =========================================================================
    //  IMAGE VIEWER
    // =========================================================================
    private void showImageViewer(File imageFile, String filename) {
        if (imageView == null) {
            try {
                imagePreview.setImage(new Image(new FileInputStream(imageFile)));
                imagePreview.setVisible(true);
                previewLabel.setText(filename);
            } catch (Exception e) {
                showNoPreview("Could not display image.");
            }
            return;
        }

        hideFileBrowserForViewer();
        imageView.setVisible(true);
        imageView.setManaged(true);
        imageTitleLabel.setText(filename);
        currentImageFile = imageFile;

        try {
            Image img = new Image(new FileInputStream(imageFile));
            imageViewFull.setImage(img);
            imageViewFull.setPreserveRatio(true);

            // DON'T bind to parent size — let zoom control the size
            imageViewFull.fitWidthProperty().unbind();
            imageViewFull.fitHeightProperty().unbind();
            imageViewFull.setFitWidth(700);
            imageViewFull.setFitHeight(600);

            if (zoomSlider != null) {
                zoomSlider.setValue(100);
                zoomLabel.setText("100%");
            }

            statusLabel.setText("🖼 " + filename +
                    "  (" + (int) img.getWidth() + " × " + (int) img.getHeight() + " px)");
        } catch (Exception e) {
            statusLabel.setText("Could not display image: " + e.getMessage());
            handleBackFromImage();
        }
    }
    private void applyZoom(double percent) {
        if (imageViewFull == null || (currentImageFile == null && pdfPageFiles.isEmpty())) return;

        imageViewFull.fitWidthProperty().unbind();
        imageViewFull.fitHeightProperty().unbind();
        imageViewFull.setPreserveRatio(true);

        if (percent == 100) {
            // At 100%, fit to the visible area nicely
            imageViewFull.setFitWidth(700);
            imageViewFull.setFitHeight(600);
        } else {
            double base = 700;
            double size = base * percent / 100.0;
            imageViewFull.setFitWidth(size);
            imageViewFull.setFitHeight(size);
        }
    }

    @FXML private void handleBackFromImage() {
        currentImageFile = null;
        pdfPageFiles = new ArrayList<>(); // ADD THIS
        currentPdfPage = 0;              // ADD THIS
        if (imageViewFull != null) {
            imageViewFull.fitWidthProperty().unbind();
            imageViewFull.fitHeightProperty().unbind();
            imageViewFull.setImage(null);
        }
        showFileBrowser();
        statusLabel.setText("Back to files.");
    }

    @FXML private void handleZoomIn()    { if (zoomSlider != null) zoomSlider.setValue(Math.min(400, zoomSlider.getValue() + 25)); }
    @FXML private void handleZoomOut()   { if (zoomSlider != null) zoomSlider.setValue(Math.max(10,  zoomSlider.getValue() - 25)); }
    @FXML private void handleZoomReset() { if (zoomSlider != null) zoomSlider.setValue(100); }

    @FXML private void handleOpenImageExternal() {
        if (currentImageFile != null) { try { java.awt.Desktop.getDesktop().open(currentImageFile); } catch (Exception e) { statusLabel.setText("Could not open: " + e.getMessage()); } }
    }

    // =========================================================================
    //  PDF VIEWER
    // =========================================================================

    private List<File> pdfPageFiles = new ArrayList<>();
    private int currentPdfPage = 0;

    private void showPdfViewer(File pdfFile, String filename) {
        showProgress("Rendering PDF...");
        Thread t = new Thread(() -> {
            try {
                // Convert all PDF pages to PNG images
                org.apache.pdfbox.pdmodel.PDDocument doc =
                        org.apache.pdfbox.pdmodel.PDDocument.load(pdfFile);
                org.apache.pdfbox.rendering.PDFRenderer renderer =
                        new org.apache.pdfbox.rendering.PDFRenderer(doc);

                pdfPageFiles = new ArrayList<>();
                for (int i = 0; i < doc.getNumberOfPages(); i++) {
                    java.awt.image.BufferedImage img = renderer.renderImageWithDPI(i, 150);
                    File pageFile = File.createTempFile("pdf_page_" + i + "_", ".png");
                    pageFile.deleteOnExit();
                    javax.imageio.ImageIO.write(img, "PNG", pageFile);
                    pdfPageFiles.add(pageFile);
                }
                doc.close();

                currentPdfPage = 0;
                currentPdfFile = pdfFile;

                javafx.application.Platform.runLater(() -> {
                    hideProgress();
                    if (pdfPageFiles.isEmpty()) {
                        statusLabel.setText("PDF has no pages.");
                        return;
                    }
                    // Reuse the image viewer to display pages
                    showImageViewer(pdfPageFiles.get(0), filename + " — Page 1 of " + pdfPageFiles.size());
                    imageTitleLabel.setText(filename);
                    updatePdfPageControls(filename);
                });

            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    hideProgress();
                    statusLabel.setText("PDF error: " + e.getMessage());
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void updatePdfPageControls(String filename) {
        int total = pdfPageFiles.size();
        imageTitleLabel.setText(filename + "  •  Page " + (currentPdfPage + 1) + " of " + total);
        statusLabel.setText("📕 " + filename + "  —  Page " + (currentPdfPage + 1) + "/" + total);
    }

    @FXML
    private void handlePdfPrevPage() {
        if (pdfPageFiles.isEmpty() || currentPdfPage <= 0) return;
        currentPdfPage--;
        imageViewFull.setImage(new javafx.scene.image.Image(
                pdfPageFiles.get(currentPdfPage).toURI().toString()));
        updatePdfPageControls(pdfTitleLabel != null ? pdfTitleLabel.getText() : "PDF");
    }

    @FXML
    private void handlePdfNextPage() {
        if (pdfPageFiles.isEmpty() || currentPdfPage >= pdfPageFiles.size() - 1) return;
        currentPdfPage++;
        imageViewFull.setImage(new javafx.scene.image.Image(
                pdfPageFiles.get(currentPdfPage).toURI().toString()));
        updatePdfPageControls(pdfTitleLabel != null ? pdfTitleLabel.getText() : "PDF");
    }

    @FXML private void handleBackFromPdf() {
        if (pdfWebView != null) pdfWebView.getEngine().load("about:blank");
        currentPdfFile = null;
        showFileBrowser();
        statusLabel.setText("Back to files.");
    }

    @FXML private void handleOpenPdfExternal() {
        if (currentPdfFile != null) { try { java.awt.Desktop.getDesktop().open(currentPdfFile); } catch (Exception e) { statusLabel.setText("Could not open: " + e.getMessage()); } }
    }

    // =========================================================================
    //  FILE LIST
    // =========================================================================

    /** Returns the right emoji icon for a file based on its extension */
    private String iconFor(String filename) {
        String ext = getExtension(filename);
        if (IMAGE_EXTENSIONS.contains(ext)) return "🖼";
        if (VIDEO_EXTENSIONS.contains(ext)) return "🎬";
        if (PDF_EXTENSIONS.contains(ext))   return "📕";
        return "📄";
    }

    private void refreshFileList() {
        try {
            NetworkManager.ListResult result = network.listDir(currentPath);
            allItems = new ArrayList<>();
            int i = 0;
            for (String f : result.folders) {
                String sz = i < result.sizes.size() ? result.sizes.get(i) : "—";
                String dt = i < result.dates.size() ? result.dates.get(i) : "—";
                allItems.add(new FileItem("📁  " + f, f, true, sz, dt));
                i++;
            }
            for (String f : result.files) {
                String sz = i < result.sizes.size() ? result.sizes.get(i) : "—";
                String dt = i < result.dates.size() ? result.dates.get(i) : "—";
                allItems.add(new FileItem(iconFor(f) + "  " + f, f, false, sz, dt));
                i++;
            }
            if (activeTab != null) { activeTab.currentPath = currentPath; activeTab.allItems = allItems; activeTab.tableView.setItems(FXCollections.observableArrayList(allItems)); }
            syncActiveTabState();
            pathLabel.setText("/ " + (currentPath.isEmpty() ? "Home" : currentPath.replace("/", " / ")));
            statusLabel.setText(allItems.isEmpty() ? "This folder is empty." :
                    result.folders.size() + " folder(s)  •  " + result.files.size() + " file(s)");
        } catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); }
        loadBookmarksBar();
        applySorting();
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
            syncActiveTabState();
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
            syncActiveTabState();
            statusLabel.setText(allItems.isEmpty() ? "No files shared with you yet." : allItems.size() + " file(s) shared with you.");
        } catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); }
        applySorting();
    }

    @FXML private void handleSearch() {
        if (activeTab == null) return;
        String q = searchField.getText().toLowerCase().trim();
        if (q.isEmpty()) { activeTab.tableView.setItems(FXCollections.observableArrayList(allItems)); return; }
        List<FileItem> f = new ArrayList<>();
        for (FileItem item : allItems) if (item.getRawName().toLowerCase().contains(q)) f.add(item);
        activeTab.tableView.setItems(FXCollections.observableArrayList(f));
        statusLabel.setText("Found " + f.size() + " result(s) for \"" + q + "\"");
    }

    // =========================================================================
    //  NAV
    // =========================================================================

    @FXML private void handleNavFiles() {
        stopDiscussionListener();
        removeCommentColumn();
        inRecycleBin=false; inSharedView=false; inGroupView=false;
        currentPath=""; currentGroupId=null; currentGroupName=null; currentGroupPath="";
        syncActiveTabState(); updateNavigationStyles(); updateActionButtons();
        showNoPreview("Select a file to preview"); refreshFileList();

        // Close discussion if open
        if (discussionPanel != null && discussionPanel.isVisible()) {
            handleCloseDiscussion();
        }
        commentColumnAdded = false; // reset so it gets re-added next time
    }

    @FXML private void handleNavRecycleBin() {
        stopDiscussionListener();
        removeCommentColumn();
        inRecycleBin=true; inSharedView=false; inGroupView=false;
        currentGroupId=null; currentGroupName=null; currentGroupPath="";
        syncActiveTabState(); updateNavigationStyles(); updateActionButtons();
        pathLabel.setText("/ Recycle Bin"); showNoPreview("Select a file to preview"); refreshBin();

        // Close discussion if open
        if (discussionPanel != null && discussionPanel.isVisible()) {
            handleCloseDiscussion();
        }
        commentColumnAdded = false; // reset so it gets re-added next time
    }

    @FXML private void handleNavShared() {
        stopDiscussionListener();
        removeCommentColumn();
        inSharedView=true; inRecycleBin=false; inGroupView=false;
        currentGroupId=null; currentGroupName=null; currentGroupPath="";
        syncActiveTabState(); updateNavigationStyles(); updateActionButtons();
        pathLabel.setText("/ Shared with Me"); showNoPreview("Select a file to preview"); refreshShared();

        // Close discussion if open
        if (discussionPanel != null && discussionPanel.isVisible()) {
            handleCloseDiscussion();
        }
        commentColumnAdded = false; // reset so it gets re-added next time
    }

    @FXML private void handleNavGroups() {
        stopDiscussionListener();
        inGroupView=true; inSharedView=false; inRecycleBin=false;
        currentGroupId=null; currentGroupName=null; currentGroupPath="";
        syncActiveTabState(); updateNavigationStyles(); updateActionButtons(); refreshGroups();
    }

    @FXML private void handleCreateGroup() {
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Create Group"); d.setHeaderText("Create a new group"); d.setContentText("Group name:");
        d.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) { statusLabel.setText("Group name cannot be empty."); return; }
            try {
                String r = network.createGroup(name.trim());
                if (r.equals("CREATEGROUP_SUCCESS")) { statusLabel.setText("✅ Group created: " + name.trim()); refreshGroups(); }
                else statusLabel.setText("Failed: " + r.replace("ERROR ", ""));
            } catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); }
        });
    }

    @FXML private void handleAddGroupMember() {
        if (!inGroupView || currentGroupId == null) { statusLabel.setText("Open a group first."); return; }
        TextInputDialog d = new TextInputDialog();
        d.setTitle("Add Group Member"); d.setHeaderText("Add member to " + currentGroupName); d.setContentText("Username:");
        d.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) { statusLabel.setText("Username cannot be empty."); return; }
            try {
                String r = network.addGroupMember(currentGroupId, name.trim());
                if (r.equals("ADDGROUPMEMBER_SUCCESS")) statusLabel.setText("✅ Added " + name.trim() + " to " + currentGroupName);
                else statusLabel.setText("Failed: " + r.replace("ERROR ", ""));
            } catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); }
        });
    }

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
        if (isDarkTheme) { scene.getRoot().getStyleClass().remove("root-dark"); scene.getRoot().getStyleClass().add("root-light"); themeToggleBtn.setText("🌙 Dark Mode"); isDarkTheme=false; }
        else             { scene.getRoot().getStyleClass().remove("root-light"); scene.getRoot().getStyleClass().add("root-dark"); themeToggleBtn.setText("☀ Light Mode"); isDarkTheme=true; }
    }

    // =========================================================================
    //  FILE CLICK  (My Files / Shared / Group — all three contexts)
    // =========================================================================

    @FXML private void handleFileClick() {
        if (activeTab == null) return;
        FileItem selected = activeTab.tableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        boolean dc = isDoubleClick();

        // ── GROUP VIEW ────────────────────────────────────────────────────────
        if (inGroupView) {
            if (currentGroupId == null) {
                // Group list: double-click opens a group
                if (dc && selected.isFolder()) {
                    NetworkManager.GroupInfo info = null;
                    try { for (NetworkManager.GroupInfo g : network.listGroups()) if (g.groupId.equals(selected.getRawName())) { info = g; break; } }
                    catch (Exception ignored) {}
                    currentGroupId   = selected.getRawName();
                    currentGroupName = info != null ? info.groupName : "Group";
                    currentGroupPath = "";
                    syncActiveTabState(); showNoPreview("Select a file to preview"); refreshGroupFiles();
                }
                return;
            }
            // Inside a group
            if (selected.isFolder()) {
                if (dc) {
                    currentGroupPath = currentGroupPath.isEmpty() ? selected.getRawName() : currentGroupPath + "/" + selected.getRawName();
                    syncActiveTabState(); showNoPreview("Select a file to preview"); refreshGroupFiles();
                }
            } else {
                String fn  = selected.getRawName();
                String ext = getExtension(fn);
                if (dc) {
                    if      (VIDEO_EXTENSIONS.contains(ext)) downloadAndPlayGroupVideo(fn);
                    else if (IMAGE_EXTENSIONS.contains(ext)) downloadAndOpenGroupImage(fn);
                    else if (PDF_EXTENSIONS.contains(ext))   downloadAndOpenGroupPdf(fn);
                    else                                      openGroupWithSystemApp(fn);
                } else {
                    updateSingleClickPreview(ext, fn);
                }
            }
            return;
        }

        // ── FOLDER ────────────────────────────────────────────────────────────
        if (selected.isFolder()) {
            if (dc) {
                currentPath = currentPath.isEmpty() ? selected.getRawName() : currentPath + "/" + selected.getRawName();
                if (activeTab != null) activeTab.currentPath = currentPath;
                showNoPreview("Select a file to preview"); refreshFileList();
            }
            return;
        }

        // ── SHARED VIEW ───────────────────────────────────────────────────────
        if (inSharedView) {
            String[] p  = selected.getRawName().split("\\|", 2);
            if (p.length < 2) return;
            String fp   = p[1];
            String fn   = fp.contains("/") ? fp.substring(fp.lastIndexOf("/") + 1) : fp;
            String ext  = getExtension(fn);
            if (dc) {
                if      (VIDEO_EXTENSIONS.contains(ext)) downloadAndPlaySharedVideo(selected);
                else if (IMAGE_EXTENSIONS.contains(ext)) downloadAndOpenSharedImage(selected);
                else if (PDF_EXTENSIONS.contains(ext))   downloadAndOpenSharedPdf(selected);
                else                                      openSharedWithSystemApp(selected);
            } else {
                updateSingleClickPreview(ext, fn);
            }
            return;
        }

        // ── MY FILES ──────────────────────────────────────────────────────────
        String fn  = selected.getRawName();
        String ext = getExtension(fn);
        if (dc) {
            if      (VIDEO_EXTENSIONS.contains(ext)) downloadAndPlayVideo(fn);
            else if (IMAGE_EXTENSIONS.contains(ext)) downloadAndOpenImage(fn);
            else if (PDF_EXTENSIONS.contains(ext))   downloadAndOpenPdf(fn);
            else                                      openWithSystemApp(fn, ext);
        } else {
            if (IMAGE_EXTENSIONS.contains(ext)) previewImage(fn);
            else updateSingleClickPreview(ext, fn);
        }
    }

    private void updateSingleClickPreview(String ext, String filename) {
        if      (VIDEO_EXTENSIONS.contains(ext)) showNoPreview("🎬 Double-click to play: "      + filename);
        else if (PDF_EXTENSIONS.contains(ext))   showNoPreview("📕 Double-click to open PDF: "  + filename);
        else if (IMAGE_EXTENSIONS.contains(ext)) showNoPreview("🖼 Double-click to open image: " + filename);
        else                                     showNoPreview("Double-click to open: "          + filename);
    }

    private boolean isDoubleClick() {
        long now = System.currentTimeMillis(); boolean d = (now - lastClickTime) < 400; lastClickTime = now; return d;
    }

    // =========================================================================
    //  DOWNLOAD + OPEN HELPERS  (My Files)
    // =========================================================================

    private void downloadAndPlayVideo(String filename) {
        String rel = currentPath.isEmpty() ? filename : currentPath + "/" + filename;
        File tmp = tmpFile(filename); showProgress("Loading video...");
        Thread t = new Thread(() -> { try {
            String r = network.downloadFile(rel, tmp, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Loading video...")));
            javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("DOWNLOAD_SUCCESS")) showVideoPlayer(tmp, filename); else statusLabel.setText("Failed: " + r); });
        } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); } });
        t.setDaemon(true); t.start();
    }

    private void downloadAndOpenImage(String filename) {
        String rel = currentPath.isEmpty() ? filename : currentPath + "/" + filename;
        File tmp = tmpFile(filename); showProgress("Loading image...");
        Thread t = new Thread(() -> { try {
            String r = network.downloadFile(rel, tmp, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Loading image...")));
            javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("DOWNLOAD_SUCCESS")) showImageViewer(tmp, filename); else statusLabel.setText("Failed: " + r); });
        } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); } });
        t.setDaemon(true); t.start();
    }

    private void downloadAndOpenPdf(String filename) {
        String rel = currentPath.isEmpty() ? filename : currentPath + "/" + filename;
        File tmp = tmpFile(filename); showProgress("Loading PDF...");
        Thread t = new Thread(() -> { try {
            String r = network.downloadFile(rel, tmp, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Loading PDF...")));
            javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("DOWNLOAD_SUCCESS")) showPdfViewer(tmp, filename); else statusLabel.setText("Failed: " + r); });
        } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); } });
        t.setDaemon(true); t.start();
    }

    // =========================================================================
    //  DOWNLOAD + OPEN HELPERS  (Shared)
    // =========================================================================

    private void downloadAndPlaySharedVideo(FileItem selected) {
        String[] p = selected.getRawName().split("\\|", 2); String owner = p[0], fp = p[1];
        String fn = fp.contains("/") ? fp.substring(fp.lastIndexOf("/") + 1) : fp;
        File tmp = tmpFile(fn); showProgress("Loading video...");
        Thread t = new Thread(() -> { try {
            String r = network.downloadSharedFile(owner, fp, tmp, prog -> javafx.application.Platform.runLater(() -> updateProgress(prog, "Loading video...")));
            javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("DOWNLOAD_SUCCESS")) showVideoPlayer(tmp, fn); else statusLabel.setText("Failed: " + r); });
        } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); } });
        t.setDaemon(true); t.start();
    }

    private void downloadAndOpenSharedImage(FileItem selected) {
        String[] p = selected.getRawName().split("\\|", 2); String owner = p[0], fp = p[1];
        String fn = fp.contains("/") ? fp.substring(fp.lastIndexOf("/") + 1) : fp;
        File tmp = tmpFile(fn); showProgress("Loading image...");
        Thread t = new Thread(() -> { try {
            String r = network.downloadSharedFile(owner, fp, tmp, prog -> javafx.application.Platform.runLater(() -> updateProgress(prog, "Loading image...")));
            javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("DOWNLOAD_SUCCESS")) showImageViewer(tmp, fn); else statusLabel.setText("Failed: " + r); });
        } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); } });
        t.setDaemon(true); t.start();
    }

    private void downloadAndOpenSharedPdf(FileItem selected) {
        String[] p = selected.getRawName().split("\\|", 2); String owner = p[0], fp = p[1];
        String fn = fp.contains("/") ? fp.substring(fp.lastIndexOf("/") + 1) : fp;
        File tmp = tmpFile(fn); showProgress("Loading PDF...");
        Thread t = new Thread(() -> { try {
            String r = network.downloadSharedFile(owner, fp, tmp, prog -> javafx.application.Platform.runLater(() -> updateProgress(prog, "Loading PDF...")));
            javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("DOWNLOAD_SUCCESS")) showPdfViewer(tmp, fn); else statusLabel.setText("Failed: " + r); });
        } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); } });
        t.setDaemon(true); t.start();
    }

    private void openSharedWithSystemApp(FileItem selected) {
        String[] p = selected.getRawName().split("\\|", 2); String owner = p[0], fp = p[1];
        String fn = fp.contains("/") ? fp.substring(fp.lastIndexOf("/") + 1) : fp;
        File tmp = tmpFile(fn); statusLabel.setText("Opening " + fn + "...");
        Thread t = new Thread(() -> { try {
            String r = network.downloadSharedFile(owner, fp, tmp);
            javafx.application.Platform.runLater(() -> { if (r.equals("DOWNLOAD_SUCCESS")) { try { java.awt.Desktop.getDesktop().open(tmp); statusLabel.setText("Opened: " + fn); } catch (Exception e) { statusLabel.setText("Could not open: " + e.getMessage()); } } else statusLabel.setText("Failed: " + r); });
        } catch (Exception e) { javafx.application.Platform.runLater(() -> statusLabel.setText("Error: " + e.getMessage())); } });
        t.setDaemon(true); t.start();
    }

    // =========================================================================
    //  DOWNLOAD + OPEN HELPERS  (Group)
    // =========================================================================

    private void downloadAndPlayGroupVideo(String filename) {
        String rel = currentGroupPath.isEmpty() ? filename : currentGroupPath + "/" + filename;
        File tmp = tmpFile(filename); showProgress("Loading video...");
        Thread t = new Thread(() -> { try {
            String r = network.downloadGroupFile(currentGroupId, rel, tmp, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Loading video...")));
            javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("DOWNLOAD_SUCCESS")) showVideoPlayer(tmp, filename); else statusLabel.setText("Failed: " + r); });
        } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); } });
        t.setDaemon(true); t.start();
    }

    private void downloadAndOpenGroupImage(String filename) {
        String rel = currentGroupPath.isEmpty() ? filename : currentGroupPath + "/" + filename;
        File tmp = tmpFile(filename); showProgress("Loading image...");
        Thread t = new Thread(() -> { try {
            String r = network.downloadGroupFile(currentGroupId, rel, tmp, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Loading image...")));
            javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("DOWNLOAD_SUCCESS")) showImageViewer(tmp, filename); else statusLabel.setText("Failed: " + r); });
        } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); } });
        t.setDaemon(true); t.start();
    }

    private void downloadAndOpenGroupPdf(String filename) {
        String rel = currentGroupPath.isEmpty() ? filename : currentGroupPath + "/" + filename;
        File tmp = tmpFile(filename); showProgress("Loading PDF...");
        Thread t = new Thread(() -> { try {
            String r = network.downloadGroupFile(currentGroupId, rel, tmp, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Loading PDF...")));
            javafx.application.Platform.runLater(() -> { hideProgress(); if (r.equals("DOWNLOAD_SUCCESS")) showPdfViewer(tmp, filename); else statusLabel.setText("Failed: " + r); });
        } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); } });
        t.setDaemon(true); t.start();
    }

    private void openGroupWithSystemApp(String filename) {
        String rel = currentGroupPath.isEmpty() ? filename : currentGroupPath + "/" + filename;
        File tmp = tmpFile(filename); statusLabel.setText("Opening " + filename + "...");
        Thread t = new Thread(() -> { try {
            String r = network.downloadGroupFile(currentGroupId, rel, tmp);
            javafx.application.Platform.runLater(() -> { if (r.equals("DOWNLOAD_SUCCESS")) { try { java.awt.Desktop.getDesktop().open(tmp); statusLabel.setText("Opened: " + filename); } catch (Exception e) { statusLabel.setText("Could not open: " + e.getMessage()); } } else statusLabel.setText("Failed: " + r); });
        } catch (Exception e) { javafx.application.Platform.runLater(() -> statusLabel.setText("Error: " + e.getMessage())); } });
        t.setDaemon(true); t.start();
    }

    // =========================================================================
    //  IMAGE PREVIEW  (sidebar thumbnail — single click)
    // =========================================================================

    private void previewImage(String filename) {
        try {
            String rel = currentPath.isEmpty() ? filename : currentPath + "/" + filename;
            File tmp = File.createTempFile("preview_", "." + getExtension(filename)); tmp.deleteOnExit();
            if (network.downloadFile(rel, tmp).equals("DOWNLOAD_SUCCESS")) {
                imagePreview.setImage(new Image(new FileInputStream(tmp)));
                imagePreview.setVisible(true); previewLabel.setText(filename);
            } else showNoPreview("Could not load preview.");
        } catch (Exception e) { showNoPreview("Preview error."); }
    }

    // =========================================================================
    //  OPEN WITH SYSTEM APP  (My Files)
    // =========================================================================

    private void openWithSystemApp(String filename, String ext) {
        String rel = currentPath.isEmpty() ? filename : currentPath + "/" + filename;
        File tmp = tmpFile(filename); statusLabel.setText("Opening " + filename + "...");
        Thread t = new Thread(() -> { try {
            String r = network.downloadFile(rel, tmp);
            javafx.application.Platform.runLater(() -> { if (r.equals("DOWNLOAD_SUCCESS")) { try { java.awt.Desktop.getDesktop().open(tmp); statusLabel.setText("Opened: " + filename); } catch (Exception e) { statusLabel.setText("Could not open: " + e.getMessage()); } } else statusLabel.setText("Failed: " + r); });
        } catch (Exception e) { javafx.application.Platform.runLater(() -> statusLabel.setText("Error: " + e.getMessage())); } });
        t.setDaemon(true); t.start();
    }

    // =========================================================================
    //  PROGRESS HELPERS
    // =========================================================================

    private void showProgress(String msg) {
        uploadProgressBar.setProgress(0); uploadProgressBar.setVisible(true); uploadProgressBar.setManaged(true);
        progressLabel.setVisible(true); progressLabel.setManaged(true); progressLabel.setText(msg);
    }

    private void updateProgress(double p, String prefix) {
        uploadProgressBar.setProgress(p);
        progressLabel.setText(prefix + " " + (int)(p * 100) + "%");
    }

    private void hideProgress() {
        uploadProgressBar.setVisible(false); uploadProgressBar.setManaged(false);
        progressLabel.setVisible(false); progressLabel.setManaged(false);
    }

    /** Convenience: get a temp file in the filevault tmp folder. */
    private File tmpFile(String filename) {
        File f = new File(new File(System.getProperty("java.io.tmpdir"), "filevault"), filename);
        f.getParentFile().mkdirs();
        return f;
    }

    // =========================================================================
    //  FILE OPERATIONS
    // =========================================================================

    @FXML private void handleGoUp() {
        if (inGroupView) {
            if (currentGroupId == null) { statusLabel.setText("Already at Groups root."); return; }
            if (currentGroupPath == null || currentGroupPath.isEmpty()) {
                removeCommentColumn();
                currentGroupId = null; currentGroupName = null; currentGroupPath = "";
                syncActiveTabState(); showNoPreview("Select a group or file."); refreshGroups(); return;
            }
            int i = currentGroupPath.lastIndexOf("/");
            currentGroupPath = i == -1 ? "" : currentGroupPath.substring(0, i);
            syncActiveTabState(); showNoPreview("Select a file to preview"); refreshGroupFiles(); return;
        }
        if (currentPath.isEmpty()) { statusLabel.setText("Already at root."); return; }
        int i = currentPath.lastIndexOf("/"); currentPath = i == -1 ? "" : currentPath.substring(0, i);
        if (activeTab != null) activeTab.currentPath = currentPath;
        showNoPreview("Select a file to preview"); refreshFileList();
    }

    @FXML private void handleCreateFolder() {
        if (inGroupView) { statusLabel.setText("Group folder creation is not enabled in this version."); return; }
        TextInputDialog d = new TextInputDialog(); d.setTitle("New Folder"); d.setHeaderText("Create a new folder"); d.setContentText("Folder name:");
        d.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) { statusLabel.setText("Folder name cannot be empty."); return; }
            try { String r = network.makeDir(currentPath.isEmpty() ? name : currentPath + "/" + name); if (r.equals("MKDIR_SUCCESS")) { statusLabel.setText("✅ Folder created: " + name); refreshFileList(); } else statusLabel.setText("Failed: " + r); }
            catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); }
        });
    }

    @FXML private void handleRename() {
        if (inGroupView) { statusLabel.setText("Rename is available only in My Files."); return; }
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
        if (inGroupView) { statusLabel.setText("Move is available only in My Files."); return; }
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
        if (inGroupView) { statusLabel.setText("Group files cannot be shared from here."); return; }
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
        if (inGroupView) refreshGroupView(); else if (inRecycleBin) refreshBin(); else if (inSharedView) refreshShared(); else refreshFileList();
        statusLabel.setText("✅ Refreshed.");
    }

    @FXML private void handleUpload() {
        if (inGroupView && currentGroupId == null) { statusLabel.setText("Open a group first."); return; }
        FileChooser fc = new FileChooser(); fc.setTitle("Select File to Upload");
        File f = fc.showOpenDialog((Stage) welcomeLabel.getScene().getWindow());
        if (f != null) {
            showProgress("Uploading...");
            Thread t = new Thread(() -> { try {
                String r = inGroupView
                        ? network.uploadGroupFile(f, currentGroupId, currentGroupPath, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Uploading...")))
                        : network.uploadFile(f, currentPath, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Uploading...")));
                javafx.application.Platform.runLater(() -> {
                    hideProgress();
                    if (r.equals("UPLOAD_SUCCESS") || r.equals("UPLOADGROUP_SUCCESS")) { statusLabel.setText("✅ Uploaded: " + f.getName()); if (inGroupView) refreshGroupFiles(); else refreshFileList(); }
                    else statusLabel.setText("Upload failed: " + r);
                });
            } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); } });
            t.setDaemon(true); t.start();
        }
    }

    @FXML private void handleDownload() {
        if (activeTab == null) return;
        FileItem sel = activeTab.tableView.getSelectionModel().getSelectedItem();
        if (sel == null || sel.isFolder()) { statusLabel.setText("Please select a file to download."); return; }
        String fn, ext, owner = null, fp = null, rel = null;
        if (inSharedView) { String[] p = sel.getRawName().split("\\|", 2); owner = p[0]; fp = p[1]; fn = fp.contains("/") ? fp.substring(fp.lastIndexOf("/") + 1) : fp; ext = getExtension(fn); }
        else if (inGroupView) { fn = sel.getRawName(); rel = currentGroupPath.isEmpty() ? fn : currentGroupPath + "/" + fn; ext = getExtension(fn); }
        else { fn = sel.getRawName(); rel = currentPath.isEmpty() ? fn : currentPath + "/" + fn; ext = getExtension(fn); }
        FileChooser fc = new FileChooser(); fc.setTitle("Save File As"); fc.setInitialFileName(fn);
        if (!ext.isEmpty()) fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(ext.toUpperCase() + " files", "*." + ext));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        File saveTo = fc.showSaveDialog((Stage) welcomeLabel.getScene().getWindow());
        if (saveTo == null) return;
        if (!ext.isEmpty() && !saveTo.getAbsolutePath().endsWith("." + ext)) saveTo = new File(saveTo.getAbsolutePath() + "." + ext);
        final File fSave = saveTo; final String fFn = fn, fOwner = owner, fFp = fp, fRel = rel;
        showProgress("Downloading...");
        Thread t = new Thread(() -> { try {
            String r = inSharedView
                    ? network.downloadSharedFile(fOwner, fFp, fSave, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Downloading...")))
                    : (inGroupView
                    ? network.downloadGroupFile(currentGroupId, fRel, fSave, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Downloading...")))
                    : network.downloadFile(fRel, fSave, p -> javafx.application.Platform.runLater(() -> updateProgress(p, "Downloading..."))));
            javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText(r.equals("DOWNLOAD_SUCCESS") ? "✅ Downloaded: " + fFn : "Download failed: " + r); });
        } catch (Exception e) { javafx.application.Platform.runLater(() -> { hideProgress(); statusLabel.setText("Error: " + e.getMessage()); }); } });
        t.setDaemon(true); t.start();
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
        } else if (inGroupView) {
            if (sel.isFolder()) { statusLabel.setText("Group folders cannot be deleted from here."); return; }
            String name = sel.getRawName(), path = currentGroupPath.isEmpty() ? name : currentGroupPath + "/" + name;
            Alert a = new Alert(Alert.AlertType.CONFIRMATION); a.setTitle("Delete Group File"); a.setHeaderText("Delete this file from the group?"); a.setContentText("\"" + name + "\" will be removed for all group members.");
            a.showAndWait().ifPresent(r -> { if (r == ButtonType.OK) { try {
                String res = network.deleteGroupFile(currentGroupId, path);
                if (res.equals("DELETEGROUPFILE_SUCCESS")) { statusLabel.setText("🗑 Deleted from group: " + name); showNoPreview("Select a file to preview"); refreshGroupFiles(); }
                else statusLabel.setText("Failed: " + res);
            } catch (Exception e) { statusLabel.setText("Error: " + e.getMessage()); } } });
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

    @FXML
    private void handleLogout() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
        SessionManager.clearSession();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Login.fxml"));
            Scene scene = new Scene(loader.load(), 580, 650);

            var cssUrl = getClass().getResource("login.css");
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());

            Stage loginStage = new Stage();
            loginStage.setTitle("FileVault");
            loginStage.setScene(scene);
            loginStage.show();

        } catch (Exception e) {
            System.err.println("Could not open login screen: " + e.getMessage());
        }

        // Close the dashboard
        ((Stage) welcomeLabel.getScene().getWindow()).close();
    }

    private void showNoPreview(String msg) { imagePreview.setVisible(false); imagePreview.setImage(null); previewLabel.setText(msg); }
    private String getExtension(String f)  { return f.contains(".") ? f.substring(f.lastIndexOf(".") + 1).toLowerCase() : ""; }

    // =========================================================================
    //  FileItem
    // =========================================================================

    public static class FileItem {
        private final String displayName, rawName, size, date;
        private final boolean isFolder;
        public FileItem(String dn, String rn, boolean isFolder, String sz, String dt) { displayName=dn; rawName=rn; this.isFolder=isFolder; size=sz; date=dt; }
        public String getDisplayName() { return displayName; }
        public String getRawName()     { return rawName; }
        public boolean isFolder()      { return isFolder; }
        public String getSize()        { return size; }
        public String getDate()        { return date; }
    }

    // =========================================================================
    //  TabState
    // =========================================================================

    private static class TabState {
        String currentPath;
        boolean inRecycleBin=false, inSharedView=false, inGroupView=false;
        String currentGroupId=null, currentGroupName=null, currentGroupPath="";
        List<FileItem> allItems = new ArrayList<>();
        Button tabButton;
        TableView<FileItem> tableView;
        TableColumn<FileItem,String> nameCol, sizeCol, dateCol;

        // ADD THESE — viewer state per tab
        enum ViewerMode { FILES, IMAGE, VIDEO, PDF }
        ViewerMode viewerMode = ViewerMode.FILES;
        File viewerFile = null;       // the image/pdf/video file currently open
        String viewerTitle = "";      // title shown in the viewer
        int pdfPage = 0;              // current PDF page
        List<File> pdfPages = new ArrayList<>(); // all PDF pages

        TabState(String path, Button btn, TableView<FileItem> tv,
                 TableColumn<FileItem,String> nameCol,
                 TableColumn<FileItem,String> sizeCol,
                 TableColumn<FileItem,String> dateCol) {
            this.currentPath=path; this.tabButton=btn; this.tableView=tv;
            this.nameCol=nameCol; this.sizeCol=sizeCol; this.dateCol=dateCol;
        }
    }

    private void saveViewerStateToActiveTab() {
        if (activeTab == null) return;

        if (videoView.isVisible()) {
            activeTab.viewerMode = TabState.ViewerMode.VIDEO;
            activeTab.viewerFile = null; // video is already playing, no need to restore
        } else if (imageView != null && imageView.isVisible()) {
            activeTab.viewerMode = TabState.ViewerMode.IMAGE;
            activeTab.viewerFile = currentImageFile;
            activeTab.viewerTitle = imageTitleLabel.getText();
            // check if it's actually a PDF being shown in image viewer
            if (!pdfPageFiles.isEmpty()) {
                activeTab.viewerMode = TabState.ViewerMode.PDF;
                activeTab.pdfPages = new ArrayList<>(pdfPageFiles);
                activeTab.pdfPage = currentPdfPage;
            }
        } else {
            activeTab.viewerMode = TabState.ViewerMode.FILES;
        }
    }



    private void startDiscussionListener() {
        if (discussionListenerActive || currentDiscussionFile == null || currentGroupId == null || network == null) return;

        try {
            network.startDiscussionListener(currentGroupId, currentDiscussionFile, msg ->
                    javafx.application.Platform.runLater(() -> {
                        if (currentDiscussionFile != null && discussionPanel.isVisible()) {
                            loadDiscussionInPanel(currentDiscussionFile);
                        }
                    })
            );
            discussionListenerActive = true;
        } catch (Exception e) {
            statusLabel.setText("Live discussion unavailable: " + e.getMessage());
        }
    }

    private void stopDiscussionListener() {
        discussionListenerActive = false;
        if (network != null) {
            network.stopDiscussionListener();
        }
    }

// ================= COMMENT SYSTEM =================

    private void openDiscussionPanel(String fileName) {
        if (network == null || currentGroupId == null) return;

        javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Discussion - " + fileName);

        javafx.scene.control.TextArea descriptionArea = new javafx.scene.control.TextArea();
        descriptionArea.setPromptText("Add description...");
        descriptionArea.setPrefHeight(80);

        javafx.scene.control.ListView<String> commentsList = new javafx.scene.control.ListView<>();

        javafx.scene.control.TextField commentInput = new javafx.scene.control.TextField();
        commentInput.setPromptText("Write a comment...");

        javafx.scene.control.Button sendBtn = new javafx.scene.control.Button("Send");

        sendBtn.setOnAction(e -> {
            try {
                String text = commentInput.getText().trim();
                if (text.isEmpty()) return;

                network.sendMessage("ADDCOMMENT " + currentGroupId + "|" + fileName + "|" + text);
                commentInput.clear();
                loadDiscussion(fileName, commentsList, descriptionArea);

            } catch (Exception ex) {
                statusLabel.setText("Error: " + ex.getMessage());
            }
        });

        javafx.scene.control.Button saveDescBtn = new javafx.scene.control.Button("Save Description");
        saveDescBtn.setOnAction(e -> {
            try {
                network.sendMessage("SETFILEDESCRIPTION " + currentGroupId + "|" + fileName + "|" + descriptionArea.getText());
                statusLabel.setText("Description saved.");
            } catch (Exception ex) {
                statusLabel.setText("Error: " + ex.getMessage());
            }
        });

        javafx.scene.layout.VBox layout = new javafx.scene.layout.VBox(10,
                new javafx.scene.control.Label("Description"),
                descriptionArea,
                saveDescBtn,
                new javafx.scene.control.Label("Comments"),
                commentsList,
                new javafx.scene.layout.HBox(5, commentInput, sendBtn)
        );

        layout.setPrefSize(400, 500);
        dialog.getDialogPane().setContent(layout);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);

        loadDiscussion(fileName, commentsList, descriptionArea);

        dialog.showAndWait();
    }

    private void loadDiscussion(String fileName, javafx.scene.control.ListView<String> list, javafx.scene.control.TextArea descArea) {
        try {
            String res = network.sendMessage("GETFILEDISCUSSION " + currentGroupId + "|" + fileName);

            list.getItems().clear();

            if (res.equals("EMPTY")) return;

            String[] parts = res.split(";;");

            for (String line : parts) {
                if (line.startsWith("description:")) {
                    descArea.setText(line.substring(12));
                } else if (line.startsWith("comment:")) {
                    list.getItems().add(line.substring(8));
                }
            }

        } catch (Exception e) {
            statusLabel.setText("Error loading discussion");
        }
    }

}