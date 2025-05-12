package com.chat.client.models;

public class User {
    private final String username;
    private boolean isOnline;

    public User(String username) {
        this.username = username;
        this.isOnline = true;
    }

    public String getUsername() {
        return username;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    @Override
    public String toString() {
        return username + (isOnline ? " (online)" : " (offline)");
    }
}