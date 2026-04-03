module com.example.filesystemclientfx {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.desktop;
    requires javafx.media;
    requires org.apache.pdfbox;
    requires javafx.swing;
//    requires org.controlsfx.controls;
//    requires com.dlsc.formsfx;
//    requires net.synedra.validatorfx;
//    requires org.kordamp.ikonli.javafx;
//    requires org.kordamp.bootstrapfx.core;
//    requires eu.hansolo.tilesfx;
//    requires com.almasb.fxgl.all;

    opens com.example.filesystemclientfx to javafx.fxml;
    exports com.example.filesystemclientfx;
}