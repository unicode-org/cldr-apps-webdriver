package org.unicode.cldr.surveydriver;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class SurveyDriverVettingTable {

    /**
     * Test the vetting table to make sure it contains the expected content under certain circumstances.
     *
     * Purpose: make sure that code revisions, such as refactoring CldrSurveyVettingTable.js, do not
     * cause unintended changes to the table contents.
     *
     * https://unicode-org.atlassian.net/browse/CLDR-11571 "Avoid rebuilding entire table on Survey Tool update"
     * https://unicode-org.atlassian.net/browse/CLDR-11943 "Implement automated testing for Survey Tool table updating"
     */
    public static boolean testVettingTable(SurveyDriver s) {
        if (!s.login()) {
            return false;
        }
        String loc = "aa";
        String page = "Numbering_Systems";
        String url = SurveyDriver.BASE_URL + "v#/" + loc + "/" + page;

        WebDriver driver = s.driver;
        driver.get(url);
        if (!s.waitForTitle(page, url)) {
            return false;
        }
        if (!s.waitUntilLoadingMessageDone(url)) {
            return false;
        }
        if (!s.hideLeftSidebar(url)) {
            return false;
        }
        if (!s.waitUntilElementInactive("left-sidebar", url)) {
            return false;
        }
        if (!s.waitUntilElementInactive("overlay", url)) {
            return false;
        }

        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.testTable = function(theTable, reuseTable) { console.log(theTable.json); }");

        /*
         * Get the table three times.
         * First time, without voting.
         * Second time, after voting for winning ("latn") in first row.
         * Third time, after voting for new value "taml" in first row.
         * Finally, abstain so the next user will find the db the same as it was.
         */
        String[] table = { null, null, null };
        final int tableCount = 3;
        String dataDirName = null;
        try {
            dataDirName = new File("./data").getCanonicalPath();
        } catch (IOException e1) {
            System.out.println("Exception getting data directory: " + e1);
            e1.printStackTrace();
            return false;
        }
        for (int t = 0; t < tableCount; t++) {
            String fName = dataDirName + "/" + "table" + t + ".txt";
            File f = new File(fName);
            try {
                table[t] = normalizeTable(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.out.println("Exception reading file " + fName + ": " + e);
                e.printStackTrace();
                return false;
            }
            if (t > 0 && table[t].equals(table[t - 1])) {
                System.out.println("File " + fName + " should not be identical to the previous file");
                return false;
            }
        }
        String[] cellIds = { "proposedcell", "input", "nocell" };
        String rowId = "r@7b8ee7884f773afa";
        int i = 0;
        int goodTableCount = 0;
        for (String cell : cellIds) {
            /*
             * Depend on the table having id "vetting-table". Can't select by tagName("table"), since
             * then we'd be liable to get the wrong table, with id "proto-datarow".
             */
            WebElement tableEl = driver.findElement(By.id("vetting-table"));

            /*
             * Point the "mouse" at the "title-locale" (outside the table), to prevent getting "tooltip" stuff
             * from hovering over, for example, the Winning cell. Ignore the log message,
             * "INFO: When using the W3C Action commands, offsets are from the center of element".
             */
            WebElement titleLocaleEl = driver.findElement(By.id("title-locale"));
            new Actions(driver).moveToElement(titleLocaleEl, 0, 0).build().perform();

            /*
             * Sleep a while to give the table time to update. 5 seconds is enough. 2 seconds is not enough.
             * TODO: find a better way to detect when the table has been updated.
             */
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                System.out.println("Sleep interrupted before finished waiting to get table html; " + e);
            }

            /*
             * Wait for tr_checking2 element (temporary green background) NOT to exist.
             * The temporary green background indicates that the client is waiting for a response
             * from the server before it can update the display to show the results of a
             * completed voting operation.
             */
            if (!s.waitUntilClassExists("tr_checking2", false, url)) {
                return false;
            }
            String tableHtml = normalizeTable(tableEl.getAttribute("outerHTML"));

            if (table[i].equals(tableHtml)) {
                System.out.println("✅ table " + i + " is OK");
                ++goodTableCount;
            } else {
                System.out.println(
                    "❌ table " +
                    i +
                    " is different (" +
                    tableHtml.length() +
                    " units):\n" +
                    tableHtml +
                    "\n\nExpected (" +
                    table[i].length() +
                    " units):\n" +
                    table[i] +
                    "\n"
                );
            }
            ++i;
            boolean doAdd = cell.equals("input");
            String tagName = doAdd ? "button" : "input";
            String cellId = doAdd ? "addcell" : cell;
            WebElement rowEl = null, columnEl = null, clickEl = null;

            int repeats = 0;
            for (;;) {
                try {
                    rowEl = driver.findElement(By.id(rowId));
                } catch (StaleElementReferenceException e) {
                    if (++repeats > 4) {
                        break;
                    }
                    System.out.println(
                        "Continuing after StaleElementReferenceException for findElement by id rowId " +
                        rowId +
                        " for " +
                        url
                    );
                    continue;
                } catch (Exception e) {
                    System.out.println(e);
                    break;
                }
                if (rowEl == null) {
                    System.out.println("❌ Vetting-table test failed, missing row id " + rowId + " for " + url);
                    return false;
                }
                try {
                    columnEl = rowEl.findElement(By.id(cellId));
                } catch (StaleElementReferenceException e) {
                    if (++repeats > 4) {
                        break;
                    }
                    System.out.println(
                        "Continuing after StaleElementReferenceException for findElement by id cellId " +
                        cellId +
                        " for " +
                        url
                    );
                    continue;
                } catch (Exception e) {
                    System.out.println(e);
                    break;
                }
                if (columnEl == null) {
                    System.out.println(
                        "❌ Vetting-table test failed, no column " + cellId + " for row " + rowId + " for " + url
                    );
                    return false;
                }
                try {
                    clickEl = columnEl.findElement(By.tagName(tagName));
                } catch (StaleElementReferenceException e) {
                    if (++repeats > 4) {
                        break;
                    }
                    System.out.println(
                        "Continuing after StaleElementReferenceException for findElement by tagName " +
                        rowId +
                        " for " +
                        url
                    );
                    continue;
                } catch (Exception e) {
                    System.out.println(e);
                    break;
                }
                break;
            }
            if (clickEl == null) {
                System.out.println(
                    "❌ Vetting-table test failed, no tag " + tagName + " for row " + rowId + " for " + url
                );
                return false;
            }
            clickEl = s.waitUntilRowCellTagElementClickable(clickEl, rowId, cellId, tagName, url);
            if (clickEl == null) {
                return false;
            }
            try {
                s.wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("overlay")));
            } catch (Exception e) {
                System.out.println(e);
                System.out.println(
                    "❌ Fast vote test failed, invisibilityOfElementLocated overlay for row " + rowId + " for " + url
                );
                return false;
            }
            try {
                s.clickOnRowCellTagElement(clickEl, rowId, cellId, tagName, url);
            } catch (StaleElementReferenceException e) {
                if (++repeats > 4) {
                    break;
                }
                System.out.println(
                    "Continuing after StaleElementReferenceException for clickEl.click for row " + rowId + " for " + url
                );
                continue;
            } catch (Exception e) {
                System.out.println(e);
                System.out.println("❌ Vetting-table test failed, clickEl.click for row " + rowId + " for " + url);
                return false;
            }
            if (doAdd) {
                /*
                 * Problem here: waitInputBoxAppears can get StaleElementReferenceException five times,
                 * must be for rowEl which isn't re-gotten. For now at least, just continue loop if
                 * waitInputBoxAppears returns null.
                 */
                WebElement inputEl = s.waitInputBoxAppears(rowEl, url);
                if (inputEl == null) {
                    System.out.println("Warning: continuing, didn't see input box for " + url);
                    continue;
                }
                inputEl = s.waitUntilRowCellTagElementClickable(inputEl, rowId, cellId, "input", url);
                if (inputEl == null) {
                    System.out.println("Warning: continuing, input box not clickable for " + url);
                    continue;
                }
                inputEl.clear();
                inputEl.click();
                inputEl.sendKeys("taml");
                inputEl.sendKeys(Keys.RETURN);
            }
        }

        if (goodTableCount != tableCount) {
            System.out.println("❌ Vetting-table test failed for " + loc + ", " + page);
            return false;
        }
        System.out.println("✅ Vetting-table test passed for " + loc + ", " + page);
        return true;
    }

    private static String normalizeTable(String tableHtml) {
        /*
         * For unknown reasons, html varies between class="fallback" and class="fallback_root" for the same item.
         * That may be a bug on the server side.
         * Work around it here by changing "fallback_root" to "fallback".
         */
        tableHtml = tableHtml.replace("fallback_root", "fallback");
        tableHtml = tableHtml.replaceFirst("<tbody>\\s*<tr", "<tbody><tr"); // whitespace varies here at random

        tableHtml = tableHtml.replace(" hideCov80", ""); // present or absent at random?
        tableHtml = tableHtml.replace(" hideCov100", ""); // present or absent at random?

        return tableHtml.trim();
    }
}
