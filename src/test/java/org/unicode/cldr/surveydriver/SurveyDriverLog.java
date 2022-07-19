package org.unicode.cldr.surveydriver;

public class SurveyDriverLog {

    public static void println(Exception e) {
        println("Exception: " + e);
    }

    public static void println(String s) {
        System.out.println(s);
    }
}
