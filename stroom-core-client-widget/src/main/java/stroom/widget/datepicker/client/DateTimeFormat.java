package stroom.widget.datepicker.client;

public interface DateTimeFormat {

    /**
     * Format a date object.
     *
     * @param date the date object being formatted
     * @return string representation for this date in desired format
     */
    String format(JsDate date);

    /**
     * Retrieve the pattern used in this DateTimeFormat object.
     *
     * @return pattern string
     */
    String getPattern();
}
