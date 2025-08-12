package com.indiabulls.captchasolver.service;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

    @Value("${csv.download.dir}")
    private String downloadDir; // Path where Selenium downloads the CSV locally first

    public void navigateToCollateralManagement(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        ((JavascriptExecutor) driver).executeScript(
                "window.open('https://www.connect2nsccl.com/collateral-management/#/allocation/file-upload', '_blank');"
        );

        List<String> tabs = new ArrayList<>(driver.getWindowHandles());
        driver.switchTo().window(tabs.get(tabs.size() - 1));

        WebDriverWait fastWait = new WebDriverWait(driver, Duration.ofSeconds(12));
        fastWait.until(webDriver -> ((JavascriptExecutor) webDriver)
                .executeScript("return document.readyState").equals("complete"));

        System.out.println("Collateral Management file upload page opened in new tab.");

        // Step 1: Click ALLOCATION dropdown
        WebElement allocationDropdown = wait.until(ExpectedConditions
                .elementToBeClickable(By.id("navbarDropdown")));
        allocationDropdown.click();
        System.out.println("Clicked ALLOCATION dropdown.");

        // Step 2: Select COLLATERAL ALLOCATION INFORMATION
        WebElement collateralInfoLink = wait.until(ExpectedConditions
                .elementToBeClickable(By.xpath("//a[contains(text(), 'COLLATERAL ALLOCATION INFORMATION')]")));
        collateralInfoLink.click();
        System.out.println("Clicked COLLATERAL ALLOCATION INFORMATION link.");

        //  First download for default tab
        downloadCsvForCurrentTab(driver, wait, "CM");

        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Click FO tab
        js.executeScript(
                "var fo = Array.from(document.querySelectorAll('#navbarNavDropdown ul li')).find(el => el.textContent.trim() === 'FO');" +
                        "if (fo) { fo.scrollIntoView({block:'center', behavior:'smooth'}); fo.click(); } else { throw 'FO tab not found'; }"
        );
        System.out.println("Clicked FO tab.");
        downloadCsvForCurrentTab(driver, wait, "FO");

        // Click CD tab
        js.executeScript(
                "var cd = Array.from(document.querySelectorAll('#navbarNavDropdown ul li')).find(el => el.textContent.trim() === 'CD');" +
                        "if (cd) { cd.scrollIntoView({block:'center', behavior:'smooth'}); cd.click(); } else { throw 'CD tab not found'; }"
        );
        System.out.println("Clicked CD tab.");
        downloadCsvForCurrentTab(driver, wait, "CD");
    }

    private void downloadCsvForCurrentTab(WebDriver driver, WebDriverWait wait, String segmentName) {
        try {
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState").equals("complete"));
            System.out.println("Collateral Allocation Information page loaded for segment: " + segmentName);

            WebElement clientDetailsTitle = wait.until(ExpectedConditions
                    .presenceOfElementLocated(By.xpath("//span[contains(@class,'page-title') and contains(normalize-space(.),'Client Level Details @')]")));

            WebElement targetTmCodeDropdown = clientDetailsTitle.findElement(
                    By.xpath(".//following::select[@id='tmcode'][1]")
            );

            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", targetTmCodeDropdown);
            wait.until(ExpectedConditions.elementToBeClickable(targetTmCodeDropdown));

            boolean selected = false;
            try {
                new Select(targetTmCodeDropdown).selectByValue("ALL");
                selected = true;
                System.out.println("Selected ALL using Selenium Select.");
            } catch (Exception ex) {
                System.out.println("Select failed, using JS: " + ex.getMessage());
            }
            if (!selected) {
                String js =
                        "arguments[0].value = arguments[1];" +
                                "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
                                "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));";
                ((JavascriptExecutor) driver).executeScript(js, targetTmCodeDropdown, "ALL");
                System.out.println("Selected ALL via JS events.");
            }

            WebElement showButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(normalize-space(.),'Show')]")
            ));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", showButton);
            showButton.click();
            System.out.println("Clicked Show button.");

            // Click CSV icon
            int maxWaitSeconds = 60;
            JavascriptExecutor js = (JavascriptExecutor) driver;
            boolean clicked = false;
            for (int i = 0; i < maxWaitSeconds; i++) {
                try {
                    Boolean exists = (Boolean) js.executeScript("return document.querySelector('img[alt=\"CSV\"]') !== null;");
                    if (exists) {
                        js.executeScript("document.querySelector('img[alt=\"CSV\"]').scrollIntoView({block:'center'});");
                        js.executeScript("document.querySelector('img[alt=\"CSV\"]').click();");
                        System.out.println("Clicked CSV download link via JS.");
                        clicked = true;
                        break;
                    }
                } catch (Exception ignored) {}
                Thread.sleep(1000);
            }
            if (!clicked) throw new TimeoutException("CSV download link not found.");

            // Wait for file to be downloaded locally
            String downloadedFilePath = waitForDownloadedFile(segmentName);
            System.out.println("Downloaded file locally: " + downloadedFilePath);

            // Upload to FTP
            uploadFileToFTP(downloadedFilePath, segmentName);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String waitForDownloadedFile(String segmentName) throws InterruptedException {
        Path downloadPath = Paths.get(downloadDir);
        String expectedPrefix = segmentName; // You can refine matching if needed

        for (int i = 0; i < 60; i++) { // Wait up to 60 sec
            try {
                File[] files = downloadPath.toFile().listFiles((dir, name) -> name.startsWith(expectedPrefix) && name.endsWith(".csv"));
                if (files != null && files.length > 0) {
                    return files[0].getAbsolutePath();
                }
            } catch (Exception ignored) {}
            Thread.sleep(1000);
        }
        throw new RuntimeException("CSV file not found in download dir after waiting: " + segmentName);
    }

    private void uploadFileToFTP(String localFilePath, String segmentName) {
        FTPClient ftpClient = new FTPClient();
        try (FileInputStream fis = new FileInputStream(localFilePath)) {
            String currentDateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"));
            String remoteDir = ftpBaseDir + "/" + currentDateFolder + "/" + segmentName;

            ftpClient.connect(ftpHost, ftpPort);
            ftpClient.login(ftpUsername, ftpPassword);
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            String fileName = Paths.get(localFilePath).getFileName().toString();

            // Check if file exists
            FTPFile[] existingFiles = ftpClient.listFiles(remoteDir + "/" + fileName);
            if (existingFiles != null && existingFiles.length > 0) {
                System.out.println("File already exists on FTP. Skipping upload: " + fileName);
                return;
            }

            // Ensure remote directory exists
            for (String folder : remoteDir.split("/")) {
                if (!folder.isEmpty()) {
                    if (!ftpClient.changeWorkingDirectory(folder)) {
                        ftpClient.makeDirectory(folder);
                        ftpClient.changeWorkingDirectory(folder);
                    }
                }
            }

            // Upload
            boolean uploaded = ftpClient.storeFile(fileName, fis);
            if (!uploaded) throw new IOException("FTP upload failed: " + fileName);

            System.out.println("File uploaded successfully to FTP: " + remoteDir + "/" + fileName);
        } catch (Exception e) {
            throw new RuntimeException("FTP upload failed: " + e.getMessage(), e);
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException ignored) {}
        }
    }
}
