package com.indiabulls.captchasolver.controller;


import java.net.Socket;
import org.apache.commons.net.ftp.FTPClient;

public class CheckFtpConnection {
    public static void main(String[] args) {
        String host = "172.17.60.142";
        int port = 21;
        String username = "ib_automation_alpha";
        String password = "ib@alpha";
        int timeoutMillis = 5000; // 5 seconds

        System.out.println("=== Step 1: Testing raw TCP connection to " + host + ":" + port + " ===");

        // Step 1: TCP connectivity check
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMillis);
            System.out.println("✅ SUCCESS: Able to connect to " + host + " on port " + port);
        } catch (Exception e) {
            System.err.println("❌ FAILED: Cannot connect to " + host + " on port " + port);
            System.err.println("Reason: " + e.getMessage());
            return; // No point in continuing if TCP itself fails
        }

        System.out.println("\n=== Step 2: Testing FTP login ===");

        // Step 2: FTP login check
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.setConnectTimeout(timeoutMillis);
            ftpClient.connect(host, port);
            int replyCode = ftpClient.getReplyCode();
            if (!ftpClient.login(username, password)) {
                System.err.println("❌ FAILED: FTP login unsuccessful. Reply code: " + replyCode);
                ftpClient.disconnect();
                return;
            }
            System.out.println("✅ SUCCESS: Logged in to FTP server");

            // Optional: check if base directory exists
            String baseDir = "/indiabulls/ib-automation/backoffice-input";
            if (ftpClient.changeWorkingDirectory(baseDir)) {
                System.out.println("✅ SUCCESS: Base directory exists: " + baseDir);
            } else {
                System.err.println("⚠️ WARNING: Base directory not found: " + baseDir);
            }

            ftpClient.logout();
        } catch (Exception e) {
            System.err.println("❌ FAILED: FTP connection/login error");
            e.printStackTrace();
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.disconnect();
                }
            } catch (Exception ignored) {}
        }
    }
}
