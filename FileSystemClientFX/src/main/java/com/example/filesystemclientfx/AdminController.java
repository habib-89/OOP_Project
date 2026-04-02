package com.example.filesystemclientfx;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;

public class AdminController {

    @FXML private TableView<NetworkManager.UserInfo> userTable;
    @FXML private TableColumn<NetworkManager.UserInfo, String> usernameColumn;
    @FXML private TableColumn<NetworkManager.UserInfo, String> storageColumn;
    @FXML private TableView<DashboardController.FileItem> fileTable;
    @FXML private TableColumn<DashboardController.FileItem, String> fileNameColumn;
    @FXML private TableColumn<DashboardController.FileItem, String> fileSizeColumn;
    @FXML private TableColumn<DashboardController.FileItem, String> fileDateColumn;
    @FXML private Label statusLabel;
    @FXML private Label selectedUserLabel;

    private NetworkManager network;
    private String selectedUser = null;

    @FXML
    public void initialize() {
        usernameColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().username));
        storageColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().storageUsed));

        fileNameColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().getDisplayName()));
        fileSizeColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().getSize()));
        fileDateColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().getDate()));

        userTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        selectedUser = newVal.username;
                        selectedUserLabel.setText("Files of: " + selectedUser);
                        loadUserFiles(selectedUser);
                    }
                });
    }

    public void setNetwork(NetworkManager network) {
        this.network = network;
        loadUsers();
    }

    private void loadUsers() {
        try {
            List<NetworkManager.UserInfo> users = network.listUsers();
            userTable.setItems(FXCollections.observableArrayList(users));
            statusLabel.setText(users.size() + " user(s) registered.");
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadUserFiles(String username) {
        try {
            NetworkManager.ListResult result = network.viewUserFiles(username);

            // FIX: The server returns items in order: all entries are in the sizes/dates lists
            // in the same order as folders first, then files — matching parseListResult output.
            // We need to map sizes and dates correctly per-item, not with a shared index.
            List<DashboardController.FileItem> items = new ArrayList<>();

            // parseListResult fills folders and files separately but sizes/dates
            // are in the combined original order: folders come first in the server response.
            int folderCount = result.folders.size();
            int fileCount = result.files.size();

            for (int i = 0; i < folderCount; i++) {
                String size = i < result.sizes.size() ? result.sizes.get(i) : "—";
                String date = i < result.dates.size() ? result.dates.get(i) : "—";
                String name = result.folders.get(i);
                items.add(new DashboardController.FileItem("📁  " + name, name, true, size, date));
            }

            for (int i = 0; i < fileCount; i++) {
                int idx = folderCount + i;
                String size = idx < result.sizes.size() ? result.sizes.get(idx) : "—";
                String date = idx < result.dates.size() ? result.dates.get(idx) : "—";
                String name = result.files.get(i);
                items.add(new DashboardController.FileItem("📄  " + name, name, false, size, date));
            }

            fileTable.setItems(FXCollections.observableArrayList(items));

        } catch (Exception e) {
            statusLabel.setText("Error loading files: " + e.getMessage());
        }
    }

    @FXML
    private void handleDeleteUser() {
        NetworkManager.UserInfo selected = userTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            statusLabel.setText("Please select a user to delete.");
            return;
        }

        if (selected.username.equals("admin")) {
            statusLabel.setText("Cannot delete admin!");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete User");
        alert.setHeaderText("Are you sure?");
        alert.setContentText("Delete user \"" + selected.username + "\" and ALL their files?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    String result = network.deleteUser(selected.username);
                    if (result.equals("DELETEUSER_SUCCESS")) {
                        statusLabel.setText("✅ Deleted user: " + selected.username);
                        fileTable.setItems(FXCollections.observableArrayList());
                        selectedUserLabel.setText("No user selected");
                        selectedUser = null;
                        loadUsers();
                    } else {
                        statusLabel.setText("Failed: " + result);
                    }
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleRefresh() {
        loadUsers();
        if (selectedUser != null) loadUserFiles(selectedUser);
        statusLabel.setText("✅ Refreshed.");
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) statusLabel.getScene().getWindow();
        stage.close();
    }
}