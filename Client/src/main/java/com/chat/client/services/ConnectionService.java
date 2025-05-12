package com.chat.client.services;

import com.chat.shared.ChatMessage;
import com.chat.shared.MessageType;
import javafx.application.Platform;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ConnectionService {
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int PING_INTERVAL = 15000; // 15 seconds

    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private final String serverAddress;
    private final int port;
    private final String username;
    private final Consumer<ChatMessage> messageConsumer;
    private volatile boolean isConnected = false;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private Thread pingThread;

    public ConnectionService(String serverAddress, int port, String username,
                             Consumer<ChatMessage> messageConsumer) {
        this.serverAddress = serverAddress;
        this.port = port;
        this.username = username;
        this.messageConsumer = messageConsumer;
    }

    public boolean connect() {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(serverAddress, port), CONNECTION_TIMEOUT);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);

            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();

            input = new ObjectInputStream(socket.getInputStream());

            isConnected = true;

            System.out.println("Connected to server at " + serverAddress + ":" + port);
            sendMessage(new ChatMessage(MessageType.LOGIN, username));

            executor.execute(this::listenForMessages);
            startPingService();

            return true;
        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    private void listenForMessages() {
        try {
            while (isConnected) {
                try {
                    Object obj = input.readObject();
                    if (obj instanceof ChatMessage) {
                        ChatMessage message = (ChatMessage) obj;
                        System.out.println("Received: " + message.getType() + " - " + message.getText());
                        Platform.runLater(() -> messageConsumer.accept(message));
                    }
                } catch (ClassNotFoundException e) {
                    System.err.println("Protocol mismatch: " + e.getMessage());
                    disconnect();
                } catch (EOFException e) {
                    System.out.println("Server closed connection gracefully");
                    disconnect();
                    break;
                }
            }
        } catch (IOException e) {
            if (isConnected) {
                String errorMessage = "Connection error: " + e.getMessage();
                System.err.println(errorMessage);

                // Create error message for UI
                ChatMessage errorMsg = new ChatMessage(MessageType.ERROR, "System");
                errorMsg.setText("Connection lost: " + e.getMessage());
                Platform.runLater(() -> {
                    try {
                        messageConsumer.accept(errorMsg);
                    } catch (Exception ex) {
                        System.err.println("Error delivering error message: " + ex.getMessage());
                    }
                });

                disconnect();
            }
        }
    }

    public synchronized void sendMessage(ChatMessage message) {
        if (!isConnected) {
            System.err.println("Cannot send message - not connected");
            return;
        }

        executor.execute(() -> {
            try {
                System.out.println("Sending: " + message.getType() + " - " + message.getText());
                output.writeObject(message);
                output.flush();
            } catch (IOException e) {
                System.err.println("Send failed: " + e.getMessage());
                disconnect();
            }
        });
    }

    private void startPingService() {
        pingThread = new Thread(() -> {
            while (isConnected) {
                try {
                    Thread.sleep(PING_INTERVAL);
                    sendMessage(new ChatMessage(MessageType.PING, username));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("Ping failed: " + e.getMessage());
                    disconnect();
                    break;
                }
            }
        });
        pingThread.setDaemon(true);
        pingThread.start();
    }

    public synchronized void disconnect() {
        if (!isConnected) return;

        isConnected = false;
        try {
            if (output != null) {
                sendMessage(new ChatMessage(MessageType.LOGOUT, username));
            }
        } finally {
            closeResources();
            executor.shutdown();
        }
    }

    private void closeResources() {
        try {
            if (input != null) input.close();
        } catch (IOException e) {
            System.err.println("Error closing input: " + e.getMessage());
        }

        try {
            if (output != null) output.close();
        } catch (IOException e) {
            System.err.println("Error closing output: " + e.getMessage());
        }

        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return isConnected;
    }
}