package com.chat.client.services;

import javafx.concurrent.Task;
import com.chat.shared.MessageType;
import com.chat.shared.ChatMessage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileTransferService {
    private static final int CHUNK_SIZE = 8192;

    public static ChatMessage prepareFileMessage(File file, String sender, String recipient, MessageType type) throws IOException {
        ChatMessage message = new ChatMessage(type, sender);
        message.setRecipient(recipient);
        message.setFilename(file.getName());
        message.setFileSize(file.length());
        return message;
    }

    public static Task<ChatMessage> createFileSendTask(File file, String sender, String recipient, MessageType type) {
        return new Task<>() {
            @Override
            protected ChatMessage call() throws Exception {
                ChatMessage message = prepareFileMessage(file, sender, recipient, type);
                long fileSize = file.length();
                byte[] buffer = new byte[CHUNK_SIZE];

                try (FileInputStream fis = new FileInputStream(file)) {
                    int bytesRead;
                    long totalRead = 0;

                    while ((bytesRead = fis.read(buffer)) != -1 && !isCancelled()) {
                        // For real implementation, we'd send chunks here
                        // For demo, we'll just build the complete data
                        byte[] chunk = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunk, 0, bytesRead);

                        totalRead += bytesRead;
                        updateProgress(totalRead, fileSize);
                        updateMessage(String.format("Sending %.1f/%.1f MB",
                                totalRead / (1024.0 * 1024.0),
                                fileSize / (1024.0 * 1024.0)));
                    }

                    // In real implementation, we gonna remove this and send chunks incrementally
                    if (!isCancelled()) {
                        byte[] fileData = Files.readAllBytes(file.toPath());
                        message.setData(fileData);
                    }
                }

                return message;
            }
        };
    }

    public static Task<Void> createFileSaveTask(ChatMessage message, Path saveDirectory) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateProgress(0, message.getFileSize());

                Path filePath = saveDirectory.resolve(message.getFilename());
                Files.write(filePath, message.getData(), StandardOpenOption.CREATE);

                updateProgress(message.getFileSize(), message.getFileSize());
                return null;
            }
        };
    }

    public static MessageType determineFileType(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "mp3", "wav", "ogg" -> MessageType.AUDIO;
            case "mp4", "avi", "mov" -> MessageType.VIDEO;
            case "txt", "md" -> MessageType.NOTE;
            default -> MessageType.FILE;
        };
    }
}