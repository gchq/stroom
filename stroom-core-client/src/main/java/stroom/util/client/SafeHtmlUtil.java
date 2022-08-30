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
     * One html paragraph block for each line where a line is delimited by \n.
     */
    public static SafeHtml toParagraphs(final String string) {
        if (string == null) {
            return null;
        } else {
            String str = string;
            if (str.startsWith("\n")) {
                str = str.substring(1);
            }
            if (str.endsWith("\n")) {
                str = str.substring(0, str.length());
            }
            // One <p>...</p> block for each line
            final SafeHtmlBuilder builder = new SafeHtmlBuilder();
            int lineBreakIdx;
            while (!str.isEmpty()) {
                lineBreakIdx = str.indexOf("\n");
                final String line;
                if (lineBreakIdx == -1) {
                    line = str;
                    str = "";
                } else {
                    line = str.substring(0, lineBreakIdx);
                    if (str.length() > lineBreakIdx + 1) {
                        // Remove the line just appended
                        str = str.substring(lineBreakIdx + 1);
                    } else {
                        str = "";
                    }
                }

                if (!line.isEmpty()) {
                    builder
                            .appendHtmlConstant("<p>")
                            .appendEscaped(line)
                            .appendHtmlConstant("</p>");
                }
            }
            return builder.toSafeHtml();
        }
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
