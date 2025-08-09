module anti.messanger.sxdpandoram {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;


    opens anti.messanger.sxdpandoram to javafx.fxml;
    exports anti.messanger.sxdpandoram;
}