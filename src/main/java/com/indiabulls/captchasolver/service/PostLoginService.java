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

        //  First download for default tab
        downloadCsvForCurrentTab(driver, wait);

        JavascriptExecutor js = (JavascriptExecutor) driver;

// Click FO tab using improved JS event dispatching
        js.executeScript(
                "var fo = Array.from(document.querySelectorAll('#navbarNavDropdown ul li')).find(el => el.textContent.trim() === 'FO');" +
                        "if (fo) {" +
                        "  fo.scrollIntoView({block:'center', behavior:'smooth'});" +
                        "  ['mouseover', 'mousedown', 'mouseup', 'click'].forEach(evt => fo.dispatchEvent(new MouseEvent(evt, { bubbles: true, cancelable: true, view: window })));" +
                        "} else {" +
                        "  throw 'FO tab not found';" +
                        "}"
        );
        System.out.println("Clicked FO tab.");
        downloadCsvForCurrentTab(driver, wait);

// Click CD tab using improved JS event dispatching
        js.executeScript(
                "var cd = Array.from(document.querySelectorAll('#navbarNavDropdown ul li')).find(el => el.textContent.trim() === 'CD');" +
                        "if (cd) {" +
                        "  cd.scrollIntoView({block:'center', behavior:'smooth'});" +
                        "  ['mouseover', 'mousedown', 'mouseup', 'click'].forEach(evt => cd.dispatchEvent(new MouseEvent(evt, { bubbles: true, cancelable: true, view: window })));" +
                        "} else {" +
                        "  throw 'CD tab not found';" +
                        "}"
        );
        System.out.println("Clicked CD tab.");
        downloadCsvForCurrentTab(driver, wait);

    }
    private void downloadCsvForCurrentTab(WebDriver driver, WebDriverWait wait) {
        try {
            // Wait for page load
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState").equals("complete"));
            System.out.println("Collateral Allocation Information page loaded.");

            // Find "Client Level Details @" span
            WebElement clientDetailsTitle = wait.until(ExpectedConditions
                    .presenceOfElementLocated(By.xpath("//span[contains(@class,'page-title') and contains(normalize-space(.),'Client Level Details @')]")));

            // Find tmcode dropdown below the title
            WebElement targetTmCodeDropdown = clientDetailsTitle.findElement(
                    By.xpath(".//following::select[@id='tmcode'][1]")
            );

            // Scroll & ensure clickable
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", targetTmCodeDropdown);
            wait.until(ExpectedConditions.elementToBeClickable(targetTmCodeDropdown));

            // Try Select, fallback to JS
            boolean selected = false;
            try {
                new Select(targetTmCodeDropdown).selectByValue("ALL");
                selected = true;
                System.out.println("Selected ALL using Selenium Select.");
            } catch (Exception ex) {
                System.out.println("Select failed, using JS: " + ex.getMessage());
            }
            if (!selected) {
                String js =
                        "arguments[0].value = arguments[1];" +
                                "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
                                "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));" +
                                "if (typeof jQuery !== 'undefined') { jQuery(arguments[0]).trigger('change'); }";
                ((JavascriptExecutor) driver).executeScript(js, targetTmCodeDropdown, "ALL");
                System.out.println("Selected ALL via JS events.");
            }

            // Click Show button
            WebElement showButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(normalize-space(.),'Show')]")
            ));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", showButton);
            showButton.click();
            System.out.println("Clicked Show button.");

            int maxWaitSeconds = 60; // Retry up to 1 minute
            JavascriptExecutor js = (JavascriptExecutor) driver;
            boolean clicked = false;

            for (int i = 0; i < maxWaitSeconds; i++) {
                try {
                    Boolean exists = (Boolean) js.executeScript("return document.querySelector('img[alt=\"CSV\"]') !== null;");
                    if (exists) {
                        js.executeScript("document.querySelector('img[alt=\"CSV\"]').scrollIntoView({block:'center'});");
                        js.executeScript("document.querySelector('img[alt=\"CSV\"]').click();");
                        System.out.println(" Clicked CSV download link via JS.");
                        clicked = true;
                        break;
                    }
                } catch (Exception e) {
                    // Ignore and retry
                }
                Thread.sleep(1000);
            }

            if (!clicked) {
                throw new TimeoutException(" CSV download link not found or clickable after " + maxWaitSeconds + " seconds.");
            }



        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
