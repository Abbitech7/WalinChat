package com.chat.client.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private TextField serverAddressField;
    @FXML private Button loginButton;
    @FXML private Label errorLabel;

    private static final int PORT = 5555;

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String serverAddress = serverAddressField.getText().trim();

        if (username.isEmpty()) {
            errorLabel.setText("Username cannot be empty");
            return;
        }

        if (serverAddress.isEmpty()) {
            serverAddress = "localhost";
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chat/client/views/chat.fxml"));
            Parent root = loader.load();

            ChatController chatController = loader.getController();
            boolean connected = chatController.initializeConnection(serverAddress, PORT, username);

            if (connected) {
                Stage stage = (Stage) loginButton.getScene().getWindow();

                Scene chatScene = new Scene(root, 800, 600);
                chatScene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

                stage.setScene(chatScene);
                stage.setTitle("Waliin Chat - " + username);
                stage.setResizable(true);
                stage.centerOnScreen();

                chatController.setStage(stage);
            } else {
                errorLabel.setText("Failed to connect to server");
            }
        } catch (IOException e) {
            errorLabel.setText("Error loading chat window");
            e.printStackTrace();
        }
    }
}
