package com.indiabulls.captchasolver.service;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class PostLoginService {

    public void navigateToCollateralManagement(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        ((JavascriptExecutor) driver).executeScript(
                "window.open('https://www.connect2nsccl.com/collateral-management/#/allocation/file-upload', '_blank');"
        );

        List<String> tabs = new ArrayList<>(driver.getWindowHandles());
        driver.switchTo().window(tabs.get(tabs.size() - 1));

        WebDriverWait fastWait = new WebDriverWait(driver, Duration.ofSeconds(12));
        fastWait.until(webDriver -> ((JavascriptExecutor) webDriver)
                .executeScript("return document.readyState").equals("complete"));

        System.out.println("Collateral Management file upload page opened in new tab.");

        // Step 1: Click ALLOCATION dropdown
        WebElement allocationDropdown = wait.until(ExpectedConditions
                .elementToBeClickable(By.id("navbarDropdown")));
        allocationDropdown.click();
        System.out.println("Clicked ALLOCATION dropdown.");

        // Step 2: Select COLLATERAL ALLOCATION INFORMATION
        WebElement collateralInfoLink = wait.until(ExpectedConditions
                .elementToBeClickable(By.xpath("//a[contains(text(), 'COLLATERAL ALLOCATION INFORMATION')]")));
        collateralInfoLink.click();
        System.out.println("Clicked COLLATERAL ALLOCATION INFORMATION link.");

        // 1️⃣ First download for default tab
        downloadCsvForCurrentTab(driver, wait);

        // 2️⃣ Click FO and download
        WebElement foTab = wait.until(ExpectedConditions
                .elementToBeClickable(By.xpath("//li[contains(normalize-space(.),'FO')]")));
        foTab.click();
        System.out.println("Clicked FO tab.");
        downloadCsvForCurrentTab(driver, wait);

        // 3️⃣ Click CD and download
        WebElement cdTab = wait.until(ExpectedConditions
                .elementToBeClickable(By.xpath("//li[contains(normalize-space(.),'CD')]")));
        cdTab.click();
        System.out.println("Clicked CD tab.");
        downloadCsvForCurrentTab(driver, wait);
    }

    private void downloadCsvForCurrentTab(WebDriver driver, WebDriverWait wait) {
        try {
            // Wait for page load
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState").equals("complete"));
            System.out.println("Collateral Allocation Information page loaded.");

            // Find the "Client Level Details @" span
            WebElement clientDetailsTitle = wait.until(ExpectedConditions
                    .presenceOfElementLocated(By.xpath("//span[contains(@class,'page-title') and contains(normalize-space(.),'Client Level Details @')]")));

            // Now find the tmcode dropdown below that span
            WebElement targetTmCodeDropdown = clientDetailsTitle.findElement(
                    By.xpath(".//following::select[@id='tmcode'][1]")  // first tmcode select after the title
            );

            // Ensure it's clickable
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", targetTmCodeDropdown);
            wait.until(ExpectedConditions.elementToBeClickable(targetTmCodeDropdown));

            // Try using Select
            boolean selected = false;
            try {
                Select select = new Select(targetTmCodeDropdown);
                select.selectByValue("ALL");
                selected = true;
                System.out.println("Selected ALL using Selenium Select.");
            } catch (Exception ex) {
                System.out.println("Selenium Select failed, trying JS fallback: " + ex.getMessage());
            }

            // JS fallback
            if (!selected) {
                String js =
                        "arguments[0].value = arguments[1];" +
                                "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
                                "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));" +
                                "if (typeof jQuery !== 'undefined') { jQuery(arguments[0]).trigger('change'); }";
                ((JavascriptExecutor) driver).executeScript(js, targetTmCodeDropdown, "ALL");
                System.out.println("Selected ALL using JS + events.");
            }

            Thread.sleep(500); // short wait for selection to apply

            // Find and click Show button in current context
            WebElement showButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(normalize-space(.),'Show')]")
            ));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", showButton);
            showButton.click();
            System.out.println("Clicked Show button.");

            Thread.sleep(700); // small wait for table refresh

            // Click CSV icon
            WebElement csvIcon = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//img[@alt='CSV']")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", csvIcon);
            csvIcon.click();
            System.out.println("Clicked CSV download icon.");

            Thread.sleep(1000); // wait for download to trigger

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
