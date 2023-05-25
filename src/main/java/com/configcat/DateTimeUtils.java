package com.configcat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateTimeUtils {

    private DateTimeUtils() { /* prevent from instantiation*/ }

    public static boolean isValidDate(String date) {
        try {
            Long.parseLong(date);
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }
}