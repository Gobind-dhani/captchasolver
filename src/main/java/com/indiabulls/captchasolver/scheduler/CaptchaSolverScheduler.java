package com.indiabulls.captchasolver.scheduler;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

@Component
public class CaptchaSolverScheduler {

    private static final String LOGIN_URL = "https://www.connect2nsccl.com/auth/#/login";

    //@Scheduled(fixedRate = 50000) // Run every 10 seconds
    public void solveCaptchaAndLogin() {
        WebDriver driver = new ChromeDriver();

        try {
            driver.get(LOGIN_URL);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState").equals("complete"));
            Thread.sleep(2000); // Let Angular render fully

            WebElement username = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username")));
            username.sendKeys("testUser123");

            WebElement passwordField = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("password")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", passwordField);
            passwordField.click();
            Thread.sleep(500);
            passwordField.sendKeys("Dhani@123456");

            WebElement memberCode = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("consCode")));
            memberCode.sendKeys("08756");

            WebElement captchaImg = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("captchaImg")));
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
            System.out.println("Processed CAPTCHA saved to: " + tempFile.toAbsolutePath());

            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath("C:\\Users\\gobind.barick\\AppData\\Local\\Programs\\Tesseract-OCR\\tessdata");
            tesseract.setLanguage("eng");
            tesseract.setPageSegMode(7);
            tesseract.setTessVariable("tessedit_char_whitelist", "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");

            String ocrResult = tesseract.doOCR(tempFile.toFile())
                    .replaceAll("[^a-zA-Z0-9]", "")
                    .trim();

            System.out.println("OCR Result: '" + ocrResult + "'");

            WebElement captchaField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("captcha")));
            captchaField.sendKeys(ocrResult);

            Thread.sleep(3000);

            System.out.println("Successfully submitted form with CAPTCHA: " + ocrResult);

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            driver.quit();
        }
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
}
