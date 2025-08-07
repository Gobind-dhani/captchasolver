package com.indiabulls.captchasolver.controller;

import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.FlagTerm;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.nio.file.*;
import java.util.Base64;
import java.util.Properties;
import java.util.regex.Pattern;
import jakarta.mail.*;

import com.twocaptcha.TwoCaptcha;
import com.twocaptcha.captcha.Normal;

@RestController
@RequestMapping("/auth")
public class LoginController {

    private static final String LOGIN_URL = "https://www.connect2nsccl.com/auth/#/login";
    private static final String CAPTCHA_IMG_ID = "captchaImg";
    private static final String API_KEY_2CAPTCHA = "YOUR_2CAPTCHA_API_KEY";
    private static final String EMAIL = "your.email@gmail.com";
    private static final String EMAIL_PASSWORD = "your_app_password";

    @GetMapping("/login")
    public ResponseEntity<String> performLogin() throws Exception {
        WebDriver driver = new ChromeDriver();
        driver.get(LOGIN_URL);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        WebElement captchaImg = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.id(CAPTCHA_IMG_ID))
        );
        String src = captchaImg.getAttribute("src");
        String base64Data = src.split(",")[1];

        // Decode base64 to image file
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        Path tempFile = Files.createTempFile("captcha", ".jpg");
        Files.write(tempFile, imageBytes);

        // Solve captcha via 2Captcha
        TwoCaptcha solver = new TwoCaptcha(API_KEY_2CAPTCHA);
        Normal captcha = new Normal(tempFile.toString());
        try {
            solver.solve(captcha);
        } finally {
            Files.deleteIfExists(tempFile);
        }
        String captchaCode = captcha.getCode();

        // Enter login details
        driver.findElement(By.id("username")).sendKeys("yourUser");
        driver.findElement(By.id("memberCode")).sendKeys("yourMemberCode");
        driver.findElement(By.id("password")).sendKeys("yourPassword");
        driver.findElement(By.id("captchaInput")).sendKeys(captchaCode);
        driver.findElement(By.id("loginButton")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("otpInput")));

        String otp = fetchOtpFromEmail();
        driver.findElement(By.id("otpInput")).sendKeys(otp);
        driver.findElement(By.id("submitOtpButton")).click();

        driver.quit();
        return ResponseEntity.ok("Login complete with OTP: " + otp);
    }

    private String fetchOtpFromEmail() throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        Session session = Session.getDefaultInstance(props);
        Store store = session.getStore("imaps");
        store.connect("imap.gmail.com", EMAIL, EMAIL_PASSWORD);
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE);

        for (int i = inbox.getMessageCount() - 1; i >= 0; i--) {
            Message msg = inbox.getMessage(i);
            if (msg.getSubject().contains("OTP")) {
                msg.setFlag(Flags.Flag.SEEN, true);
                String content = (String)((MimeMultipart) msg.getContent()).getBodyPart(0).getContent();
                var m = Pattern.compile("\\b\\d{6}\\b").matcher(content);
                if (m.find()) return m.group();
            }
        }
        throw new IllegalStateException("OTP email not found");
    }
}
