package com.indiabulls.captchasolver.controller;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Controller;

import javax.imageio.ImageIO;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class LoginController {

    private static final String LOGIN_URL = "https://www.connect2nsccl.com/auth/#/login";
    private static final ITesseract TESSERACT = createTesseract();

    private static ITesseract createTesseract() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:\\Users\\gobind.barick\\AppData\\Local\\Programs\\Tesseract-OCR\\tessdata");
        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(7);
        tesseract.setTessVariable("tessedit_char_whitelist",
                "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        return tesseract;
    }

    private WebDriver launchNewDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-blink-features=AutomationControlled");
        return new ChromeDriver(options);
    }

    public void performLogin(WebDriver driver) {
        int maxOverallRetries = 5;
        int overallRetryCount = 0;

        while (overallRetryCount < maxOverallRetries) {
            try {
                System.out.println("=== Login attempt " + (overallRetryCount + 1) + " ===");

                driver.get(LOGIN_URL);
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

                wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                        .executeScript("return document.readyState").equals("complete"));

                WebElement username = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username")));
                username.clear();
                username.sendKeys("gobind");

                WebElement passwordField = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("password")));
                passwordField.clear();
                passwordField.sendKeys("Dhani@123456");

                WebElement memberCode = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("consCode")));
                memberCode.clear();
                memberCode.sendKeys("08756");

                String finalCaptcha = null;
                boolean otpScreenReached = false;
                int captchaRetryCount = 0;
                int maxCaptchaRetries = 100;

                while (!otpScreenReached && captchaRetryCount < maxCaptchaRetries) {
                    captchaRetryCount++;
                    System.out.println("Captcha attempt " + captchaRetryCount);

                    if (username.getAttribute("value").isEmpty()) username.sendKeys("gobind");
                    if (passwordField.getAttribute("value").isEmpty()) passwordField.sendKeys("Dhani@123456");
                    if (memberCode.getAttribute("value").isEmpty()) memberCode.sendKeys("08756");

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
                    } catch (TimeoutException ignored) {
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
                String otp = fetchLatestOtpFromEmail(
                        "imap.gmail.com",
                        "gobind.barick@indiabulls.com",
                        "fkolsfimzanoexce",
                        "CONNECT2NSCCL@nse.co.in"
                );

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
                e.printStackTrace();

                if (overallRetryCount >= maxOverallRetries) {
                    throw new RuntimeException(" Login failed after " + maxOverallRetries + " retries", e);
                }

                System.out.println(" Relaunching driver and retrying...");
                try {
                    driver.quit();
                    Thread.sleep(120000);
                } catch (Exception ignored) {
                }
                driver = launchNewDriver();
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

        String ocrResult = TESSERACT.doOCR(processedImage)
                .replaceAll("[^a-zA-Z0-9]", "")
                .trim();

        System.out.println("OCR Captcha result: '" + ocrResult + "'");
        return ocrResult;
    }

    private BufferedImage preprocessImage(BufferedImage image) {
        int scale = 1;
        int width = image.getWidth() * scale;
        int height = image.getHeight() * scale;

        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
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
