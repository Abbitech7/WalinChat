package com.chat.client;

import com.chat.client.controllers.LoginController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chat/client/views/login.fxml"));
        Parent root = loader.load();
        LoginController loginController = loader.getController();

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setTitle("WaliinChat - Login");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }
}