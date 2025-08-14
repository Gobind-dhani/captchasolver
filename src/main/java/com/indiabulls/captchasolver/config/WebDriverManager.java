package com.indiabulls.captchasolver.config;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.stereotype.Component;

@Component
public class WebDriverManager {
    public WebDriver createDriver() {
        return new ChromeDriver();
    }
}
