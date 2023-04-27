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
    private static final String HTTP_HEADER_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss z";

    public static boolean isValidDate(String date) {
        try {
            SimpleDateFormat simpleDateFormat= new SimpleDateFormat(HTTP_HEADER_DATE_FORMAT, Locale.ENGLISH);
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            simpleDateFormat.parse(date);
        } catch (ParseException e) {
            return false;
        }
        return true;
    }

    public static long parseToMillis(String dateTime) {
        try {
            SimpleDateFormat simpleDateFormat= new SimpleDateFormat(HTTP_HEADER_DATE_FORMAT, Locale.ENGLISH);
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            return simpleDateFormat.parse(dateTime).getTime();
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static String format(long timeInMilliseconds) {
        SimpleDateFormat simpleDateFormat= new SimpleDateFormat(HTTP_HEADER_DATE_FORMAT, Locale.ENGLISH);
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return simpleDateFormat.format(new Date(timeInMilliseconds));

    }
}