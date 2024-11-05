package stroom.dashboard.client.table;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.RootPanel;

import java.util.HashMap;
import java.util.Map;

public class DynamicStyles {

    private static final Map<String, DynamicStyle> styles = new HashMap<>();
    private static Element styleElement;

    public static DynamicStyle create(final String style) {
        if (styleElement == null) {
            styleElement = DOM.createElement("style");
            RootPanel.getBodyElement().appendChild(styleElement);
        }

        return styles.computeIfAbsent(style, k -> {
            final String dynamicName = "dynamic" + styles.size() + 1;
            final String css = ".dataGridEvenRow." + dynamicName + "," +
                    " .dataGridOddRow." + dynamicName + " " +
                    "{" + style + "}\n\n";
            styleElement.setInnerHTML(styleElement.getInnerHTML() + css);
            return new DynamicStyle(dynamicName, css);
        });
    }

    public static class DynamicStyle {

        private final String name;
        private final String css;

        public DynamicStyle(final String name, final String css) {
            this.name = name;
            this.css = css;
        }

        public String getName() {
            return name;
        }

        public String getCss() {
            return css;
        }
    }
}
