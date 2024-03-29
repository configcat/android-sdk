package com.configcat;


import java.text.SimpleDateFormat;
import java.util.Date;
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

    public static String doubleToFormattedUTC(double dateInDouble) {
        long dateInMilliSec = (long) dateInDouble * 1000;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return simpleDateFormat.format(new Date(dateInMilliSec));
    }

    public static double getUnixSeconds(Date date) {
        return date.getTime() / 1000d;
    }
}