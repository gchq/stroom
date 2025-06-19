package stroom.widget.util.client;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.function.Function;

public class TableUtil {

    public static <T> String getString(final T object, final Function<T, String> function) {
        if (object == null) {
            return "";
        }
        final String string = function.apply(object);
        if (string == null) {
            return "";
        }
        return string;
    }

    public static <T> SafeHtml getSafeHtml(final T object, final Function<T, String> function) {
        if (object == null) {
            return SafeHtmlUtil.NBSP;
        }
        final String string = function.apply(object);
        if (string == null) {
            return SafeHtmlUtil.NBSP;
        }
        return SafeHtmlUtils.fromString(string);
    }
}
