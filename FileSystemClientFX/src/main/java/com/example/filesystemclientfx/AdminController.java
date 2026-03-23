package com.example.filesystemclientfx;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
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

        // When user is selected in table, show their files
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

            List<DashboardController.FileItem> items = new java.util.ArrayList<>();
            int idx = 0;
            for (String folder : result.folders) {
                String size = idx < result.sizes.size() ? result.sizes.get(idx) : "—";
                String date = idx < result.dates.size() ? result.dates.get(idx) : "—";
                items.add(new DashboardController.FileItem(
                        "📁  " + folder, folder, true, size, date));
                idx++;
            }
            for (String file : result.files) {
                String size = idx < result.sizes.size() ? result.sizes.get(idx) : "—";
                String date = idx < result.dates.size() ? result.dates.get(idx) : "—";
                items.add(new DashboardController.FileItem(
                        "📄  " + file, file, false, size, date));
                idx++;
            }

            fileTable.setItems(FXCollections.observableArrayList(items));

        } catch (Exception e) {
            statusLabel.setText("Error loading files: " + e.getMessage());
        }
    }

    @FXML
    private void handleDeleteUser() {
        NetworkManager.UserInfo selected =
                userTable.getSelectionModel().getSelectedItem();

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
        alert.setContentText("Delete user \"" + selected.username +
                "\" and ALL their files?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    String result = network.deleteUser(selected.username);
                    if (result.equals("DELETEUSER_SUCCESS")) {
                        statusLabel.setText("✅ Deleted user: " + selected.username);
                        fileTable.setItems(FXCollections.observableArrayList());
                        selectedUserLabel.setText("No user selected");
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