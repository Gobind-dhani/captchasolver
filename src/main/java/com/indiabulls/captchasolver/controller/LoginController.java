package com.indiabulls.captchasolver.controller;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/auth")
public class LoginController {

    private static final String LOGIN_URL = "https://www.connect2nsccl.com/auth/#/login";

    @GetMapping("/test")
    public ResponseEntity<String> testSeleniumLoginInputs() {
        WebDriver driver = new ChromeDriver();

        try {
            driver.get(LOGIN_URL);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState").equals("complete"));
            Thread.sleep(2000); // Wait for Angular to render

            // Initial fill
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
            int retryCount = 0;
            int maxRetries = 100;

            while (!otpScreenReached && retryCount < maxRetries) {
                retryCount++;
                System.out.println("Attempt " + retryCount + " to solve captcha...");

                // Re-fill fields if cleared after refresh
                WebElement uField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username")));
                if (uField.getAttribute("value").isEmpty()) {
                    uField.clear();
                    uField.sendKeys("gobind");
                }

                WebElement pField = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("password")));
                if (pField.getAttribute("value").isEmpty()) {
                    pField.clear();
                    pField.sendKeys("Dhani@123456");
                }

                WebElement mField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("consCode")));
                if (mField.getAttribute("value").isEmpty()) {
                    mField.clear();
                    mField.sendKeys("08756");
                }

                // Solve captcha
                WebElement captchaImg = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("captchaImg")));
                String ocrResult = solveCaptcha(captchaImg);
                finalCaptcha = ocrResult;

                // Fill captcha
                WebElement captchaField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("captcha")));
                captchaField.clear();
                captchaField.sendKeys(ocrResult);

                // Click login
                WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("btnLogin")));
                loginButton.click();

                // Handle OTP sent popup before proceeding
                try {
                    WebElement okButton = wait.until(ExpectedConditions.elementToBeClickable(
                            By.cssSelector("button.btn.red-button")
                    ));
                    okButton.click();
                    System.out.println("OTP popup dismissed.");
                } catch (TimeoutException te) {
                    System.out.println("No OTP popup appeared, continuing...");
                }

                try {
                    // Wait for OTP fields - success condition
                    List<WebElement> otpFields = wait.until(
                            ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector("input.otp_input"), 5)
                    );
                    otpScreenReached = true;

                    // Fetch OTP from email
                    Thread.sleep(5000);
                    String otp = fetchLatestOtpFromEmail(
                            "imap.gmail.com",
                            "gobind.barick@indiabulls.com",
                            "fkolsfimzanoexce",
                            "CONNECT2NSCCL@nse.co.in"
                    );

                    if (otp == null || otp.length() != 6) {
                        throw new RuntimeException("Failed to fetch or parse valid 6-digit OTP from email.");
                    }

                    System.out.println("Fetched OTP: " + otp);

                    // Fill OTP
                    for (int i = 0; i < 6; i++) {
                        otpFields.get(i).sendKeys(String.valueOf(otp.charAt(i)));
                    }

                    // Click the Proceed button to land on the home page
                    WebElement proceedButton = wait.until(ExpectedConditions.elementToBeClickable(
                            By.cssSelector("button.btn.btn-danger.button-submit-otp")
                    ));
                    proceedButton.click();
                    System.out.println("Clicked Proceed button after OTP entry.");

                    // Wait until the link is clickable
                    WebElement collateralLink = wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//a[.//span[text()='COLLATERAL MANAGEMENT']]")
                    ));

                    // Click the link
                    collateralLink.click();

                    // Switch to new tab
                    for (String handle : driver.getWindowHandles()) {
                        driver.switchTo().window(handle);
                    }
                    // select Allocation dropdown
                    WebElement allocationDropdown = wait.until(ExpectedConditions
                            .elementToBeClickable(By.xpath("//a[@id='navbarDropdown' and contains(text(), 'ALLOCATION')]")));
                    allocationDropdown.click();

                    // select collateral allocation information option
                    WebElement collateralOption = wait.until(ExpectedConditions
                            .elementToBeClickable(By.xpath("//a[contains(text(), 'COLLATERAL ALLOCATION INFORMATION')]")));
                    collateralOption.click();


                } catch (TimeoutException e) {
                    System.out.println("Captcha likely incorrect. Refreshing captcha...");
                    try {
                        WebElement refreshButton = driver.findElement(By.id("refreshCaptcha"));
                        refreshButton.click();
                    } catch (NoSuchElementException ex) {
                        driver.navigate().refresh();
                        wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                                .executeScript("return document.readyState").equals("complete"));
                    }
                    Thread.sleep(2000);
                }
            }

            if (!otpScreenReached) {
                throw new RuntimeException("Failed to login after " + maxRetries + " captcha attempts.");
            }

            return ResponseEntity.ok("Login flow completed. Captcha: " + finalCaptcha);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Selenium error: " + e.getMessage());}
//        } finally {
//            driver.quit();
//        }
    }

    private String solveCaptcha(WebElement captchaImg) throws Exception {
        String captchaSrc = captchaImg.getAttribute("src");
        if (captchaSrc == null || !captchaSrc.contains(",")) {
            throw new RuntimeException("Invalid captcha src attribute: " + captchaSrc);
        }

        String base64Data = captchaSrc.split(",")[1];
        byte[] decodedBytes = Base64.getDecoder().decode(base64Data);

        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(decodedBytes));
        BufferedImage processedImage = preprocessImage(originalImage);

        Path tempFile = Files.createTempFile("captcha-preprocessed-", ".png");
        ImageIO.write(processedImage, "png", tempFile.toFile());

        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:\\Users\\gobind.barick\\AppData\\Local\\Programs\\Tesseract-OCR\\tessdata");
        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(7);
        tesseract.setTessVariable("tessedit_char_whitelist", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");

        String ocrResult = tesseract.doOCR(tempFile.toFile())
                .replaceAll("[^a-zA-Z0-9]", "")
                .trim();

        System.out.println("OCR Captcha result: '" + ocrResult + "'");
        return ocrResult;
    }

    private BufferedImage preprocessImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int scale = 3;
        BufferedImage resized = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2dResize = resized.createGraphics();
        g2dResize.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2dResize.drawImage(image, 0, 0, width * scale, height * scale, null);
        g2dResize.dispose();

        BufferedImage gray = new BufferedImage(resized.getWidth(), resized.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = gray.getGraphics();
        g.drawImage(resized, 0, 0, null);
        g.dispose();

        for (int x = 0; x < gray.getWidth(); x++) {
            for (int y = 0; y < gray.getHeight(); y++) {
                int rgb = gray.getRGB(x, y) & 0xFF;
                int threshold = 150;
                gray.setRGB(x, y, rgb < threshold ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }

        return gray;
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
                Pattern otpPattern = Pattern.compile("\\b\\d{6}\\b");
                Matcher matcher = otpPattern.matcher(content);
                if (matcher.find()) {
                    return matcher.group(0);
                }
            }
        }
        return null;
    }
}
