package com.indiabulls.captchasolver.service;

import com.indiabulls.captchasolver.controller.LoginController;
import com.indiabulls.captchasolver.config.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


@Service
public class ScheduledJobService {

    private final WebDriverManager webDriverManager;
    private final LoginController loginController;
    private final PostLoginService postLoginService;

    // Track if we've done the first login yet
    private boolean firstRun = true;

    public ScheduledJobService(WebDriverManager webDriverManager,
                               LoginController loginController,
                               PostLoginService postLoginService) {
        this.webDriverManager = webDriverManager;
        this.loginController = loginController;
        this.postLoginService = postLoginService;
    }

    @Scheduled(fixedRate = 900000) // every 10 minutes
    public void runJob() {
        WebDriver driver = null;
        try {
            driver = webDriverManager.getDriver();

            if (firstRun) {
                // First run — always login
                System.out.println("🔑 First run detected — logging in...");
                loginController.performLogin(driver);
                postLoginService.goToLandingPageAndCheckCollateralLink(driver);
                firstRun = false;
            } else {
                // Later runs — check if session is still valid
                boolean sessionActive = postLoginService.goToLandingPageAndCheckCollateralLink(driver);

                if (!sessionActive) {
                    System.out.println("⚠️ Session expired — logging in again...");
                    loginController.performLogin(driver);
                    postLoginService.goToLandingPageAndCheckCollateralLink(driver);
                }
            }

            // Post-login tasks (run every time after session check/login)
            postLoginService.navigateToCollateralManagement(driver);

            System.out.println("✅ Scheduled job completed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ Scheduled job failed: " + e.getMessage());
        }
    }
}

