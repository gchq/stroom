package stroom.dashboard.client.table.cf;

import stroom.main.client.view.DynamicStyles;
import stroom.query.api.CustomConditionalFormattingStyle;
import stroom.query.api.CustomRowStyle;

import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesBuilder;
import com.google.gwt.safecss.shared.SafeStylesHostedModeUtils;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.HashMap;
import java.util.Map;

public class ConditionalFormattingDynamicStyles {

    private static final Map<CustomConditionalFormattingStyle, String> styles = new HashMap<>();

    public static String create(final CustomConditionalFormattingStyle formattingStyle) {
        if (formattingStyle == null) {
            return "";
        }

        return styles.computeIfAbsent(formattingStyle, k -> {
            final String dynamicName = "cfDynamic" + (styles.size() + 1);
            DynamicStyles.put(
                    SafeHtmlUtils.fromTrustedString(".stroom-theme-light ." + dynamicName),
                    makeStyleDeclarations(k.getLight()));
            DynamicStyles.put(
                    SafeHtmlUtils.fromTrustedString(".stroom-theme-dark ." + dynamicName),
                    makeStyleDeclarations(k.getDark()));
            return dynamicName;
        });
    }

    private static SafeStyles makeStyleDeclarations(final CustomRowStyle customRowStyle) {
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
            return builder.toSafeStyles();
        }
        return null;
    }

    private static String cleanValue(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (SafeStylesHostedModeUtils.isValidStyleValue(trimmed) != null) {
            return null;
        }
        return trimmed;
    }
}
