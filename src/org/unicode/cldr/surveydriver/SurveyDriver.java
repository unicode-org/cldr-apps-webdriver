package org.unicode.cldr.surveydriver;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.logging.Logs;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Perform automated testing of the CLDR Survey Tool using Selenium WebDriver.
 * Reference: https://unicode.org/cldr/trac/ticket/11488
 *   "Implement new Survey Tool automated test framework and infrastructure"
 *
 * This test has been used with the survey-driver project running in Eclipse. At the same time,
 * cldr-apps can be running either on localhost (in the same Eclipse as survey-driver) or on SmokeTest.
 *
 * This code requires installing an implementation of WebDriver, such as chromedriver for Chrome.
 * On macOS, chromedriver can be installed from Terminal with brew as follows:
 *   brew tap homebrew/cask
 *   brew cask install chromedriver
 * -- or download chromedriver from https://chromedriver.storage.googleapis.com
 * (Testing with geckodriver for Firefox has been unsuccessful.)
 *
 * A tutorial for setting up a project using Selenium in Eclipse:
 *   https://www.guru99.com/selenium-tutorial.html
 */
public class SurveyDriver {
	/*
	 * Enable/disable specific tests using these booleans
	 */
	static final boolean TEST_FAST_VOTING = false;
	static final boolean TEST_LOCALES_AND_PAGES = false;
	static final boolean TEST_ANNOTATION_VOTING = true;

	/*
	 * Configure for Survey Tool server, which can be localhost, SmokeTest, or other
	 */
	static final String BASE_URL = "http://localhost:8080/cldr-apps/";
	// static final String BASE_URL = "http://cldr-smoke.unicode.org/smoketest/";

	/*
	 * Configure login, which may depend on BASE_URL.
	 * TODO: enable multiple distinct logins for the same server, so that each node in the grid can
	 * run as a different user. Probably should use configuration files instead of hard-coding here.
	 */
	static final String LOGIN_URL = "survey?letmein=pTFjaLECN&email=admin@";
	// static final String LOGIN_URL = "survey?email=hinarlinguist.wul7q2qkq@dbi4.utilika%20foundation.example.com&uid=2by_67IPy";

	static final long TIME_OUT_SECONDS = 30;
	static final long SLEEP_MILLISECONDS = 100;

	/*
	 * If USE_REMOTE_WEBDRIVER is true, then the driver will be a RemoteWebDriver (a class that implements
	 * the WebDriver interface). Otherwise the driver could be a ChromeDriver, or FirefoxDriver, EdgeDriver,
	 * or SafariDriver (all subclasses of RemoteWebDriver) if we add options for those.
	 * While much of the code in this class works either way, Selenium Grid needs the driver to be a
	 * RemoteWebDriver and requires installation of a hub and one or more nodes.
	 *
	 * Sample commands to start the grid (first node is default port 5555, second node explicit port 5556):
	 *
	 * HUB_URL=http://localhost:4444/grid/register java -jar selenium-server-standalone-3.9.1.jar -role hub
	 * HUB_URL=http://localhost:4444/grid/register java -jar selenium-server-standalone-3.9.1.jar -role node
	 * HUB_URL=http://localhost:4444/grid/register java -jar selenium-server-standalone-3.9.1.jar -role node -port 5556
	 */
	static final boolean USE_REMOTE_WEBDRIVER = false;

	WebDriver driver;
	WebDriverWait wait;

	public static void main(String[] args) {
		SurveyDriver s = new SurveyDriver();
		s.setUp();
		if (TEST_FAST_VOTING) {
			s.testFastVoting();
		}
		if (TEST_LOCALES_AND_PAGES) {
			s.testAllLocalesAndPages();
		}
		if (TEST_ANNOTATION_VOTING) {
			s.testAnnotationVoting();
		}
		s.tearDown();
	}

	/**
	 * Set up the driver and its "wait" object.
	 */
	private void setUp() {
		LoggingPreferences logPrefs = new LoggingPreferences();
		logPrefs.enable(LogType.BROWSER, Level.ALL);

		ChromeOptions options = new ChromeOptions();
		options.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);
		options.addArguments("start-maximized"); // doesn't work
		// options.addArguments("auto-open-devtools-for-tabs"); // this works, but makes window too narrow

		if (USE_REMOTE_WEBDRIVER) {
			try {
				driver = new RemoteWebDriver(new URL("http://localhost:4444/wd/hub"), options);
			} catch (MalformedURLException e) {
				System.out.println(e);
			}
			System.out.println("Session id = " + ((RemoteWebDriver) driver).getSessionId());
		} else {
			driver = new ChromeDriver(options);
			// driver.manage().window().maximize(); // doesn't work
		}
		wait = new WebDriverWait(driver, TIME_OUT_SECONDS, SLEEP_MILLISECONDS);
	}

	/**
	 * Clean up when finished testing.
	 */
	private void tearDown() {
		if (driver != null) {
			/*
			 * This five-second sleep may not always be appropriate. It can help to see the browser for a few seconds
			 * before it closes. Alternatively a breakpoint can be set on driver.quit() for the same purpose.
			 */
			try {
				Thread.sleep(5000);
			} catch (Exception e) {
				System.out.println("Sleep interrupted before driver.quit; " + e);
			}
			driver.quit();
		}
	}

	/**
	 * Test "fast" voting, that is, voting for several items on a page, and measuring
	 * the time of response.
	 *
	 * Purposes:
	 * (1) study the sequence of events, especially client-server traffic,
	 * when multiple voting events (maybe by a single user) are being handled;
	 * (2) simulate simultaneous input from multiple vetters, for integration and
	 * performance testing under high load.
	 *
	 * References:
	 * https://cldr.unicode.org/index/cldr-engineer/sow "Performance Improvement Goals"
	 * https://unicode.org/cldr/trac/ticket/11211 "Performance is slow when voting on multiple items on a page"
	 * https://unicode.org/cldr/trac/ticket/10990 "Fix synchronize (threading)"
	 */
	private boolean testFastVoting() {
		if (!login()) {
			return false;
		}
		/*
		 * TODO: configure the locale and page on a per-node basis, to enable multiple simulated
		 * users to be voting in multiple locales and/or pages. 
		 */
		String loc = "ar";
		// String loc = "en_CA";
		String page = "Languages_A_D";
		String url = BASE_URL + "v#/" + loc + "/" + page;

		/*
		 * Repeat the test for a minute or so.
		 * Eventually we'll have more sophisticated criteria for when to stop.
		 * This loop to 1000 isn't set in stone.
		 */
		for (int i = 0; i < 1000; i++) {
			try {
				if (!testFastVotingInner(page, url)) {
					return false;
				}
			} catch (StaleElementReferenceException e) {
				/*
				 * Sometimes we get "org.openqa.selenium.StaleElementReferenceException:
				 * stale element reference: element is not attached to the page document".
				 * Presumably this happens due to ajax response causing the table to be rebuilt.
				 * TODO: catch this exception and continue wherever it occurs. Ideally also survey.js
				 * may be revised to update the table in place when possible instead of rebuilding the
				 * table from scratch so often. Reference: https://unicode.org/cldr/trac/ticket/11571
				 */
				System.out.println("Continuing main loop after StaleElementReferenceException, i = " + i);
				continue;
			}
		}
		System.out.println("✅ Fast vote test passed for " + loc + ", " + page);
		return true;
	}

	private boolean testFastVotingInner(String page, String url) {
		driver.get(url);
		/*
		 * Wait for the correct title, and then wait for the div
		 * whose id is "LoadingMessageSection" to get the style "display: none".
		 */
		if (!waitForTitle(page, url)) {
			return false;
		}
		if (!waitUntilLoadingMessageDone(url)) {
			return false;
		}
		if (!hideLeftSidebar(url)) {
			return false;
		}
		if (!waitUntilElementInactive("left-sidebar", url)) {
			return false;
		}
		/*
		 * TODO: handle "overlay" element more robustly. While this mostly works, the overlay can
		 * pop up again when you least expect it, causing, for example:
		 *
		 * org.openqa.selenium.WebDriverException: unknown error: Element <input ...
		 * title="click to vote" value="الأدانجمية"> is not clickable at point (746, 429).
		 * Other element would receive the click: <div id="overlay" class="" style="z-index: 1000;"></div>
		 */
		if (!waitUntilElementInactive("overlay", url)) {
			return false;
		}
		double firstClickTime = 0;

		/*
		 * For the first four rows, click on the Abstain (nocell) button.
		 * Then, for the first three rows, click on the Winning (proposedcell) button.
		 * Then, for the fourth row, click on the Add (addcell) button and enter a new value.
		 * TODO: instead of hard-coding these hexadecimal row ids, specify the first four
		 * "tr" elements of the main table using the appropriate findElement(By...).
		 */
		String[] rowIds = { "f3d4397b739b287", "6899b21f19eef8cc", "7d1d3cbd260601a4", "1bb330c3cf8572aa" };
		String[] cellIds = { "nocell", "proposedcell" };
		for (String cell : cellIds) {
			for (int i = 0; i < rowIds.length; i++) {
				String rowId = "r@" + rowIds[i];
				boolean doAdd = (i == rowIds.length - 1) && cell.equals("proposedcell");
				String cellId = doAdd ? "addcell" : cell;
				WebElement rowEl = null, columnEl = null, clickEl = null;
				try {
					rowEl = driver.findElement(By.id(rowId));
				} catch (Exception e) {
					System.out.println(e);
				}
				if (rowEl == null) {
					System.out.println("❌ Fast vote test failed, missing row id " + rowId + " for " + url);
					return false;
				}
				try {
					columnEl = rowEl.findElement(By.id(cellId));
				} catch (Exception e) {
					System.out.println(e);
				}
				if (columnEl == null) {
					System.out
							.println("❌ Fast vote test failed, no column " + cellId + " for row " + rowId + " for "
									+ url);
					return false;
				}
				String tagName = doAdd ? "button" : "input";
				try {
					clickEl = columnEl.findElement(By.tagName(tagName));
				} catch (StaleElementReferenceException e) {
					System.out.println("Continuing after StaleElementReferenceException for findElement by tagName "
							+ rowId + " for " + url);
					continue;
				} catch (Exception e) {
					System.out.println(e);
				}
				if (clickEl == null) {
					System.out.println(
							"❌ Fast vote test failed, no tag " + tagName + " for row " + rowId + " for " + url);
					return false;
				}
				if (!waitUntilElementClickable(clickEl, url)) {
					return false;
				}
				try {
					wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("overlay")));
					if (firstClickTime == 0.0) {
						firstClickTime = System.currentTimeMillis();
					}
					clickEl.click();
				} catch (StaleElementReferenceException e) {
					/*
					 * Sometimes we get "org.openqa.selenium.StaleElementReferenceException:
					 * stale element reference: element is not attached to the page document".
					 * Presumably this happens due to ajax response causing the table to be rebuilt.
					 */
					System.out.println("Continuing after StaleElementReferenceException for clickEl.click for row "
							+ rowId + " for " + url);
					continue;
				} catch (Exception e) {
					System.out.println(e);
					System.out.println("❌ Fast vote test failed, clickEl.click for row " + rowId + " for " + url);
					return false;
				}
				if (doAdd) {
					WebElement inputEl = waitInputBoxAppears(rowEl, url);
					if (inputEl == null) {
						System.out.println("❌ Fast vote test failed, didn't see input box for " + url);
						return false;
					}
					if (!waitUntilElementClickable(inputEl, url)) {
						return false;
					}
					inputEl.clear();
					inputEl.click();
					inputEl.sendKeys("Testxyz");
					inputEl.sendKeys(Keys.RETURN);
				}
			}
		}
		/*
		 * TODO: Wait for tr_checking2 element (temporary green background) to exist.
		 * Problem: sometimes server is too fast and/or our polling isn't frequent enough,
		 * and then the tr_checking2 element(s) are already gone before we get here.
		 * For now, skip this call.
		 */
		if (false && !waitUntilClassChecking(true, url)) {
			return false;
		}
		/*
		 * Wait for tr_checking2 element (temporary green background) NOT to exist.
		 * The temporary green background indicates that the client is waiting for a response
		 * from the server before it can update the display to show the results of a
		 * completed voting operation.
		 */
		if (!waitUntilClassChecking(false, url)) {
			return false;
		}
		double deltaTime = System.currentTimeMillis() - firstClickTime;
		System.out.println("Total time elapsed since first click = " + deltaTime / 1000.0 + " sec");
		return true;
	}

	/**
	 * Log into Survey Tool.
	 */
	private boolean login() {
		String url = BASE_URL + LOGIN_URL;
		driver.get(url);

		/*
		 * Wait for the correct title, and then wait for the div
		 * whose id is "LoadingMessageSection" to get the style "display: none".
		 */
		String page = "Locale List";
		if (!waitForTitle(page, url)) {
			return false;
		}
		if (!waitUntilLoadingMessageDone(url)) {
			return false;
		}
		return true;
	}

	/**
	 * Test all the locales and pages we're interested in.
	 *
	 * TODO: use a complete list of locales (or at least sublocales), and a complete
	 * list of pages. For now, only a few locales and pages are tested,
	 * including some that have been known to exhibit a bug where the browser
	 * console displays an error message, "INHERITANCE_MARKER without inheritedValue".
	 *
	 * There is a PageId enum defined in PathHeader.java. We could link with the
	 * cldr-apps code and access that enum directly. However, there are difficulties
	 * with initiation, like "java.lang.RuntimeException: CLDRConfigImpl used before SurveyMain.init() called!"
	 * "Set -DCLDR_ENVIRONMENT=UNITTEST if you are in the test cases." Follow up on that possibility later.
	 * In the meantime, we can copy and simplify the enum from PathHeader.java, since all we need here
	 * is an array of strings.
	 *
	 * PageId versus SectionId: PageId.Alphabetic_Information is in the section SectionId.Core_Data
	 *
	 * Alphabetic_Information(SectionId.Core_Data, "Alphabetic Information")
	 *
	 * Each page is one web page; a section may encompass multiple pages, not all visible at once.
	 * (There may also be multiple headers in one page. See PathHeader.txt which is a source file.)
	 */
	private void testAllLocalesAndPages() {
		String[] locales = { "pt_PT", "en", "en_CA", "fa_AF", "sr_Cyrl_BA" };
		/* This list of page names was created by temporarily commenting out the toString function
		 * in PathHeader.java, and inserting this line into the PageId initialization function:
		 * System.out.println("PageId raw name: " + this.toString());
		 */
		String[] pages = {"Alphabetic_Information",
			"Numbering_Systems",
			"Locale_Name_Patterns",
			"Languages_A_D",
			"Languages_E_J",
			"Languages_K_N",
			"Languages_O_S",
			"Languages_T_Z",
			"Scripts",
			"Territories",
			"T_NAmerica",
			"T_SAmerica",
			"T_Africa",
			"T_Europe",
			"T_Asia",
			"T_Oceania",
			"Locale_Variants",
			"Keys",
			"Fields",
			"Gregorian",
			"Generic",
			"Buddhist",
			"Chinese",
			"Coptic",
			"Dangi",
			"Ethiopic",
			"Ethiopic_Amete_Alem",
			"Hebrew",
			"Indian",
			"Islamic",
			"Japanese",
			"Persian",
			"Minguo",
			"Timezone_Display_Patterns",
			"NAmerica",
			"SAmerica",
			"Africa",
			"Europe",
			"Russia",
			"WAsia",
			"CAsia",
			"EAsia",
			"SAsia",
			"SEAsia",
			"Australasia",
			"Antarctica",
			"Oceania",
			"UnknownT",
			"Overrides",
			"Symbols",
			"MinimalPairs",
			"Number_Formatting_Patterns",
			"Compact_Decimal_Formatting",
			"Compact_Decimal_Formatting_Other",
			"Measurement_Systems",
			"Duration",
			"Length",
			"Area",
			"Volume",
			"SpeedAcceleration",
			"MassWeight",
			"EnergyPower",
			"ElectricalFrequency",
			"Weather",
			"Digital",
			"Coordinates",
			"OtherUnits",
			"CompoundUnits",
			"Displaying_Lists",
			"LinguisticElements",
			"Transforms",
				// "Identity", // what is "Identity"? Leads to error message.
				// "Version", // ditto
				// "Suppress",
				// "Deprecated",
				// "Unknown",
			"C_NAmerica",
			"C_SAmerica",
			"C_NWEurope",
			"C_SEEurope",
			"C_NAfrica",
			"C_WAfrica",
			"C_MAfrica",
			"C_EAfrica",
			"C_SAfrica",
			"C_WAsia",
			"C_CAsia",
			"C_EAsia",
			"C_SAsia",
			"C_SEAsia",
			"C_Oceania",
			"C_Unknown",
			"u_Extension",
			"t_Extension",
			"Alias",
			"IdValidity",
			"Locale",
			"RegionMapping",
			"WZoneMapping",
			"Transform",
			"UnitPreferences",
			"Likely",
			"LanguageMatch",
			"TerritoryInfo",
			"LanguageInfo",
			"LanguageGroup",
			"Fallback",
			"Gender",
			"Metazone",
			"NumberSystem",
			"Plural",
			"PluralRange",
			"Containment",
			"Currency",
			"Calendar",
			"WeekData",
			"Measurement",
			"Language",
			"RBNF",
			"Segmentation",
			"DayPeriod",
			"Category",
			"Smileys",
			"People",
			"Animals_Nature",
			"Food_Drink",
			"Travel_Places",
			"Activities",
			"Objects",
			"Symbols2",
			"Flags",
			"Component",
				"Typography" };

		/*
		 * Reference: https://unicode.org/cldr/trac/ticket/11238 "browser console shows error message,
		 * there is INHERITANCE_MARKER without inheritedValue"
		 */
		String searchString = "INHERITANCE_MARKER without inheritedValue"; // formerly, "there is no Bailey Target item"

		for (String loc : locales) {
			// for (PathHeader.PageId page : PathHeader.PageId.values()) {
			for (String page : pages) {
				if (!testOneLocationAndPage(loc, page, searchString)) {
					return;
				}
			}
		}
	}

	/**
	 * Test the given locale and page.
	 *
	 * @param loc
	 *            the locale string, like "pt_PT"
	 * @param page
	 *            the page name, like "Alphabetic_Information"
	 * @return true if all parts of the test pass, else false
	 */
	private boolean testOneLocationAndPage(String loc, String page, String searchString) {
		String url = BASE_URL + "v#/" + loc + "/" + page;
		driver.get(url);

		/*
		 * Wait for the correct title, and then wait for the div
		 * whose id is "LoadingMessageSection" to get the style "display: none".
		 */
		if (!waitForTitle(page, url)) {
			return false;
		}
		if (!waitUntilLoadingMessageDone(url)) {
			return false;
		}
		int searchStringCount = countLogEntriesContainingString(searchString);
		if (searchStringCount > 0) {
			System.out.println("❌ Test failed: " + searchStringCount + " occurrences in log of \'" + searchString
					+ "\' for " + url);
			return false;
		}
		System.out.println(
				"✅ Test passed: zero occurrences in log of \'" + searchString + "\' for " + loc + ", " + page);
		return true;
	}

	private void testAnnotationVoting() {
		/*
		 * This list of locales was obtained by putting a breakpoint on SurveyAjax.getLocalesSet, getting its
		 * return value, and adding quotation marks by search/replace.
		 */
		String[] locales = {
				/* "aa", "aa_DJ", "aa_ER", "aa_ET", "af", "af_NA", "af_ZA", "agq", "agq_CM", "ak", "ak_GH",
				"am", "am_ET", "ar", "ar_001", "ar_AE", "ar_BH", "ar_DJ", "ar_DZ", "ar_EG", "ar_EH", "ar_ER", "ar_IL",
				"ar_IQ", "ar_JO", "ar_KM", "ar_KW", "ar_LB", "ar_LY", "ar_MA", "ar_MR", "ar_OM", "ar_PS", "ar_QA",
				"ar_SA", "ar_SD", "ar_SO", "ar_SS", "ar_SY", "ar_TD", "ar_TN", "ar_YE", "arn", "arn_CL", "as", "as_IN",
				"asa", "asa_TZ", "ast", "ast_ES", "az", "az_Arab", "az_Arab_IQ", "az_Arab_IR", "az_Arab_TR", "az_Cyrl",
				"az_Cyrl_AZ", "az_Latn", "az_Latn_AZ", "ba", "ba_RU", "bas", "bas_CM", "be", "be_BY", "bem", "bem_ZM",
				"bez", "bez_TZ", "bg", "bg_BG", "bgn", "bgn_AE", "bgn_AF", "bgn_IR", "bgn_OM", "bgn_PK", "blt",
				"blt_VN", "bm", "bm_ML", "bm_Nkoo", "bm_Nkoo_ML", "bn", "bn_BD", "bn_IN", "bo", "bo_CN", "bo_IN", "br",
				"br_FR", "brx", "brx_IN", "bs", "bs_Cyrl", "bs_Cyrl_BA", "bs_Latn", "bs_Latn_BA", "bss", "bss_CM",
				"byn", "byn_ER", "ca", "ca_AD", "ca_ES", "ca_ES_VALENCIA", "ca_FR", "ca_IT", "cch", "cch_NG", "ccp",
				"ccp_BD", "ccp_IN", "ce", "ce_RU", "ceb", "cgg", "cgg_UG", "chr", "chr_US", "ckb", "ckb_IQ", "ckb_IR",
				"co", "co_FR", "cs", "cs_CZ", "cu", "cu_RU", "cv", "cv_RU", "cy", "cy_GB", "da", "da_DK", "da_GL",
				"dav", "dav_KE", "de", "de_AT", "de_BE", "de_CH", "de_DE", "de_IT", "de_LI", "de_LU", "dje", "dje_NE",
				"dsb", "dsb_DE", "dua", "dua_CM", "dv", "dv_MV", "dyo", "dyo_SN", "dz", "dz_BT", "ebu", "ebu_KE", "ee",
				"ee_GH", "ee_TG", "el", "el_CY", "el_GR", "el_POLYTON", "en", "en_001", "en_150", "en_AG", "en_AI",
				"en_AS", "en_AT", "en_AU", "en_BB", "en_BE", "en_BI", "en_BM", "en_BS", "en_BW", "en_BZ", "en_CA",
				"en_CC", "en_CH", "en_CK", "en_CM", "en_CX", "en_CY", "en_DE", "en_DG", "en_DK", "en_DM", "en_Dsrt",
				"en_Dsrt_US", "en_ER", "en_FI", "en_FJ", "en_FK", "en_FM", "en_GB", "en_GD", "en_GG", "en_GH", "en_GI",
				"en_GM", "en_GU", "en_GY", "en_HK", "en_IE", "en_IL", "en_IM", "en_IN", "en_IO", "en_JE", "en_JM",
				"en_KE", "en_KI", "en_KN", "en_KY", "en_LC", "en_LR", "en_LS", "en_MG", "en_MH", "en_MO", "en_MP",
				"en_MS", "en_MT", "en_MU", "en_MW", "en_MY", "en_NA", "en_NF", "en_NG", "en_NL", "en_NR", "en_NU",
				"en_NZ", "en_PG", "en_PH", "en_PK", "en_PN", "en_PR", "en_PW", "en_RW", "en_SB", */ "en_SC", "en_SD",
				"en_SE", "en_SG", "en_SH", "en_SI", "en_SL", "en_SS", "en_SX", "en_SZ", "en_Shaw", "en_TC", "en_TK",
				"en_TO", "en_TT", "en_TV", "en_TZ", "en_UG", "en_UM", "en_US", "en_US_POSIX", "en_VC", "en_VG", "en_VI",
				"en_VU", "en_WS", "en_ZA", "en_ZM", "en_ZW", "en_ZZ", "eo", "eo_001", "es", "es_419", "es_AR", "es_BO",
				"es_BR", "es_BZ", "es_CL", "es_CO", "es_CR", "es_CU", "es_DO", "es_EA", "es_EC", "es_ES", "es_GQ",
				"es_GT", "es_HN", "es_IC", "es_MX", "es_NI", "es_PA", "es_PE", "es_PH", "es_PR", "es_PY", "es_SV",
				"es_US", "es_UY", "es_VE", "et", "et_EE", "eu", "eu_ES", "ewo", "ewo_CM", "fa", "fa_AF", "fa_IR", "ff",
				"ff_Adlm", "ff_Adlm_BF", "ff_Adlm_CM", "ff_Adlm_GH", "ff_Adlm_GM", "ff_Adlm_GN", "ff_Adlm_GW",
				"ff_Adlm_LR", "ff_Adlm_MR", "ff_Adlm_NE", "ff_Adlm_NG", "ff_Adlm_SL", "ff_Adlm_SN", "ff_Latn",
				"ff_Latn_BF", "ff_Latn_CM", "ff_Latn_GH", "ff_Latn_GM", "ff_Latn_GN", "ff_Latn_GW", "ff_Latn_LR",
				"ff_Latn_MR", "ff_Latn_NE", "ff_Latn_NG", "ff_Latn_SL", "ff_Latn_SN", "fi", "fi_FI", "fil", "fil_PH",
				"fo", "fo_DK", "fo_FO", "fr", "fr_BE", "fr_BF", "fr_BI", "fr_BJ", "fr_BL", "fr_CA", "fr_CD", "fr_CF",
				"fr_CG", "fr_CH", "fr_CI", "fr_CM", "fr_DJ", "fr_DZ", "fr_FR", "fr_GA", "fr_GF", "fr_GN", "fr_GP",
				"fr_GQ", "fr_HT", "fr_KM", "fr_LU", "fr_MA", "fr_MC", "fr_MF", "fr_MG", "fr_ML", "fr_MQ", "fr_MR",
				"fr_MU", "fr_NC", "fr_NE", "fr_PF", "fr_PM", "fr_RE", "fr_RW", "fr_SC", "fr_SN", "fr_SY", "fr_TD",
				"fr_TG", "fr_TN", "fr_VU", "fr_WF", "fr_YT", "fur", "fur_IT", "fy", "fy_NL", "ga", "ga_IE", "gaa",
				"gaa_GH", "gd", "gd_GB", "gez", "gez_ER", "gez_ET", "gl", "gl_ES", "gn", "gn_PY", "gsw", "gsw_CH",
				"gsw_FR", "gsw_LI", "gu", "gu_IN", "guz", "guz_KE", "gv", "gv_IM", "ha", "ha_Arab", "ha_Arab_NG",
				"ha_Arab_SD", "ha_GH", "ha_NE", "ha_NG", "haw", "haw_US", "he", "he_IL", "hi", "hi_IN", "hr", "hr_BA",
				"hr_HR", "hsb", "hsb_DE", "hu", "hu_HU", "hy", "hy_AM", "ia", "ia_001", "id", "id_ID", "ig", "ig_NG",
				"ii", "ii_CN", "io", "io_001", "is", "is_IS", "it", "it_CH", "it_IT", "it_SM", "it_VA", "iu", "iu_CA",
				"iu_Latn", "iu_Latn_CA", "ja", "ja_JP", "jbo", "jbo_001", "jgo", "jgo_CM", "jmc", "jmc_TZ", "jv",
				"jv_ID", "ka", "ka_GE", "kab", "kab_DZ", "kaj", "kaj_NG", "kam", "kam_KE", "kcg", "kcg_NG", "kde",
				"kde_TZ", "kea", "kea_CV", "ken", "ken_CM", "khq", "khq_ML", "ki", "ki_KE", "kk", "kk_KZ", "kkj",
				"kkj_CM", "kl", "kl_GL", "kln", "kln_KE", "km", "km_KH", "kn", "kn_IN", "ko", "ko_KP", "ko_KR", "kok",
				"kok_IN", "kpe", "kpe_GN", "kpe_LR", "ks", "ks_IN", "ksb", "ksb_TZ", "ksf", "ksf_CM", "ksh", "ksh_DE",
				"ku", "ku_TR", "kw", "kw_GB", "ky", "ky_KG", "lag", "lag_TZ", "lb", "lb_LU", "lg", "lg_UG", "lkt",
				"lkt_US", "ln", "ln_AO", "ln_CD", "ln_CF", "ln_CG", "lo", "lo_LA", "lrc", "lrc_IQ", "lrc_IR", "lt",
				"lt_LT", "lu", "lu_CD", "luo", "luo_KE", "luy", "luy_KE", "lv", "lv_LV", "mas", "mas_KE", "mas_TZ",
				"mer", "mer_KE", "mfe", "mfe_MU", "mg", "mg_MG", "mgh", "mgh_MZ", "mgo", "mgo_CM", "mi", "mi_NZ", "mk",
				"mk_MK", "ml", "ml_IN", "mn", "mn_MN", "mn_Mong", "mn_Mong_CN", "mn_Mong_MN", "mni", "mni_IN", "moh",
				"moh_CA", "mr", "mr_IN", "ms", "ms_Arab", "ms_Arab_BN", "ms_Arab_MY", "ms_BN", "ms_MY", "ms_SG", "mt",
				"mt_MT", "mua", "mua_CM", "my", "my_MM", "myv", "myv_RU", "mzn", "mzn_IR", "naq", "naq_NA", "nb",
				"nb_NO", "nb_SJ", "nd", "nd_ZW", "nds", "nds_DE", "nds_NL", "ne", "ne_IN", "ne_NP", "nl", "nl_AW",
				"nl_BE", "nl_BQ", "nl_CW", "nl_NL", "nl_SR", "nl_SX", "nmg", "nmg_CM", "nn", "nn_NO", "nnh", "nnh_CM",
				"nqo", "nqo_GN", "nr", "nr_ZA", "nso", "nso_ZA", "nus", "nus_SS", "ny", "ny_MW", "nyn", "nyn_UG", "oc",
				"oc_FR", "om", "om_ET", "om_KE", "or", "or_IN", "os", "os_GE", "os_RU", "pa", "pa_Arab", "pa_Arab_PK",
				"pa_Guru", "pa_Guru_IN", "pl", "pl_PL", "prg", "prg_001", "ps", "ps_AF", "ps_PK", "pt", "pt_AO",
				"pt_BR", "pt_CH", "pt_CV", "pt_GQ", "pt_GW", "pt_LU", "pt_MO", "pt_MZ", "pt_PT", "pt_ST", "pt_TL", "qu",
				"qu_BO", "qu_EC", "qu_PE", "quc", "quc_GT", "rm", "rm_CH", "rn", "rn_BI", "ro", "ro_MD", "ro_RO", "rof",
				"rof_TZ", "root", "ru", "ru_BY", "ru_KG", "ru_KZ", "ru_MD", "ru_RU", "ru_UA", "rw", "rw_RW", "rwk",
				"rwk_TZ", "sa", "sa_IN", "sah", "sah_RU", "saq", "saq_KE", "sbp", "sbp_TZ", "sc", "sc_IT", "scn",
				"scn_IT", "sd", "sd_PK", "sdh", "sdh_IQ", "sdh_IR", "se", "se_FI", "se_NO", "se_SE", "seh", "seh_MZ",
				"ses", "ses_ML", "sg", "sg_CF", "shi", "shi_Latn", "shi_Latn_MA", "shi_Tfng", "shi_Tfng_MA", "si",
				"si_LK", "sid", "sid_ET", "sk", "sk_SK", "sl", "sl_SI", "sma", "sma_NO", "sma_SE", "smj", "smj_NO",
				"smj_SE", "smn", "smn_FI", "sms", "sms_FI", "sn", "sn_ZW", "so", "so_DJ", "so_ET", "so_KE", "so_SO",
				"sq", "sq_AL", "sq_MK", "sq_XK", "sr", "sr_Cyrl", "sr_Cyrl_BA", "sr_Cyrl_ME", "sr_Cyrl_RS",
				"sr_Cyrl_XK", "sr_Latn", "sr_Latn_BA", "sr_Latn_ME", "sr_Latn_RS", "sr_Latn_XK", "ss", "ss_SZ", "ss_ZA",
				"ssy", "ssy_ER", "st", "st_LS", "st_ZA", "sv", "sv_AX", "sv_FI", "sv_SE", "sw", "sw_CD", "sw_KE",
				"sw_TZ", "sw_UG", "syr", "syr_IQ", "syr_SY", "ta", "ta_IN", "ta_LK", "ta_MY", "ta_SG", "te", "te_IN",
				"teo", "teo_KE", "teo_UG", "tg", "tg_TJ", "th", "th_TH", "ti", "ti_ER", "ti_ET", "tig", "tig_ER", "tk",
				"tk_TM", "tn", "tn_BW", "tn_ZA", "to", "to_TO", "tr", "tr_CY", "tr_TR", "trv", "trv_TW", "ts", "ts_ZA",
				"tt", "tt_RU", "twq", "twq_NE", "tzm", "tzm_MA", "ug", "ug_CN", "uk", "uk_UA", "und", "und_ZZ", "ur",
				"ur_IN", "ur_PK", "uz", "uz_Arab", "uz_Arab_AF", "uz_Cyrl", "uz_Cyrl_UZ", "uz_Latn", "uz_Latn_UZ",
				"vai", "vai_Latn", "vai_Latn_LR", "vai_Vaii", "vai_Vaii_LR", "ve", "ve_ZA", "vi", "vi_VN", "vo",
				"vo_001", "vun", "vun_TZ", "wa", "wa_BE", "wae", "wae_CH", "wal", "wal_ET", "wbp", "wbp_AU", "wo",
				"wo_SN", "xh", "xh_ZA", "xog", "xog_UG", "yav", "yav_CM", "yi", "yi_001", "yo", "yo_BJ", "yo_NG", "yue",
				"yue_Hans", "yue_Hans_CN", "yue_Hant", "yue_Hant_HK", "zgh", "zgh_MA", "zh", "zh_Hans", "zh_Hans_CN",
				"zh_Hans_HK", "zh_Hans_MO", "zh_Hans_SG", "zh_Hant", "zh_Hant_HK", "zh_Hant_MO", "zh_Hant_TW", "zu",
				"zu_ZA" };
		String[] pages = { "Category", "Smileys", "People", "Animals_Nature", "Food_Drink", "Travel_Places",
				"Activities", "Objects", "Symbols2", "Flags" };

		/*
		 * Reference: https://unicode.org/cldr/trac/ticket/11270
		 * "Use floating point instead of integers for vote counts"
		 * See VoteResolver.java
		 */
		String searchString = "Rounding matters for useKeywordAnnotationVoting";

		for (String loc : locales) {
			// for (PathHeader.PageId page : PathHeader.PageId.values()) {
			for (String page : pages) {
				if (!testOneLocationAndPage(loc, page, searchString)) {
					return;
				}
			}
		}
	}

	/**
	 * Count how many log entries contain the given string.
	 *
	 * @param searchString
	 *            the string for which to search
	 * @return the number of occurrences
	 */
	private int countLogEntriesContainingString(String searchString) {
		int searchStringCount = 0;
		Logs logs = driver.manage().logs();
		for (String type : logs.getAvailableLogTypes()) {
			List<LogEntry> logEntries = logs.get(type).getAll();
			for (LogEntry entry : logEntries) {
				String message = entry.getMessage();
				if (message.contains(searchString)) {
					System.out.println(entry);
					++searchStringCount;
				}
			}
		}
		return searchStringCount;
	}

	/**
	 * Wait for the title to contain the given string
	 *
	 * @param s the string expected to occur in the title
	 * @param url the url we're loading
	 * @return true for success, false for failure
	 */
	private boolean waitForTitle(String s, String url) {
		try {
			wait.until(new ExpectedCondition<Boolean>() {
				@Override
				public Boolean apply(WebDriver webDriver) {
					return (webDriver.getTitle().contains(s));
				}
			});
		} catch (Exception e) {
			System.out.println(e);
			System.out.println("❌ Test failed, maybe timed out, waiting for title to contain " + s + " in " + url);
			return false;
		}
		return true;
	}

	/**
	 * Wait for the div whose id is "LoadingMessageSection" to get the style "display: none".
	 *
	 * @param url the url we're loading
	 * @return true for success, false for failure
	 */
	private boolean waitUntilLoadingMessageDone(String url) {
		String loadingId = "LoadingMessageSection";
		try {
			wait.until(new ExpectedCondition<Boolean>() {
				@Override
				public Boolean apply(WebDriver webDriver) {
					return webDriver.findElement(By.id(loadingId)).getCssValue("display").contains("none");
				}
			});
		} catch (Exception e) {
			System.out.println(e);
			System.out.println("❌ Test failed, maybe timed out, waiting for " + loadingId + " in " + url);
			return false;
		}
		return true;
	}

	/**
	 * Hide the element whose id is "left-sidebar", by simulating the appropriate mouse action if it's
	 * visible.
	 *
	 * @param url the url we're loading
	 * @return true for success, false for failure
	 */
	private boolean hideLeftSidebar(String url) {
		String id = "left-sidebar";
		WebElement bar = driver.findElement(By.id(id));
		if (bar.getAttribute("class").contains("active")) {
			/*
			 * Move the mouse away from the left sidebar.
			 * Mouse movements don't seem able to hide the left sidebar with WebDriver.
			 * Even clicks on other elements don't work.
			 * The only solution I've found is to add this line to redesign.js:
			 *     $('#dragger').click(hideOverlayAndSidebar);
			 * Then clicking on the edge of the sidebar closes it, and simulated click here works.
			 */
			// System.out.println("Moving mouse, so to squeak...");
			String otherId = "dragger";
			WebElement otherEl = driver.findElement(By.id(otherId));
			if (!waitUntilElementClickable(otherEl, url)) {
				return false;
			}
			try {
				otherEl.click();
			} catch (Exception e) {
				System.out.println("Exception caught while moving mouse, so to squeak...");
				System.out.println(e);
				return false;
			}
			/*
			 * With latest redesign.js on localhost, the above click triggers hideOverlayAndSidebar.
			 * With older code still on SmokeTest, however, that doesn't work, in which case the following
			 * executeScript works, though it's "cheating" since it doesn't simulate a GUI interaction.
			 */
			JavascriptExecutor js = (JavascriptExecutor) driver;
			js.executeScript("hideOverlayAndSidebar()");
		}
		return true;
	}

	/**
	 * Wait for the element with given id not to have class "active".
	 *
	 * @param id the id of the element
	 * @param url the url we're loading
	 * @return true for success, false for failure
	 */
	private boolean waitUntilElementInactive(String id, String url) {
		try {
			wait.until(new ExpectedCondition<Boolean>() {
				@Override
				public Boolean apply(WebDriver webDriver) {
					WebElement el = webDriver.findElement(By.id(id));
					return el == null || !el.getAttribute("class").contains("active");
				}
			});
		} catch (Exception e) {
			System.out.println(e);
			System.out.println("❌ Test failed, maybe timed out, waiting for " + id + " in " + url);
			return false;
		}
		return true;
	}

	/**
	 * Wait for an input element to appear inside the addcell in the given row element
	 *
	 * @param rowEl the row element that will contain the input element
	 * @param url the url we're loading
	 * @return the input element, or null for failure
	 */
	private WebElement waitInputBoxAppears(WebElement rowEl, String url) {
		/*
		 * Caution: a row may have more than one input element -- for example, the "radio" buttons are input elements.
		 * First we need to find the addcell for this row, then find the input tag inside the addcell.
		 * Note that the add button does NOT contain the input tag, but the addcell contains both the add button
		 * and the input tag.
		 */
		WebElement addCell = rowEl.findElement(By.id("addcell"));
		WebElement inputEl = null;
		try {
			inputEl = wait.until(ExpectedConditions.presenceOfNestedElementLocatedBy(addCell, By.tagName("input")));
		} catch (Exception e) {
			System.out.println(e);
			System.out.println("❌ Test failed, maybe timed out, waiting for input in addcell in " + url);
		}
		return inputEl;
	}

	/**
	 * Wait until the element is clickable.
	 *
	 * @param el the element
	 * @param url the url we're loading
	 * @return true for success, false for failure
	 */
	private boolean waitUntilElementClickable(WebElement el, String url) {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(el));
		} catch (Exception e) {
			System.out.println(e);
			System.out.println("❌ Test failed, maybe timed out, waiting for " + el + " to be clickable in " + url);
			return false;
		}
		return true;
	}

	/**
	 * Wait until an element with class "tr_checking2" exists, or wait until one doesn't.
	 *
	 * @param checking true to wait until such an element exists, or false to wait until no such element exists
	 * @param url the url we're loading
	 * @return true for success, false for failure
	 */
	private boolean waitUntilClassChecking(boolean checking, String url) {
		String className = "tr_checking2";
		try {
			wait.until(new ExpectedCondition<Boolean>() {
				@Override
				public Boolean apply(WebDriver webDriver) {
					// WebElement el = webDriver.findElement(By.className(className));
					// return checking ? (el != null) : (el == null);
					int elCount = webDriver.findElements(By.className(className)).size();
					return checking ? (elCount > 0) : (elCount == 0);
				}
			});
		} catch (Exception e) {
			System.out.println(e);
			System.out.println("❌ Test failed, maybe timed out, waiting for class " + className
					+ (checking ? "" : " not") + " to exist for " + url);
			return false;
		}
		return true;
	}
}
