package stroom.util.client;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class SafeHtmlUtil {

    private SafeHtmlUtil() {
    }

    public static SafeHtml getSafeHtml(final String string) {
        return SafeHtmlUtils.fromString(nullSafe(string));
    }

    private static String nullSafe(final String string) {
        return string != null
            ? string
            : "";
    }

    /**
     * @return Text coloured grey if enabled is false
     */
    public static SafeHtml getSafeHtml(final String string, final boolean enabled) {
        if (enabled) {
            return getSafeHtml(string);
        }
        return getColouredText(string, "grey");
    }

    /**
     * @return Text coloured grey if enabled is false
     */
    public static SafeHtml getColouredText(final String string, final String colour) {
        if (colour == null || colour.isEmpty()) {
            return getSafeHtml(string);
        }

        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        builder.appendHtmlConstant("<span style=\"color:" + colour + "\">");
        builder.appendEscaped(nullSafe(string));
        builder.appendHtmlConstant("</span>");
        return builder.toSafeHtml();
    }

    public static SafeHtml getColouredText(final String string,
                                           final String colour,
                                           final boolean isColoured) {
        if (isColoured) {
            return getColouredText(string, colour);
        } else {
            return getSafeHtml(string);
        }
    }
}
