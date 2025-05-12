package com.chat.shared;

import java.io.*;
import java.util.Date;

public class ChatMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final MessageType type;
    private final String sender;
    private String recipient;

    private String text;
    private transient byte[] data;
    private String filename;
    private long fileSize;
    private final long timestamp;

    public ChatMessage(MessageType type, String sender) {
        this.type = type;
        this.sender = sender;
        this.timestamp = new Date().getTime();
    }
    public MessageType getType() { return type; }
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public String getText() { return text; }
    public byte[] getData() { return data; }
    public String getFilename() { return filename; }
    public long getFileSize() { return fileSize; }
    public long getTimestamp() { return timestamp; }

    public void setRecipient(String recipient) { this.recipient = recipient; }
    public void setText(String text) { this.text = text; }
    public void setData(byte[] data) { this.data = data; }
    public void setFilename(String filename) { this.filename = filename; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        if (data != null) {
            out.writeInt(data.length);
            if (data.length > 0) {
                out.write(data);
            }
        } else {
            out.writeInt(0);
        }
    }

    @Serial
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        int dataLength = in.readInt();
        if (dataLength > 0) {
            this.data = new byte[dataLength];
            in.readFully(this.data);
        } else {
            this.data = null;
        }
    }


    public byte[] getSafeData() {
        return data != null ? data.clone() : null;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s -> %s: %s (size: %d)",
                type,
                sender,
                recipient != null ? recipient : "ALL",
                text != null ? text : filename,
                fileSize);
    }
}