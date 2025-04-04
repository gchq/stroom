package stroom.dashboard.client.table.cf;

import stroom.query.api.CustomConditionalFormattingStyle;
import stroom.query.api.CustomRowStyle;

import com.google.gwt.safecss.shared.SafeStylesBuilder;
import com.google.gwt.safecss.shared.SafeStylesHostedModeUtils;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.RootPanel;

import java.util.HashMap;
import java.util.Map;

public class ConditionalFormattingDynamicStyles {

    private static final Map<CustomConditionalFormattingStyle, String> styles = new HashMap<>();
    private static Element styleElement;

    public static String create(final CustomConditionalFormattingStyle formattingStyle) {
        if (formattingStyle == null) {
            return "";
        }

        if (styleElement == null) {
            styleElement = DOM.createElement("style");
            RootPanel.getBodyElement().appendChild(styleElement);
        }

        return styles.computeIfAbsent(formattingStyle, k -> {
            final String dynamicName = "cfDynamic" + (styles.size() + 1);
            final String lightCss = makeCss(dynamicName, ".stroom-theme-light ", k.getLight());
            final String darkCss = makeCss(dynamicName, ".stroom-theme-dark ", k.getDark());
            styleElement.setInnerHTML(styleElement.getInnerHTML() + lightCss + darkCss);
            return dynamicName;
        });
    }

    private static String makeCss(final String dynamicName,
                                  final String cssPrefix,
                                  final CustomRowStyle customRowStyle) {
        if (customRowStyle == null) {
            return "";
        }
        final String declarations = makeStyleDeclarations(customRowStyle);
        if (declarations == null) {
            return "";
        }
        return cssPrefix + "." + dynamicName + " " +
               "{" + declarations + "}\n\n";
    }

    private static String makeStyleDeclarations(final CustomRowStyle customRowStyle) {
        // Custom row styles.
        final String backgroundColour = cleanValue(customRowStyle.getBackgroundColour());
        final String textColour = cleanValue(customRowStyle.getTextColour());
        if (backgroundColour != null || textColour != null) {
            final SafeStylesBuilder builder = new SafeStylesBuilder();
            if (backgroundColour != null) {
                builder.trustedBackgroundColor(backgroundColour);
            }
            if (textColour != null) {
                builder.trustedColor(textColour);
            }
            return builder.toSafeStyles().asString();
        }
        return null;
    }

    private static String cleanValue(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        if (trimmed.length() == 0) {
            return null;
        }
        if (SafeStylesHostedModeUtils.isValidStyleValue(trimmed) != null) {
            return null;
        }
        return trimmed;
    }
}
