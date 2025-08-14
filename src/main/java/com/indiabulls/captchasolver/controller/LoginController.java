package com.indiabulls.captchasolver.controller;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;

import javax.imageio.ImageIO;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class LoginController {

    @Value("${login.url}")
    private String loginUrl;

    @Value("${login.username}")
    private String usernameProp;

    @Value("${login.password}")
    private String passwordProp;

    @Value("${login.membercode}")
    private String memberCodeProp;

    @Value("${email.host}")
    private String emailHost;

    @Value("${email.user}")
    private String emailUser;

    @Value("${email.password}")
    private String emailPassword;

    @Value("${email.sender.filter}")
    private String senderFilter;



    public void performLogin(WebDriver driver) {
        int maxOverallRetries = 5;
        int overallRetryCount = 0;

        while (overallRetryCount < maxOverallRetries) {
            try {
                System.out.println("=== Login attempt " + (overallRetryCount + 1) + " ===");

                driver.get(loginUrl);
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

                wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                        .executeScript("return document.readyState").equals("complete"));

                WebElement username = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username")));
                username.clear();
                username.sendKeys(usernameProp);

                WebElement passwordField = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("password")));
                passwordField.clear();
                passwordField.sendKeys(passwordProp);

                WebElement memberCode = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("consCode")));
                memberCode.clear();
                memberCode.sendKeys(memberCodeProp);

                String finalCaptcha = null;
                boolean otpScreenReached = false;
                int captchaRetryCount = 0;
                int maxCaptchaRetries = 100;

                while (!otpScreenReached && captchaRetryCount < maxCaptchaRetries) {
                    captchaRetryCount++;
                    System.out.println("Captcha attempt " + captchaRetryCount);

                    if (username.getAttribute("value").isEmpty()) username.sendKeys(usernameProp);
                    if (passwordField.getAttribute("value").isEmpty()) passwordField.sendKeys(passwordProp);
                    if (memberCode.getAttribute("value").isEmpty()) memberCode.sendKeys(memberCodeProp);

                    WebElement captchaImg = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("captchaImg")));
                    finalCaptcha = solveCaptcha(captchaImg);

                    WebElement captchaField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("captcha")));
                    captchaField.clear();
                    captchaField.sendKeys(finalCaptcha);

                    WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("btnLogin")));
                    loginButton.click();

                    try {
                        WebElement okButton = wait.until(ExpectedConditions.elementToBeClickable(
                                By.cssSelector("button.btn.red-button")
                        ));
                        okButton.click();
                    } catch (TimeoutException ignored) {}

                    try {
                        WebDriverWait secondPopupWait = new WebDriverWait(driver, Duration.ofSeconds(6));
                        try {
                            secondPopupWait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".modal-backdrop")));
                        } catch (TimeoutException ignored) {}

                        WebElement secondOkButton = secondPopupWait.until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//button[contains(@class,'btn') and contains(@class,'red-button') and normalize-space()='Ok']")));
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", secondOkButton);
                        System.out.println("✅ Second popup dismissed successfully.");
                    } catch (TimeoutException e) {
                        System.out.println("ℹ️ No second popup detected, continuing...");
                    }

                    try {
                        wait.withTimeout(Duration.ofSeconds(2))
                                .until(ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector("input.otp_input"), 5));
                        otpScreenReached = true;
                    } catch (TimeoutException e) {
                        System.out.println("Captcha incorrect. Refreshing...");
                        try {
                            driver.findElement(By.id("refreshCaptcha")).click();
                        } catch (NoSuchElementException ex) {
                            driver.navigate().refresh();
                            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                                    .executeScript("return document.readyState").equals("complete"));
                        }
                    }
                }

                if (!otpScreenReached) {
                    throw new RuntimeException("Failed to reach OTP screen after " + maxCaptchaRetries + " attempts");
                }

                var otpFields = wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector("input.otp_input"), 5));
                String otp = fetchLatestOtpFromEmail(emailHost, emailUser, emailPassword, senderFilter);

                if (otp == null || otp.length() != 6) {
                    throw new RuntimeException("Invalid OTP fetched");
                }

                System.out.println("Fetched OTP: " + otp);
                for (int i = 0; i < 6; i++) {
                    otpFields.get(i).sendKeys(String.valueOf(otp.charAt(i)));
                }

                WebElement proceedButton = wait.until(ExpectedConditions.elementToBeClickable(
                        By.cssSelector("button.btn.btn-danger.button-submit-otp")
                ));
                proceedButton.click();

                System.out.println(" Login and OTP completed. Captcha used: " + finalCaptcha);
                return;

            } catch (Exception e) {
                overallRetryCount++;
                System.err.println(" Error in login attempt " + overallRetryCount + ": " + e.getMessage());
                if (overallRetryCount >= maxOverallRetries) {
                    throw new RuntimeException(" Login failed after " + maxOverallRetries + " retries", e);
                }
                try {
                    Thread.sleep(120000);
                } catch (Exception ignored) {}
                driver.navigate().refresh();
            }
        }
    }

    private String solveCaptcha(WebElement captchaImg) throws Exception {
        String captchaSrc = captchaImg.getAttribute("src");
        if (captchaSrc == null || !captchaSrc.contains(",")) {
            throw new RuntimeException("Invalid captcha src attribute: " + captchaSrc);
        }
        byte[] decodedBytes = Base64.getDecoder().decode(captchaSrc.split(",")[1]);
        BufferedImage processedImage = preprocessImage(ImageIO.read(new ByteArrayInputStream(decodedBytes)));

        // Ensure tessdata is available from resources (works in IDE & JAR)
        File tempTessDataDir = new File(System.getProperty("java.io.tmpdir"), "tessdata");
        if (!tempTessDataDir.exists()) {
            tempTessDataDir.mkdirs();
        }

        File engFile = new File(tempTessDataDir, "eng.traineddata");
        if (!engFile.exists()) {
            try (var in = getClass().getResourceAsStream("/tessdata/eng.traineddata")) {
                if (in == null) {
                    throw new RuntimeException("eng.traineddata not found in resources");
                }
                java.nio.file.Files.copy(
                        in,
                        engFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }
        }

        // Configure Tesseract to use temp tessdata
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tempTessDataDir.getAbsolutePath()); // now points to tessdata folder directly
        tesseract.setLanguage("eng");

        String ocrResult;
        try {
            ocrResult = tesseract.doOCR(processedImage)
                    .replaceAll("[^a-zA-Z0-9]", "")
                    .trim();
        } catch (TesseractException e) {
            throw new RuntimeException("Error reading captcha: " + e.getMessage(), e);
        }

        System.out.println("OCR Captcha result: '" + ocrResult + "'");
        return ocrResult;
    }

    private BufferedImage preprocessImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(image, 0, 0, width, height, null);
        g2d.dispose();
        BufferedImage binary = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics g = binary.getGraphics();
        g.drawImage(resized, 0, 0, null);
        g.dispose();
        return binary;
    }

    private String fetchLatestOtpFromEmail(String host, String user, String password, String senderFilter) throws Exception {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");

        Session emailSession = Session.getDefaultInstance(properties);
        Store store = emailSession.getStore("imaps");
        store.connect(host, user, password);

        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        Message[] messages = inbox.getMessages();
        for (int i = messages.length - 1; i >= 0; i--) {
            Message message = messages[i];
            Address[] froms = message.getFrom();
            String fromEmail = froms == null ? "" : ((InternetAddress) froms[0]).getAddress();

            if (fromEmail.contains(senderFilter)) {
                String content = message.getContent().toString();
                Matcher matcher = Pattern.compile("\\b\\d{6}\\b").matcher(content);
                if (matcher.find()) {
                    return matcher.group(0);
                }
            }
        }
        return null;
    }
}
