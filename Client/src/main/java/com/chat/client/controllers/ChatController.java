package com.chat.client.controllers;

import com.chat.client.models.User;
import com.chat.client.models.DisplayMessage;
import com.chat.shared.ChatMessage;
import com.chat.shared.MessageType;
import com.chat.client.services.ConnectionService;
import com.chat.client.services.FileTransferService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class ChatController {
    @FXML private ListView<DisplayMessage> messageListView;
    @FXML private TextField messageField;
    @FXML private Button sendButton;
    @FXML private ListView<User> userListView;
    @FXML private Button sendFileButton;
    @FXML private ProgressBar uploadProgressBar;
    @FXML private ProgressBar downloadProgressBar;
    @FXML private VBox chatContainer;
    public void initialize() {
        sendButton.setOnAction(event -> handleSendMessage());
        sendFileButton.setOnAction(event -> handleSendFile());

        uploadProgressBar.setProgress(0);
        downloadProgressBar.setProgress(0);

        messageField.setOnAction(event -> handleSendMessage());

        messageListView.setStyle("-fx-font-family: monospace;");
        userListView.setStyle("-fx-font-family: sans-serif;");
    }

    private ConnectionService connectionService;
    private final ObservableList<DisplayMessage> messages = FXCollections.observableArrayList();
    private final ObservableList<User> users = FXCollections.observableArrayList();
    private String username;
    private Stage stage;
    private static final double MB_TO_BYTES = 1024.0 * 1024.0;
    private static final String TIME_FORMAT = "HH:mm";
    public boolean initializeConnection(String serverAddress, int port, String username) {
        this.username = username;
        this.connectionService = new ConnectionService(serverAddress,
                port,
                username,
                this::handleIncomingMessage);

        if (connectionService.connect()) {
            setupUI();
            return true;
        }
        return false;
    }

    private void setupUI() {
        messageListView.setItems(messages);

        messageListView.setCellFactory(listView -> new ListCell<DisplayMessage>() {

            public void updateItem(DisplayMessage msg, boolean empty) {
                super.updateItem(msg, empty);
                if (empty || msg == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Node contentNode = msg.getContent();
                    HBox container = new HBox(contentNode);
                    container.setMaxWidth(500.0);

                    if (msg.isSelf()) {
                        container.setAlignment(Pos.BOTTOM_RIGHT);
                        if (contentNode instanceof Label label) {
                            label.setStyle("-fx-background-color: lightblue; -fx-padding: 8px; -fx-background-radius: 8px;");
                            label.setWrapText(true);
                        }
                    } else {
                        container.setAlignment(Pos.BOTTOM_LEFT);
                        if (contentNode instanceof Label label) {
                            label.setStyle("-fx-background-color: lightgray; -fx-padding: 8px; -fx-background-radius: 8px;");
                            label.setWrapText(true);
                        }
                    }

                    setGraphic(container);
                }

            }
        });
        userListView.setItems(users);

        userListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) ->
                messageField.setPromptText(newVal != null ?
                        "Private message to " + newVal.getUsername() :
                        "Type your message here...")
        );
    }


    private void handleIncomingMessage(ChatMessage message) {
        if (message == null) {
            Platform.runLater(() ->
                    showError("Protocol Error", "Received empty message"));
            return;
        }
        Platform.runLater(() -> {
            switch (message.getType()) {
                case TEXT -> handleTextMessage(message);
                case FILE, AUDIO, VIDEO, NOTE ->notifyFileReceived(message);
            }
        });
    }

    private void handleTextMessage(ChatMessage message) {
        if (message.getText().startsWith("USERLIST:")) {
            updateUserList(message.getText().substring(9));
        } else {
            displayMessage(message);
        }
    }

    private void displayMessage(ChatMessage message) {
        String timestamp = formatTimestamp(message.getTimestamp());
        String prefix = message.getRecipient() != null ?
                "\n  from " + message.getSender() + " " :
                " \n" + message.getSender() + " ";

        boolean isSelf = message.getSender().equals(username);
        Label label=new Label(message.getText()+"\n"+timestamp + " " + prefix );
        messages.add(new DisplayMessage(label, isSelf));

        scrollToLatestMessage();
    }

    private String formatTimestamp(long timestamp) {
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
        ).format(DateTimeFormatter.ofPattern(TIME_FORMAT));
    }

    private void scrollToLatestMessage() {
        messageListView.scrollTo(messages.size() - 1);
    }

    private void updateUserList(String userList) {
        users.clear();
        for (String name : userList.split(",")) {
            User user = new User(name);
            user.setOnline(true); // mark as online
            users.add(user);
        }
    }

    private void saveReceivedFile(ChatMessage message) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName(message.getFilename());
        File saveFile = fileChooser.showSaveDialog(stage);

        if (saveFile != null) {
            Path saveDir = saveFile.getParentFile().toPath();
            Task<Void> saveTask = FileTransferService.createFileSaveTask(message, saveDir);

            downloadProgressBar.progressProperty().bind(saveTask.progressProperty());
            new Thread(saveTask).start();

            notifyFileReceived(message);
        }
    }

    private void notifyFileReceived(ChatMessage message) {
        String type = message.getType().toString().toLowerCase();
        String fileName = message.getFilename();
        double size = (message.getFileSize() / MB_TO_BYTES);
        String fileSize=String.format("%.2f",size);

        boolean isSelf = false;

        Label label = new Label(fileName + " (" + type + ", " + fileSize + " MB)\n");

        Button downloadButton = new Button("Download");
        downloadButton.setOnAction(e -> saveReceivedFile(message));

        VBox messageBox = new VBox(label, downloadButton);
        messageBox.setSpacing(5);

        messages.add(new DisplayMessage(messageBox, isSelf));

        scrollToLatestMessage();
    }


    @FXML
    private void handleSendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        ChatMessage message = createTextMessage(text);
        connectionService.sendMessage(message);
        displayMessage(message);
        messageField.clear();
    }

    private ChatMessage createTextMessage(String text) {
        ChatMessage message = new ChatMessage(MessageType.TEXT, username);
        message.setText(text);

        User selectedUser = userListView.getSelectionModel().getSelectedItem();
        if (selectedUser != null) {
            message.setRecipient(selectedUser.getUsername());
        }

        return message;
    }

    @FXML
    private void handleSendFile() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            Task<ChatMessage> sendTask = FileTransferService.createFileSendTask(
                    file,
                    username,
                    getSelectedRecipient(),
                    FileTransferService.determineFileType(file.getName())
            );


            uploadProgressBar.progressProperty().bind(sendTask.progressProperty());

            sendTask.setOnSucceeded(e -> {
                connectionService.sendMessage(sendTask.getValue());
                uploadProgressBar.progressProperty().unbind();
                notifyFileSent(file, sendTask.getValue().getType());
            });

            sendTask.setOnFailed(e -> {
                uploadProgressBar.progressProperty().unbind();
                showError("Upload Failed", sendTask.getException().getMessage());
            });

            new Thread(sendTask).start();
        }
    }

    private String getSelectedRecipient() {
        User selectedUser = userListView.getSelectionModel().getSelectedItem();
        return selectedUser != null ? selectedUser.getUsername() : null;
    }

    private void notifyFileSent(File file, MessageType type) {
        Label displayMsg = new Label(String.format("Sent %s file: %s (%.2f MB)",
                type.toString().toLowerCase(),
                file.getName(),
                file.length() / MB_TO_BYTES));

        boolean isSelf = true;
        messages.add(new DisplayMessage(displayMsg, isSelf));
        scrollToLatestMessage();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText("");
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void setStage(Stage stage) {
        stage.centerOnScreen();
        this.stage = stage;
        stage.setOnCloseRequest(event -> disconnectCleanly());
    }

    private void disconnectCleanly() {
        if (connectionService != null && connectionService.isConnected()) {
            connectionService.sendMessage(new ChatMessage(MessageType.LOGOUT, username));
            connectionService.disconnect();
        }
    }
}