package com.indiabulls.captchasolver.config;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.stereotype.Component;

@Component
public class WebDriverManager {

    private WebDriver driver;

    public WebDriver getDriver() {
        if (driver == null) {
            driver = new ChromeDriver(); // initialize once
        }
        return driver;
    }
}
