package com.indiabulls.captchasolver.controller;

import com.indiabulls.captchasolver.config.WebDriverManager;
import com.indiabulls.captchasolver.service.PostLoginService;
import org.openqa.selenium.WebDriver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AfterLoginController {

    private final WebDriverManager webDriverManager;
    private final PostLoginService postLoginService;

    public AfterLoginController(WebDriverManager webDriverManager, PostLoginService postLoginService) {
        this.webDriverManager = webDriverManager;
        this.postLoginService = postLoginService;
    }

    @GetMapping("/auth/after-login")
    public String runAfterLoginTasks() {
        WebDriver driver = webDriverManager.createDriver();
        return "Navigated to Collateral Management page.";
    }
}
