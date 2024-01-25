package org.unicode.cldr.surveydriver;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class SurveyDriverCredentials {
    /**
     * Fictitious domain identifying fictitious email addresses for simulated users like
     * "driver-123@cldr-apps-webdriver.org"
     */
    private static final String EMAIL_AT_DOMAIN = "@cldr-apps-webdriver.org";

    private static final String EMAIL_PREFIX = "driver-";

    /**
     * cldr-apps-webdriver/src/test/resources/org/unicode/cldr/surveydriver/surveydriver.properties
     * -- not in version control; contains a line WEBDRIVER_PASSWORD=...
     */
    private static final String PROPS_FILENAME = "surveydriver.properties";

    private static final String PROPS_PASSWORD_KEY = "WEBDRIVER_PASSWORD";
    private static String webdriverPassword = null;

    private final String email;

    private SurveyDriverCredentials(String email) {
        this.email = email;
    }

    /**
     * Get credentials for logging in as a particular user depending on which Selenium slot we're
     * running on.
     */
    public static SurveyDriverCredentials getForUser(int userIndex) {
        String email = EMAIL_PREFIX + userIndex + EMAIL_AT_DOMAIN;
        return new SurveyDriverCredentials(email);
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        if (webdriverPassword != null) {
            return webdriverPassword;
        }
        final InputStream stream =
                SurveyDriverCredentials.class.getResourceAsStream(PROPS_FILENAME);
        if (stream == null) {
            throw new RuntimeException("File not found: " + PROPS_FILENAME);
        }
        final Properties props = new java.util.Properties();
        try {
            props.load(stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        webdriverPassword = (String) props.get(PROPS_PASSWORD_KEY);
        if (webdriverPassword == null || webdriverPassword.isBlank()) {
            throw new RuntimeException("WEBDRIVER_PASSWORD not found in " + PROPS_FILENAME);
        }
        return webdriverPassword;
    }
}
