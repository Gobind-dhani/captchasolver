package com.indiabulls.captchasolver.controller;

import java.net.Socket;

public class CheckImapConnection {
    public static void main(String[] args) {
        String host = "imap.gmail.com";
        int port = 993;
        int timeoutMillis = 5000; // 5 seconds

        System.out.println("Testing connection to " + host + ":" + port + "...");

        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMillis);
            System.out.println("✅ SUCCESS: Able to connect to " + host + " on port " + port);
        } catch (Exception e) {
            System.err.println("❌ FAILED: Cannot connect to " + host + " on port " + port);
            System.err.println("Reason: " + e.getMessage());
        }
    }
}
