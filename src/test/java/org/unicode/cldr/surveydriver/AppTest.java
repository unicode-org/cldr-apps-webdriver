package org.unicode.cldr.surveydriver;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppTest {

    @Test
    public void shouldDrive() {
        System.out.println("AppTest.shouldDrive calling SurveyDriver.go");
        SurveyDriver.go();
        assertTrue(true);
    }
}
