package stroom.dashboard.client.table.cf;

import stroom.query.api.v2.ConditionalFormattingRule;
import stroom.query.api.v2.ConditionalFormattingStyle;
import stroom.util.shared.GwtNullSafe;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

public class ConditionalFormattingSwatchUtil {

    public static SafeHtml createTableCell(final ConditionalFormattingRule rule) {
        if (rule.isCustomStyle()) {
            final SafeHtmlBuilder sb = new SafeHtmlBuilder();
            sb.appendHtmlConstant("<div class=\"cf-colour-swatch\" style=\"");
            if (GwtNullSafe.isNonBlankString(rule.getBackgroundColor())) {
                sb.appendHtmlConstant("background-color:");
                sb.appendEscaped(rule.getBackgroundColor());
            }
            if (GwtNullSafe.isNonBlankString(rule.getTextColor())) {
                sb.appendHtmlConstant(";color:");
                sb.appendEscaped(rule.getTextColor());
            }
            sb.appendHtmlConstant("\">");
            sb.appendEscaped("Custom");
            sb.appendHtmlConstant("</div>");
            return sb.toSafeHtml();
        } else {
            return createSwatch(rule.getStyle());
        }
    }

    public static SafeHtml createSwatch(final ConditionalFormattingStyle style) {
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        if (style == null) {
            sb.appendHtmlConstant("<div class=\"cf-colour-swatch\">");
            sb.appendEscaped("None");
        } else {
            sb.appendHtmlConstant("<div class=\"cf-colour-swatch " + style.getCssClassName() + "\">");
            sb.appendEscaped(style.getDisplayValue());
        }
        sb.appendHtmlConstant("</div>");
        return sb.toSafeHtml();
    }
}
