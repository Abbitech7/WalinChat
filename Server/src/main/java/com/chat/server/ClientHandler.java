package com.chat.server;

import com.chat.shared.ChatMessage;
import com.chat.shared.MessageType;
import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {
    private static final Logger logger = Logger.getLogger(ClientHandler.class.getName());
    private static final int MAX_INACTIVE_TIME = 300_000; // 5 minutes

    private final Socket clientSocket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private String username;
    private final Map<String, ClientHandler> clients;
    private volatile boolean running = true;
    private long lastActivityTime;

    public ClientHandler(Socket socket, Map<String, ClientHandler> clients) {
        this.clientSocket = socket;
        this.clients = clients;
        this.lastActivityTime = System.currentTimeMillis();

        try {
            this.output = new ObjectOutputStream(socket.getOutputStream());
            this.output.flush(); // Critical!
            this.input = new ObjectInputStream(socket.getInputStream());
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error setting up streams: " + ex.getMessage(), ex);
            closeConnection();
        }
    }

    @Override
    public void run() {
        try {
            // Handle login message
            ChatMessage loginMessage = (ChatMessage) input.readObject();
            if (loginMessage.getType() != MessageType.LOGIN || loginMessage.getSender() == null) {
                sendError("Invalid login message");
                return;
            }

            username = loginMessage.getSender();
            if (clients.containsKey(username)) {
                sendError("Username already in use");
                return;
            }

            clients.put(username, this);
            logger.info(username + " has logged in");
            sendWelcomeMessage();
            ServerMain.broadcastUserList();

            // Read and process messages from the client
            while (running) {
                checkInactivity();

                ChatMessage message = (ChatMessage) input.readObject();
                if (message == null) {
                    logger.warning("Received null message");
                    continue;
                }

                lastActivityTime = System.currentTimeMillis();
                processMessage(message);
            }
        } catch (EOFException e) {
            logger.info(username + " disconnected gracefully");
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "Protocol error with " + username, e);
        } catch (IOException e) {
            if (running) { // Only log unexpected disconnects
                logger.log(Level.SEVERE, "I/O error with " + username, e);
            }
        } finally {
            closeConnection();
        }
    }

    private void processMessage(ChatMessage message) {
        logger.info(String.format("Message from %s [%s]: %s",
                username, message.getType(), message.getText()));

        switch (message.getType()) {
            case TEXT -> handleTextMessage(message);
            case FILE, AUDIO, VIDEO, NOTE -> handleFileMessage(message);
            case LOGOUT -> running = false;
            case PING -> handlePing();
            default -> logger.warning("Unknown message type: " + message.getType());
        }
    }

    private void checkInactivity() throws IOException {
        if (System.currentTimeMillis() - lastActivityTime > MAX_INACTIVE_TIME) {
            sendError("Disconnected due to inactivity");
            throw new IOException("Client inactive");
        }
    }

    private void handlePing() {
        ChatMessage pong = new ChatMessage(MessageType.PONG, "Server");
        sendMessage(pong);
    }

    private void sendWelcomeMessage() {
        ChatMessage welcomeMsg = new ChatMessage(MessageType.TEXT, "Server");
        welcomeMsg.setText("Welcome to the chat, " + username + "!");
        sendMessage(welcomeMsg);
    }

    private void sendError(String error) {
        ChatMessage errorMsg = new ChatMessage(MessageType.ERROR, "Server");
        errorMsg.setText(error);
        sendMessage(errorMsg);
    }

    private void handleTextMessage(ChatMessage message) {
        if (message.getRecipient() != null) {
            ServerMain.sendPrivateMessage(message, message.getRecipient());
        } else {
            ServerMain.broadcastMessage(message, username);
        }
    }

    private void handleFileMessage(ChatMessage message) {
        if (message.getData() == null || message.getData().length == 0) {
            logger.warning("Empty file data from " + username);
            return;
        }

        if (message.getRecipient() != null) {
            ServerMain.sendPrivateMessage(message, message.getRecipient());
        } else {
            ServerMain.broadcastMessage(message, username);
        }
    }

    public synchronized void sendMessage(ChatMessage message) {
        if (!running || output == null) return;

        try {
            output.writeObject(message);
            output.flush();
            logger.fine("Sent message to " + username + ": " + message.getType());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Send failed to " + username, e);
            closeConnection();
        }
    }

    public String getUsername() {
        return username;
    }

    public synchronized void closeConnection() {
        running = false;
        try {
            if (username != null) {
                clients.remove(username);
                logger.info(username + " disconnected");
                ServerMain.broadcastUserList();
            }

            if (output != null) {
                output.close();
            }
            if (input != null) {
                input.close();
            }
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            logger.log(Level.FINE, "Error during cleanup", e);
        }
    }
}
