module Client {
    requires javafx.controls;
    requires javafx.fxml;
    requires Shared;

    opens com.chat.client to javafx.fxml;
    opens com.chat.client.controllers to javafx.fxml;
    opens com.chat.client.views to javafx.fxml;
    exports com.chat.client;
    exports com.chat.client.models;
}