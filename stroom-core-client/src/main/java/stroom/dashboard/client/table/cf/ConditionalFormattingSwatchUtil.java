package stroom.dashboard.client.table.cf;

import stroom.query.api.v2.ConditionalFormattingRule;
import stroom.query.api.v2.ConditionalFormattingStyle;
import stroom.query.api.v2.ConditionalFormattingType;
import stroom.util.shared.GwtNullSafe;

import com.google.gwt.safecss.shared.SafeStylesHostedModeUtils;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

public class ConditionalFormattingSwatchUtil {

    public static SafeHtml createTableCell(final ConditionalFormattingRule rule) {
        if (rule.getFormattingType() == null || ConditionalFormattingType.CUSTOM.equals(rule.getFormattingType())) {
            final String styleName = ConditionalFormattingDynamicStyles.create(rule.getCustomStyle());
            final SafeHtmlBuilder sb = new SafeHtmlBuilder();
            sb.appendHtmlConstant("<div class=\"cf-colour-swatch " + styleName + "\"");
            sb.appendHtmlConstant("\">");
            sb.appendEscaped("Custom");
            sb.appendHtmlConstant("</div>");
            return sb.toSafeHtml();
        } else {
            return createSwatch(rule.getFormattingType(), rule.getFormattingStyle());
        }
    }

    public static SafeHtml createSwatch(final ConditionalFormattingType formattingType,
                                        final ConditionalFormattingStyle formattingStyle) {
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        String div = "<div class=\"cf-colour-swatch";
        if (formattingStyle != null) {
            if (ConditionalFormattingType.TEXT.equals(formattingType)) {
                div += " cf-text";
            }
            div += " ";
            div += formattingStyle.getCssClassName();
        }
        div += "\">";
        sb.appendHtmlConstant(div);

        if (formattingStyle == null) {
            sb.appendEscaped("None");
        } else {
            sb.appendEscaped(formattingStyle.getDisplayValue());
        }

        sb.appendHtmlConstant("</div>");
        return sb.toSafeHtml();
    }

    public static SafeHtml createCustomSwatch(final String backgroundColour,
                                              final String textColour) {
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        sb.appendHtmlConstant("<div class=\"cf-colour-swatch\" style=\"");
        if (GwtNullSafe.isNonBlankString(backgroundColour) &&
            SafeStylesHostedModeUtils.isValidStyleValue(backgroundColour) == null) {
            sb.appendHtmlConstant("background-color:");
            sb.appendEscaped(backgroundColour);
        }
        if (GwtNullSafe.isNonBlankString(textColour) &&
            SafeStylesHostedModeUtils.isValidStyleValue(textColour) == null) {
            sb.appendHtmlConstant(";color:");
            sb.appendEscaped(textColour);
        }
        sb.appendHtmlConstant("\">");
        sb.appendEscaped("Custom");
        sb.appendHtmlConstant("</div>");
        return sb.toSafeHtml();
    }
}
