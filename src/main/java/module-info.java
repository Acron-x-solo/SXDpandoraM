module anti.messanger.sxdpandoram {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires java.desktop;
    requires javafx.swing;
    requires jdk.httpserver;
    requires javafx.media;


    opens anti.messanger.sxdpandoram to javafx.fxml;
    exports anti.messanger.sxdpandoram;
}