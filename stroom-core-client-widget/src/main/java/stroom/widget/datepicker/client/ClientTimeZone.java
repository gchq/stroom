package stroom.widget.datepicker.client;

public class ClientTimeZone {
    private static String TIME_ZONE = "GMT";

    public static String getTimeZone() {
        return TIME_ZONE;
    }

    public static void setTimeZone(final String timeZone) {
        TIME_ZONE = timeZone;
    }
}
