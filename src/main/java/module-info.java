module anti.messanger.sxdpandoram {
    requires javafx.controls;
    requires javafx.fxml;


    opens anti.messanger.sxdpandoram to javafx.fxml;
    exports anti.messanger.sxdpandoram;
}