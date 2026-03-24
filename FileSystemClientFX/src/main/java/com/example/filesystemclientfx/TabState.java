package com.example.filesystemclientfx;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.beans.property.SimpleStringProperty;
import java.util.ArrayList;
import java.util.List;

public class TabState {

    public String currentPath = "";
    public boolean inRecycleBin = false;
    public boolean inSharedView = false;
    public List<DashboardController.FileItem> allItems = new ArrayList<>();
    public Tab tab;
    public TableView<DashboardController.FileItem> tableView;
    public Label pathLabel;
    public Label statusLabel;

    public TabState(String startPath, Tab tab,
                    TableView<DashboardController.FileItem> tableView,
                    Label pathLabel, Label statusLabel) {
        this.currentPath = startPath;
        this.tab = tab;
        this.tableView = tableView;
        this.pathLabel = pathLabel;
        this.statusLabel = statusLabel;
    }
}