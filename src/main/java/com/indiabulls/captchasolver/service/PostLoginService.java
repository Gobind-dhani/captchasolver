package com.indiabulls.captchasolver.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

@Service
public class PostLoginService {

    @Value("${ftp.host}")
    private String ftpHost;

    @Value("${ftp.port}")
    private int ftpPort;

    @Value("${ftp.username}")
    private String ftpUsername;

    @Value("${ftp.password}")
    private String ftpPassword;

    @Value("${ftp.base-dir}")
    private String ftpBaseDir;

    /**
     * Map containing API URL and payload for each segment.
     */
    private final Map<String, String> segmentUrls = Map.of(
            "CM", "https://www.connect2nsccl.com/collateral-management/cm-coll-client-dtls",
            "FO", "https://www.connect2nsccl.com/collateral-management/fo-coll-client-dtls",
            "CD", "https://www.connect2nsccl.com/collateral-management/cd-coll-client-dtls"
    );

    private final Map<String, String> segmentPayloads = Map.of(
            "CM", """
                    {
                      "version": "2.0",
                      "data": {
                        "memType": "CM",
                        "memCode": "08756",
                        "tmCode": "ALL",
                        "cliCode": "",
                        "dataFormat": "JSON:CSV"
                      }
                    }
                  """,
            "FO", """
                    {
                      "version": "2.0",
                      "data": {
                        "memType": "CM",
                        "memCode": "M50834",
                        "tmCode": "ALL",
                        "cliCode": "",
                        "dataFormat": "JSON:CSV"
                      }
                    }
                  """,
            "CD", """
                    {
                      "version": "2.0",
                      "data": {
                        "memType": "CM",
                        "memCode": "M50834",
                        "tmCode": "ALL",
                        "cliCode": "",
                        "dataFormat": "JSON:CSV"
                      }
                    }
                  """
    );

    public void fetchAllSegmentCsvs(WebDriver driver) {
        String[] segments = {"CM", "FO", "CD"};
        for (String segment : segments) {
            try {
                pause();
                System.out.println("📥 Fetching CSV for segment: " + segment);

                // 1. Fetch Base64 CSV from API
                String base64Csv = fetchCollateralCsvBase64(driver, segment);

                // 2. Decode and upload directly to FTP
                byte[] csvBytes = Base64.getDecoder().decode(base64Csv);
                String fileName = segment + "_ClientLevelCollaterals_" +
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyyyyHHmmss")) + ".csv";

                uploadBytesToFTP(csvBytes, fileName);

            } catch (Exception e) {
                System.err.println("❌ Failed for segment " + segment + ": " + e.getMessage());
                e.printStackTrace();
            }


        }
        try {
            pause(); // Give the UI a moment after last file
            System.out.println("🚪 Logging out...");

            // Click the user name text to open the dropdown
            driver.findElement(By.xpath("//div[contains(@class,'user-name-text') and text()='DHANI STOCKS LIMITED']")).click();
            pause();

            // Click the logout link
            driver.findElement(By.xpath("//a[contains(@class,'dropdown-item') and contains(.,'Logout')]")).click();

            System.out.println("✅ Successfully logged out");
        } catch (Exception e) {
            System.err.println("⚠️ Logout failed: " + e.getMessage());
        }
    }

    private String fetchCollateralCsvBase64(WebDriver driver, String segment) throws IOException {
        String apiUrl = segmentUrls.get(segment);
        String payload = segmentPayloads.get(segment);

        if (apiUrl == null || payload == null) {
            throw new IllegalArgumentException("No API details found for segment: " + segment);
        }

        // Extract cookies from Selenium
        Set<Cookie> seleniumCookies = driver.manage().getCookies();
        StringBuilder cookieHeader = new StringBuilder();
        for (Cookie cookie : seleniumCookies) {
            cookieHeader.append(cookie.getName()).append("=")
                    .append(cookie.getValue()).append("; ");
        }

        // Make POST request with cookies
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Cookie", cookieHeader.toString());

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        if (conn.getResponseCode() != 200) {
            throw new IOException("HTTP error: " + conn.getResponseCode());
        }

        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        JsonObject result = json.getAsJsonObject("data").getAsJsonObject("result");
        if (result == null || !result.has("base64str")) {
            throw new IOException("No base64str found in API response");
        }
        return result.get("base64str").getAsString();
    }

    private void uploadBytesToFTP(byte[] fileBytes, String fileName) {
        FTPClient ftpClient = new FTPClient();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes)) {
            String currentDateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMMM-yyyy"));
            String remoteDir = ftpBaseDir + "/" + currentDateFolder + "/collateral shortage/current";

            ftpClient.connect(ftpHost, ftpPort);
            ftpClient.login(ftpUsername, ftpPassword);
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            // Ensure remote directory exists
            for (String folder : remoteDir.split("/")) {
                if (!folder.isEmpty()) {
                    if (!ftpClient.changeWorkingDirectory(folder)) {
                        ftpClient.makeDirectory(folder);
                        ftpClient.changeWorkingDirectory(folder);
                    }
                }
            }

            boolean uploaded = ftpClient.storeFile(fileName, bais);
            if (!uploaded) throw new IOException("FTP upload failed: " + fileName);

            System.out.println("📤 Uploaded to FTP: " + remoteDir + "/" + fileName);
        } catch (Exception e) {
            throw new RuntimeException("FTP upload failed: " + e.getMessage(), e);
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void pause() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread sleep interrupted", e);
        }
    }
}
