package org.unicode.cldr.surveydriver;

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
	 * https://unicode.org/cldr/trac/ticket/11571 "Avoid rebuilding entire table on Survey Tool update"
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
		js.executeScript(
				"window.testTable = function(theTable, reuseTable) { console.log(theTable.json); }");

		/*
		 * Get the table three times.
		 * First time, without voting.
		 * Second time, after voting for winning ("latn") in first row.
		 * Third time, after voting for new value "taml" in first row.
		 * Finally, abstain so the next user will find the db the same as it was.
		 */
		String[] table = { table0, table1, table2 };
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
			if (!s.waitUntilClassChecking(false, url)) {
				return false;
			}
			String tableHtml = tableEl.getAttribute("outerHTML");
			/*
			 * For unknown reasons, html varies between class="fallback" and class="fallback_root" for the same item.
			 * That may be a bug on the server side.
			 * Work around it here by changing "fallback_root" to "fallback".
			 */
			tableHtml = tableHtml.replace("fallback_root", "fallback");
			tableHtml = tableHtml.replaceFirst("<tbody>\\s*<tr", "<tbody><tr"); // whitespace varies here at random

			if (table[i].equals(tableHtml)) {
				System.out.println("✅ table " + i + " is OK");
				++goodTableCount;
			} else {
				System.out.println("❌ table " + i + " is different than expected: " + tableHtml
						+ "\n\nExpected: " + table[i] + "\n");
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
					System.out
							.println("Continuing after StaleElementReferenceException for findElement by id rowId "
									+ rowId + " for " + url);
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
					System.out
							.println("Continuing after StaleElementReferenceException for findElement by id cellId "
									+ cellId + " for " + url);
					continue;
				} catch (Exception e) {
					System.out.println(e);
					break;
				}
				if (columnEl == null) {
					System.out.println(
							"❌ Vetting-table test failed, no column " + cellId + " for row " + rowId + " for " + url);
					return false;
				}
				try {
					clickEl = columnEl.findElement(By.tagName(tagName));
				} catch (StaleElementReferenceException e) {
					if (++repeats > 4) {
						break;
					}
					System.out.println("Continuing after StaleElementReferenceException for findElement by tagName "
							+ rowId + " for " + url);
					continue;
				} catch (Exception e) {
					System.out.println(e);
					break;
				}
				break;
			}
			if (clickEl == null) {
				System.out.println(
						"❌ Vetting-table test failed, no tag " + tagName + " for row " + rowId + " for " + url);
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
				System.out.println("❌ Fast vote test failed, invisibilityOfElementLocated overlay for row " + rowId
						+ " for " + url);
				return false;
			}
			try {
				s.clickOnRowCellTagElement(clickEl, rowId, cellId, tagName, url);
			} catch (StaleElementReferenceException e) {
				if (++repeats > 4) {
					break;
				}
				System.out.println("Continuing after StaleElementReferenceException for clickEl.click for row "
						+ rowId + " for " + url);
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

		if (goodTableCount != 3) {
			System.out.println("❌ Vetting-table test failed for " + loc + ", " + page);
			return false;
		}
		System.out.println("✅ Vetting-table test passed for " + loc + ", " + page);
		return true;
	}

	static private String table0 = "<table id=\"vetting-table\" class=\"data table table-bordered vetting-page\">\n"
			+ "<thead>\n" + "<tr class=\"headingb active\">\n" + "<!--  see stui.js for the following -->\n"
			+ "    <th title=\"Code for this item\" id=\"null\">Code</th>\n"
			+ "	<th title=\"Comparison value\" id=\"null\">English</th>\n"
			+ "	<th title=\"Abstain from voting on this item\" id=\"null\" class=\"d-no\">Abstain</th>\n"
			+ "	<th title=\"Shows a checkmark if you voted\" id=\"null\" style=\"display: none\">Voting</th>\n"
			+ "	<th title=\"Approval Status\" id=\"null\" class=\"d-status\">A</th>\n"
			+ "	<th title=\"Status Icon\" id=\"null\" style=\"display: none\">Errors</th>\n"
			+ "	<th title=\"Winning value\" id=\"null\">Winning</th>\n"
			+ "	<th title=\"Add another value\" id=\"null\">Add</th><th title=\"Other non-winning items\" id=\"null\">Others</th>\n"
			+ "<!--	<th title=\"$flyoverchange\" id='stui-htmlchange' class='d-change'></th>-->\n" + "</tr>\n"
			+ "</thead>\n" + "<tbody><tr id=\"null\" class=\"undefined cov40\">\n"
			+ "	<th class=\"partsection\" colspan=\"9\">\n" + "		<a id=\"Numbering System\">Numbering System</a>\n"
			+ "	</th>\n" + "</tr><tr id=\"r@7b8ee7884f773afa\" class=\"vother cov40\">\n"
			+ "			<td id=\"codecell\" class=\"d-code\" title=\"Code for this item\"><span>default</span></td>\n"
			+ "			<td id=\"comparisoncell\" title=\"Comparison value\" class=\"d-disp\" dir=\"ltr\" lang=\"en-ZZ\"><span class=\"subSpan\">latn</span><div class=\"d-example well well-sm\" dir=\"ltr\" lang=\"en-ZZ\"><div class=\"cldr_example\">2345</div></div><div class=\"infos-code\"><img src=\"example.png\" alt=\"Example\"></div></td>\n"
			+ "			<td id=\"nocell\" title=\"undefined\" class=\"d-no-vo-false\"><label title=\"\" class=\"btn btn-default\" data-original-title=\"Vote\"><input id=\"NO__xauhj\" class=\"ichoice-x\" type=\"radio\" title=\"click to vote\" value=\"\"></label></td>\n"
			+ "			<td style=\"display: none\" id=\"votedcell\" class=\"d-vo\" title=\"Shows a checkmark if you voted\"></td>\n"
			+ "			<td id=\"statuscell\" class=\"d-dr-missing d-dr-status\" title=\"Status: Missing\"></td>\n"
			+ "			<td style=\"display: none\" id=\"errcell\" class=\"d-st-okay\" title=\"Status Icon\"></td>\n"
			+ "			<td id=\"proposedcell\" title=\"Winning value\" class=\"d-win\" dir=\"ltr\" lang=\"aa\"><span class=\"s-flag-d\" title=\"Losing items may be flagged for CLDR Committee review.\">&nbsp; &nbsp;</span><div class=\"d-item\"><div class=\"choice-field\"><label title=\"\" class=\"btn btn-default\" data-original-title=\"Vote\"><input id=\"v4oaR4oaR4oaR__xauhj\" class=\"ichoice-o\" type=\"radio\" title=\"click to vote\" value=\"↑↑↑\"></label><span class=\"subSpan\"><span class=\"fallback\" dir=\"ltr\" lang=\"aa\">latn</span></span><div class=\"i-star\" title=\"This mark shows on the item which was approved in the last release, if any.\"></div></div><div class=\"d-example well well-sm\" dir=\"ltr\" lang=\"aa\"><div class=\"cldr_example\">2345</div></div></div></td>\n"
			+ "			<td id=\"addcell\" title=\"Add another value\" class=\"d-win\"><form class=\"form-inline\"><div class=\"button-add form-group\"><button class=\"btn btn-primary\" title=\"\" type=\"submit\" data-original-title=\"Add\"><span class=\"glyphicon glyphicon-plus\"></span></button></div></form></td>\n"
			+ "			<td id=\"othercell\" title=\"Other non-winning items\" class=\"d-win\" dir=\"ltr\" lang=\"aa\"></td>\n"
			+ "		</tr><tr id=\"r@602dd39f06ddeb32\" class=\"vother cov40\">\n"
			+ "			<td id=\"codecell\" class=\"d-code\" title=\"Code for this item\"><span>native</span></td>\n"
			+ "			<td id=\"comparisoncell\" title=\"Comparison value\" class=\"d-disp\" dir=\"ltr\" lang=\"en-ZZ\"><span class=\"subSpan\">latn</span><div class=\"d-example well well-sm\" dir=\"ltr\" lang=\"en-ZZ\"><div class=\"cldr_example\">2345</div></div><div class=\"infos-code\"><img src=\"example.png\" alt=\"Example\"></div></td>\n"
			+ "			<td id=\"nocell\" title=\"undefined\" class=\"d-no-vo-false\"><label title=\"\" class=\"btn btn-default\" data-original-title=\"Vote\"><input id=\"NO__xgorkb\" class=\"ichoice-x\" type=\"radio\" title=\"click to vote\" value=\"\"></label></td>\n"
			+ "			<td style=\"display: none\" id=\"votedcell\" class=\"d-vo\" title=\"Shows a checkmark if you voted\"></td>\n"
			+ "			<td id=\"statuscell\" class=\"d-dr-missing d-dr-status\" title=\"Status: Missing\"></td>\n"
			+ "			<td style=\"display: none\" id=\"errcell\" class=\"d-st-okay\" title=\"Status Icon\"></td>\n"
			+ "			<td id=\"proposedcell\" title=\"Winning value\" class=\"d-win\" dir=\"ltr\" lang=\"aa\"><span class=\"s-flag-d\" title=\"Losing items may be flagged for CLDR Committee review.\">&nbsp; &nbsp;</span><div class=\"d-item\"><div class=\"choice-field\"><label title=\"\" class=\"btn btn-default\" data-original-title=\"Vote\"><input id=\"v4oaR4oaR4oaR__xgorkb\" class=\"ichoice-o\" type=\"radio\" title=\"click to vote\" value=\"↑↑↑\"></label><span class=\"subSpan\"><span class=\"fallback\" dir=\"ltr\" lang=\"aa\">latn</span></span><div class=\"i-star\" title=\"This mark shows on the item which was approved in the last release, if any.\"></div></div><div class=\"d-example well well-sm\" dir=\"ltr\" lang=\"aa\"><div class=\"cldr_example\">2345</div></div></div></td>\n"
			+ "			<td id=\"addcell\" title=\"Add another value\" class=\"d-win\"><form class=\"form-inline\"><div class=\"button-add form-group\"><button class=\"btn btn-primary\" title=\"\" type=\"submit\" data-original-title=\"Add\"><span class=\"glyphicon glyphicon-plus\"></span></button></div></form></td>\n"
			+ "			<td id=\"othercell\" title=\"Other non-winning items\" class=\"d-win\" dir=\"ltr\" lang=\"aa\"></td>\n"
			+ "		</tr></tbody>\n" + "</table>";

	static private String table1 = "<table id=\"vetting-table\" class=\"data table table-bordered vetting-page\">\n"
			+ "<thead>\n" + "<tr class=\"headingb active\">\n" + "<!--  see stui.js for the following -->\n"
			+ "    <th title=\"Code for this item\" id=\"null\">Code</th>\n"
			+ "	<th title=\"Comparison value\" id=\"null\">English</th>\n"
			+ "	<th title=\"Abstain from voting on this item\" id=\"null\" class=\"d-no\">Abstain</th>\n"
			+ "	<th title=\"Shows a checkmark if you voted\" id=\"null\" style=\"display: none\">Voting</th>\n"
			+ "	<th title=\"Approval Status\" id=\"null\" class=\"d-status\">A</th>\n"
			+ "	<th title=\"Status Icon\" id=\"null\" style=\"display: none\">Errors</th>\n"
			+ "	<th title=\"Winning value\" id=\"null\">Winning</th>\n"
			+ "	<th title=\"Add another value\" id=\"null\">Add</th><th title=\"Other non-winning items\" id=\"null\">Others</th>\n"
			+ "<!--	<th title=\"$flyoverchange\" id='stui-htmlchange' class='d-change'></th>-->\n" + "</tr>\n"
			+ "</thead>\n" + "<tbody><tr id=\"null\" class=\"undefined cov40\">\n"
			+ "	<th class=\"partsection\" colspan=\"9\">\n" + "		<a id=\"Numbering System\">Numbering System</a>\n"
			+ "	</th>\n" + "</tr><tr id=\"r@7b8ee7884f773afa\" class=\"vother cov40\">\n"
			+ "			<td id=\"codecell\" class=\"d-code\" title=\"Code for this item\"><span>default</span></td>\n"
			+ "			<td id=\"comparisoncell\" title=\"Comparison value\" class=\"d-disp\" dir=\"ltr\" lang=\"en-ZZ\"><span class=\"subSpan\">latn</span><div class=\"d-example well well-sm\" dir=\"ltr\" lang=\"en-ZZ\"><div class=\"cldr_example\">2345</div></div><div class=\"infos-code\"><img src=\"example.png\" alt=\"Example\"></div></td>\n"
			+ "			<td id=\"nocell\" title=\"undefined\" class=\"d-no-vo-true\"><label title=\"\" class=\"btn btn-default\" data-original-title=\"Vote\"><input id=\"NO__xauhj\" class=\"ichoice-o\" type=\"radio\" title=\"click to vote\" value=\"\"></label></td>\n"
			+ "			<td style=\"display: none\" id=\"votedcell\" class=\"d-vo\" title=\"Shows a checkmark if you voted\"></td>\n"
			+ "			<td id=\"statuscell\" class=\"d-dr-approved d-dr-status\" title=\"Status: Approved\"></td>\n"
			+ "			<td style=\"display: none\" id=\"errcell\" class=\"d-st-okay\" title=\"Status Icon\"></td>\n"
			+ "			<td id=\"proposedcell\" title=\"Winning value\" class=\"pu-select d-win\" dir=\"ltr\" lang=\"aa\"><span class=\"s-flag-d\" title=\"Losing items may be flagged for CLDR Committee review.\">&nbsp; &nbsp;</span><div class=\"d-item\"><div class=\"choice-field\"><label title=\"\" class=\"btn btn-default\" data-original-title=\"Vote\"><input id=\"v4oaR4oaR4oaR__xauhj\" class=\"ichoice-x\" type=\"radio\" title=\"click to vote\" value=\"↑↑↑\"></label><span class=\"subSpan\"><span class=\"fallback\" dir=\"ltr\" lang=\"aa\">latn</span></span><div class=\"i-star\" title=\"This mark shows on the item which was approved in the last release, if any.\"></div></div><div class=\"d-example well well-sm\" dir=\"ltr\" lang=\"aa\"><div class=\"cldr_example\">2345</div></div></div></td>\n"
			+ "			<td id=\"addcell\" title=\"Add another value\" class=\"d-win\"><form class=\"form-inline\"><div class=\"button-add form-group\"><button class=\"btn btn-primary\" title=\"\" type=\"submit\" data-original-title=\"Add\"><span class=\"glyphicon glyphicon-plus\"></span></button></div></form></td>\n"
			+ "			<td id=\"othercell\" title=\"Other non-winning items\" class=\"d-win\" dir=\"ltr\" lang=\"aa\"></td>\n"
			+ "		</tr><tr id=\"r@602dd39f06ddeb32\" class=\"vother cov40\">\n"
			+ "			<td id=\"codecell\" class=\"d-code\" title=\"Code for this item\"><span>native</span></td>\n"
			+ "			<td id=\"comparisoncell\" title=\"Comparison value\" class=\"d-disp\" dir=\"ltr\" lang=\"en-ZZ\"><span class=\"subSpan\">latn</span><div class=\"d-example well well-sm\" dir=\"ltr\" lang=\"en-ZZ\"><div class=\"cldr_example\">2345</div></div><div class=\"infos-code\"><img src=\"example.png\" alt=\"Example\"></div></td>\n"
			+ "			<td id=\"nocell\" title=\"undefined\" class=\"d-no-vo-false\"><label title=\"\" class=\"btn btn-default\" data-original-title=\"Vote\"><input id=\"NO__xgorkb\" class=\"ichoice-x\" type=\"radio\" title=\"click to vote\" value=\"\"></label></td>\n"
			+ "			<td style=\"display: none\" id=\"votedcell\" class=\"d-vo\" title=\"Shows a checkmark if you voted\"></td>\n"
			+ "			<td id=\"statuscell\" class=\"d-dr-missing d-dr-status\" title=\"Status: Missing\"></td>\n"
			+ "			<td style=\"display: none\" id=\"errcell\" class=\"d-st-okay\" title=\"Status Icon\"></td>\n"
			+ "			<td id=\"proposedcell\" title=\"Winning value\" class=\"d-win\" dir=\"ltr\" lang=\"aa\"><span class=\"s-flag-d\" title=\"Losing items may be flagged for CLDR Committee review.\">&nbsp; &nbsp;</span><div class=\"d-item\"><div class=\"choice-field\"><label title=\"\" class=\"btn btn-default\" data-original-title=\"Vote\"><input id=\"v4oaR4oaR4oaR__xgorkb\" class=\"ichoice-o\" type=\"radio\" title=\"click to vote\" value=\"↑↑↑\"></label><span class=\"subSpan\"><span class=\"fallback\" dir=\"ltr\" lang=\"aa\">latn</span></span><div class=\"i-star\" title=\"This mark shows on the item which was approved in the last release, if any.\"></div></div><div class=\"d-example well well-sm\" dir=\"ltr\" lang=\"aa\"><div class=\"cldr_example\">2345</div></div></div></td>\n"
			+ "			<td id=\"addcell\" title=\"Add another value\" class=\"d-win\"><form class=\"form-inline\"><div class=\"button-add form-group\"><button class=\"btn btn-primary\" title=\"\" type=\"submit\" data-original-title=\"Add\"><span class=\"glyphicon glyphicon-plus\"></span></button></div></form></td>\n"
			+ "			<td id=\"othercell\" title=\"Other non-winning items\" class=\"d-win\" dir=\"ltr\" lang=\"aa\"></td>\n"
			+ "		</tr></tbody>\n" + "</table>";

	static private String table2 = "<table id=\"vetting-table\" class=\"data table table-bordered vetting-page\">\n"
			+ "<thead>\n" + "<tr class=\"headingb active\">\n" + "<!--  see stui.js for the following -->\n"
			+ "    <th title=\"Code for this item\" id=\"null\">Code</th>\n"
			+ "	<th title=\"Comparison value\" id=\"null\">English</th>\n"
			+ "	<th title=\"Abstain from voting on this item\" id=\"null\" class=\"d-no\">Abstain</th>\n"
			+ "	<th title=\"Shows a checkmark if you voted\" id=\"null\" style=\"display: none\">Voting</th>\n"
			+ "	<th title=\"Approval Status\" id=\"null\" class=\"d-status\">A</th>\n"
			+ "	<th title=\"Status Icon\" id=\"null\" style=\"display: none\">Errors</th>\n"
			+ "	<th title=\"Winning value\" id=\"null\">Winning</th>\n"
			+ "	<th title=\"Add another value\" id=\"null\">Add</th><th title=\"Other non-winning items\" id=\"null\">Others</th>\n"
			+ "<!--	<th title=\"$flyoverchange\" id='stui-htmlchange' class='d-change'></th>-->\n" + "</tr>\n"
			+ "</thead>\n" + "<tbody><tr id=\"null\" class=\"undefined cov40\">\n"
			+ "	<th class=\"partsection\" colspan=\"9\">\n" + "		<a id=\"Numbering System\">Numbering System</a>\n"
			+ "	</th>\n" + "</tr><tr id=\"r@7b8ee7884f773afa\" class=\"vother cov40\">\n"
			+ "			<td id=\"codecell\" class=\"d-code\" title=\"Code for this item\"><span>default</span></td>\n"
			+ "			<td id=\"comparisoncell\" title=\"Comparison value\" class=\"d-disp\" dir=\"ltr\" lang=\"en-ZZ\"><span class=\"subSpan\">latn</span><div class=\"d-example well well-sm\" dir=\"ltr\" lang=\"en-ZZ\"><div class=\"cldr_example\">2345</div></div><div class=\"infos-code\"><img src=\"example.png\" alt=\"Example\"></div></td>\n"
			+ "			<td id=\"nocell\" title=\"undefined\" class=\"d-no-vo-true\"><label title=\"\" class=\"btn btn-default\" data-original-title=\"Vote\"><input id=\"NO__xauhj\" class=\"ichoice-o\" type=\"radio\" title=\"click to vote\" value=\"\"></label></td>\n"
			+ "			<td style=\"display: none\" id=\"votedcell\" class=\"d-vo\" title=\"Shows a checkmark if you voted\"></td>\n"
			+ "			<td id=\"statuscell\" class=\"d-dr-approved d-dr-status\" title=\"Status: Approved\"></td>\n"
			+ "			<td style=\"display: none\" id=\"errcell\" class=\"d-st-okay\" title=\"Status Icon\"></td>\n"
			+ "			<td id=\"proposedcell\" title=\"Winning value\" class=\"pu-select d-win\" dir=\"ltr\" lang=\"aa\"><span class=\"s-flag-d\" title=\"Losing items may be flagged for CLDR Committee review.\">&nbsp; &nbsp;</span><div class=\"d-item\"><div class=\"choice-field\"><label title=\"\" class=\"btn btn-default\" data-original-title=\"Vote\"><input id=\"vdGFtbA,,__xauhj\" class=\"ichoice-x\" type=\"radio\" title=\"click to vote\" value=\"taml\"></label><span class=\"subSpan\"><span class=\"winner\" dir=\"ltr\" lang=\"aa\">taml</span></span></div><div class=\"d-example well well-sm\" dir=\"ltr\" lang=\"aa\"><div class=\"cldr_example\">௨௲௩௱௪௰௫</div></div></div></td>\n"
			+ "			<td id=\"addcell\" title=\"Add another value\" class=\"d-win\"><form class=\"form-inline\"><div class=\"button-add form-group\"><button class=\"btn btn-primary\" title=\"\" type=\"submit\" data-original-title=\"Add\"><span class=\"glyphicon glyphicon-plus\"></span></button></div></form></td>\n"
			+ "			<td id=\"othercell\" title=\"Other non-winning items\" class=\"d-win\" dir=\"ltr\" lang=\"aa\"><div class=\"d-item\"><div class=\"choice-field\"><label title=\"\" class=\"btn btn-default\" data-original-title=\"Vote\"><input id=\"v4oaR4oaR4oaR__xauhj\" class=\"ichoice-o\" type=\"radio\" title=\"click to vote\" value=\"↑↑↑\"></label><span class=\"subSpan\"><span class=\"fallback\" dir=\"ltr\" lang=\"aa\">latn</span></span><div class=\"i-star\" title=\"This mark shows on the item which was approved in the last release, if any.\"></div></div><div class=\"d-example well well-sm\" dir=\"ltr\" lang=\"aa\"><div class=\"cldr_example\">2345</div></div></div><hr></td>\n"
			+ "		</tr><tr id=\"r@602dd39f06ddeb32\" class=\"vother cov40\">\n"
			+ "			<td id=\"codecell\" class=\"d-code\" title=\"Code for this item\"><span>native</span></td>\n"
			+ "			<td id=\"comparisoncell\" title=\"Comparison value\" class=\"d-disp\" dir=\"ltr\" lang=\"en-ZZ\"><span class=\"subSpan\">latn</span><div class=\"d-example well well-sm\" dir=\"ltr\" lang=\"en-ZZ\"><div class=\"cldr_example\">2345</div></div><div class=\"infos-code\"><img src=\"example.png\" alt=\"Example\"></div></td>\n"
			+ "			<td id=\"nocell\" title=\"undefined\" class=\"d-no-vo-false\"><label title=\"\" class=\"btn btn-default\" data-original-title=\"Vote\"><input id=\"NO__xgorkb\" class=\"ichoice-x\" type=\"radio\" title=\"click to vote\" value=\"\"></label></td>\n"
			+ "			<td style=\"display: none\" id=\"votedcell\" class=\"d-vo\" title=\"Shows a checkmark if you voted\"></td>\n"
			+ "			<td id=\"statuscell\" class=\"d-dr-missing d-dr-status\" title=\"Status: Missing\"></td>\n"
			+ "			<td style=\"display: none\" id=\"errcell\" class=\"d-st-okay\" title=\"Status Icon\"></td>\n"
			+ "			<td id=\"proposedcell\" title=\"Winning value\" class=\"d-win\" dir=\"ltr\" lang=\"aa\"><span class=\"s-flag-d\" title=\"Losing items may be flagged for CLDR Committee review.\">&nbsp; &nbsp;</span><div class=\"d-item\"><div class=\"choice-field\"><label title=\"\" class=\"btn btn-default\" data-original-title=\"Vote\"><input id=\"v4oaR4oaR4oaR__xgorkb\" class=\"ichoice-o\" type=\"radio\" title=\"click to vote\" value=\"↑↑↑\"></label><span class=\"subSpan\"><span class=\"fallback\" dir=\"ltr\" lang=\"aa\">latn</span></span><div class=\"i-star\" title=\"This mark shows on the item which was approved in the last release, if any.\"></div></div><div class=\"d-example well well-sm\" dir=\"ltr\" lang=\"aa\"><div class=\"cldr_example\">2345</div></div></div></td>\n"
			+ "			<td id=\"addcell\" title=\"Add another value\" class=\"d-win\"><form class=\"form-inline\"><div class=\"button-add form-group\"><button class=\"btn btn-primary\" title=\"\" type=\"submit\" data-original-title=\"Add\"><span class=\"glyphicon glyphicon-plus\"></span></button></div></form></td>\n"
			+ "			<td id=\"othercell\" title=\"Other non-winning items\" class=\"d-win\" dir=\"ltr\" lang=\"aa\"></td>\n"
			+ "		</tr></tbody>\n" + "</table>";
}
