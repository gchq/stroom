package stroom.dashboard.client.table.cf;

import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.ConditionalFormattingStyle;
import stroom.query.api.ConditionalFormattingType;
import stroom.query.api.CustomConditionalFormattingStyle;
import stroom.query.api.TextAttributes;
import stroom.security.client.presenter.ClassNameBuilder;
import stroom.util.shared.NullSafe;

import com.google.gwt.safecss.shared.SafeStylesHostedModeUtils;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

public class ConditionalFormattingSwatchUtil {

    public static final String CF_COLOUR_SWATCH = "cf-colour-swatch";
    public static final String CF_TEXT = "cf-text";
    public static final String CF_BOLD = "cf-bold";
    public static final String CF_ITALIC = "cf-italic";

    public static SafeHtml createTableCell(final ConditionalFormattingRule rule) {
        return createSwatch(
                rule.getFormattingType(),
                rule.getFormattingStyle(),
                rule.getCustomStyle(),
                rule.getTextAttributes());
    }

    public static SafeHtml createSwatch(final ConditionalFormattingType formattingType,
                                        final ConditionalFormattingStyle formattingStyle,
                                        final CustomConditionalFormattingStyle customStyle,
                                        final TextAttributes textAttributes) {
        if (formattingType == null || ConditionalFormattingType.CUSTOM.equals(formattingType)) {
            final ClassNameBuilder classNameBuilder = new ClassNameBuilder();
            classNameBuilder.addClassName(CF_COLOUR_SWATCH);
            classNameBuilder.addClassName(ConditionalFormattingDynamicStyles.create(customStyle));
            classNameBuilder.addClassName(getTextAttributeClassNames(textAttributes));

            final SafeHtmlBuilder sb = new SafeHtmlBuilder();
            sb.appendHtmlConstant("<div");
            sb.appendHtmlConstant(classNameBuilder.buildClassAttribute());
            sb.appendHtmlConstant(">");
            sb.appendEscaped("Custom");
            sb.appendHtmlConstant("</div>");
            return sb.toSafeHtml();
        } else {
            return createSwatch(formattingType, formattingStyle, textAttributes);
        }
    }

    public static SafeHtml createSwatch(final ConditionalFormattingType formattingType,
                                        final ConditionalFormattingStyle formattingStyle,
                                        final TextAttributes textAttributes) {
        final ClassNameBuilder classNameBuilder = new ClassNameBuilder();
        classNameBuilder.addClassName(CF_COLOUR_SWATCH);
        if (formattingStyle != null) {
            if (ConditionalFormattingType.TEXT.equals(formattingType)) {
                classNameBuilder.addClassName(CF_TEXT);
            }
            classNameBuilder.addClassName(formattingStyle.getCssClassName());
        }
        classNameBuilder.addClassName(getTextAttributeClassNames(textAttributes));

        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        sb.appendHtmlConstant("<div");
        sb.appendHtmlConstant(classNameBuilder.buildClassAttribute());
        sb.appendHtmlConstant(">");
        if (formattingStyle == null) {
            sb.appendEscaped("None");
        } else {
            sb.appendEscaped(formattingStyle.getDisplayValue());
        }
        sb.appendHtmlConstant("</div>");

        return sb.toSafeHtml();
    }

    public static SafeHtml createCustomSwatch(final String backgroundColour,
                                              final String textColour,
                                              final TextAttributes textAttributes) {
        final ClassNameBuilder classNameBuilder = new ClassNameBuilder();
        classNameBuilder.addClassName(CF_COLOUR_SWATCH);
        classNameBuilder.addClassName(getTextAttributeClassNames(textAttributes));

        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        sb.appendHtmlConstant("<div");
        sb.appendHtmlConstant(classNameBuilder.buildClassAttribute());
        sb.appendHtmlConstant(" style=\"");
        if (NullSafe.isNonBlankString(backgroundColour) &&
            SafeStylesHostedModeUtils.isValidStyleValue(backgroundColour) == null) {
            sb.appendHtmlConstant("background-color:");
            sb.appendEscaped(backgroundColour);
        }
        if (NullSafe.isNonBlankString(textColour) &&
            SafeStylesHostedModeUtils.isValidStyleValue(textColour) == null) {
            sb.appendHtmlConstant(";color:");
            sb.appendEscaped(textColour);
        }
        sb.appendHtmlConstant("\">");
        sb.appendEscaped("Custom");
        sb.appendHtmlConstant("</div>");
        return sb.toSafeHtml();
    }

    public static String getTextAttributeClassNames(final TextAttributes textAttributes) {
        final ClassNameBuilder classNameBuilder = new ClassNameBuilder();
        if (textAttributes != null) {
            if (textAttributes.isBold()) {
                classNameBuilder.addClassName(CF_BOLD);
            }
            if (textAttributes.isItalic()) {
                classNameBuilder.addClassName(CF_ITALIC);
            }
        }
        return classNameBuilder.build();
    }
}
