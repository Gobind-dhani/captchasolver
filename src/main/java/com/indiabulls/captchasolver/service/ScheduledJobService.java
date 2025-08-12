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

    public ScheduledJobService(WebDriverManager webDriverManager,
                               LoginController loginController,
                               PostLoginService postLoginService) {
        this.webDriverManager = webDriverManager;
        this.loginController = loginController;
        this.postLoginService = postLoginService;
    }

    @Scheduled(fixedRate = 600000) // every 15 minutes
    public void runJob() {
        WebDriver driver = null;
        try {
            driver = webDriverManager.getDriver();

            System.out.println("Starting login + captcha solve...");
            loginController.performLogin(driver);

            System.out.println("Starting collateral management navigation + CSV download...");
            postLoginService.navigateToCollateralManagement(driver);

            System.out.println("Scheduled job completed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Scheduled job failed: " + e.getMessage());
        }


    }
}
