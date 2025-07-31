package stroom.main.client.view;

import stroom.widget.util.client.HtmlBuilder;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.RootPanel;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class DynamicStyles {

    private static Element styleElement;

    private static Map<SafeHtml, SafeStyles> styles = new HashMap<>();

    public static Element getStyleElement() {
        if (styleElement == null) {
            styleElement = DOM.createElement("style");
            RootPanel.getBodyElement().appendChild(styleElement);
        }
        return styleElement;
    }

    public static void put(final SafeHtml className, final SafeStyles safeStyles) {
        styles.put(className, safeStyles);
        update();
    }

    public static void remove(final SafeHtml className) {
        styles.remove(className);
        update();
    }

    private static void update() {
        final HtmlBuilder builder = new HtmlBuilder();
        for (final Entry<SafeHtml, SafeStyles> entry : styles.entrySet()) {
            builder.append(entry.getKey());
            builder.appendTrustedString(" {\n");
            builder.appendTrustedString(entry.getValue().asString());
            builder.appendTrustedString("\n}\n\n");
        }
        getStyleElement().setInnerSafeHtml(builder.toSafeHtml());
    }
}
