package com.configcat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateTimeUtils {

    private DateTimeUtils() { /* prevent from instantiation*/ }

    /**
     * HTTP Date header formatter. Date: day-name, day month year hour:minute:second GMT
     *
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Date">mdn docs</a>
     */
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);

    public static boolean isValidDate(String date) {
        try {
            SIMPLE_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
            SIMPLE_DATE_FORMAT.parse(date);
        } catch (ParseException e) {
            return false;
        }
        return true;
    }

    public static long parseToMillis(String dateTime) {
        try {
            SIMPLE_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
            return SIMPLE_DATE_FORMAT.parse(dateTime).getTime();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static String format(long timeInMilliseconds) {
        SIMPLE_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
        return SIMPLE_DATE_FORMAT.format(new Date(timeInMilliseconds));

    }
}