package com.chat.server;

import com.chat.shared.ChatMessage;
import com.chat.shared.MessageType;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerMain {
    private static final int PORT = 5555;
    private static final Logger logger = Logger.getLogger(ServerMain.class.getName());
    private static final Map<String, ClientHandler> clients = new HashMap<>();
    private static final ExecutorService pool = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down server...");
            pool.shutdownNow();
        }));

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("Chat Server is listening on port " + PORT);

            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                logger.info("New client connected from: " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket, clients);
                pool.execute(clientHandler);
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Server exception: " + ex.getMessage(), ex);
        } finally {
            gracefulShutdown();
        }
    }

    public static synchronized void broadcastMessage(ChatMessage message, String sender) {
        clients.values().stream()
                .filter(client -> !client.getUsername().equals(sender))
                .forEach(client -> client.sendMessage(message));
    }

    public static synchronized void sendPrivateMessage(ChatMessage message, String recipient) {
        ClientHandler client = clients.get(recipient);
        if (client != null) {
            client.sendMessage(message);
        } else {
            logger.warning("Recipient not found: " + recipient);
        }
    }

    public static synchronized void addClient(String username, ClientHandler clientHandler) {
        if (clients.containsKey(username)) {
            logger.warning("Duplicate username attempt: " + username);
            sendErrorMessage(clientHandler, "Username already taken");
            return;
        }
        clients.put(username, clientHandler);
        broadcastUserList();
        logger.info("User registered: " + username);
    }

    public static synchronized void removeClient(String username) {
        ClientHandler handler = clients.remove(username);
        if (handler != null) {
            logger.info("User disconnected: " + username);
            broadcastUserList();
        }
    }

    static synchronized void broadcastUserList() {
        ChatMessage userListMessage = new ChatMessage(MessageType.TEXT, "Server");
        userListMessage.setText("USERLIST:" + String.join(",", clients.keySet()));
        broadcastMessage(userListMessage, "Server");
    }

    private static void sendErrorMessage(ClientHandler handler, String message) {
        ChatMessage errorMsg = new ChatMessage(MessageType.TEXT, "Server");
        errorMsg.setText("ERROR:" + message);
        handler.sendMessage(errorMsg);
    }

    private static void gracefulShutdown() {
        try {
            ChatMessage shutdownMsg = new ChatMessage(MessageType.TEXT, "Server");
            shutdownMsg.setText("Server is shutting down");
            broadcastMessage(shutdownMsg, "Server");
            clients.values().forEach(ClientHandler::closeConnection);
            pool.shutdown();

            logger.info("Server shutdown complete");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during shutdown: " + e.getMessage(), e);
        }
    }
}