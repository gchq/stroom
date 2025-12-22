/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.client.table.cf;

import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.ConditionalFormattingStyle;
import stroom.query.api.ConditionalFormattingType;
import stroom.query.api.CustomConditionalFormattingStyle;
import stroom.query.api.TextAttributes;
import stroom.security.client.presenter.ClassNameBuilder;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.safecss.shared.SafeStylesBuilder;
import com.google.gwt.safehtml.shared.SafeHtml;

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
            final String classNamesStr = new ClassNameBuilder()
                    .addClassName(CF_COLOUR_SWATCH)
                    .addClassName(ConditionalFormattingDynamicStyles.create(customStyle))
                    .addAll(getTextAttributeClassNames(textAttributes))
                    .build();
            return SafeHtmlUtil.getTemplate()
                    .divWithClass(classNamesStr, SafeHtmlUtil.getSafeHtml("Custom"));
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
        classNameBuilder.addAll(getTextAttributeClassNames(textAttributes));

        final SafeHtml inner = SafeHtmlUtil.getSafeHtml(
                formattingStyle == null
                        ? "None"
                        : formattingStyle.getDisplayValue());
        return SafeHtmlUtil.getTemplate()
                .divWithClass(classNameBuilder.build(), inner);
    }

    public static SafeHtml createCustomSwatch(final String backgroundColour,
                                              final String textColour,
                                              final TextAttributes textAttributes) {
        final ClassNameBuilder classNameBuilder = new ClassNameBuilder();
        classNameBuilder.addClassName(CF_COLOUR_SWATCH);
        classNameBuilder.addAll(getTextAttributeClassNames(textAttributes));

        final SafeStylesBuilder safeStylesBuilder = new SafeStylesBuilder();
        SafeHtmlUtil.asTrustedColour(backgroundColour)
                .ifPresent(safeStylesBuilder::trustedBackgroundColor);
        SafeHtmlUtil.asTrustedColour(textColour)
                .ifPresent(safeStylesBuilder::trustedColor);

        return SafeHtmlUtil.getTemplate()
                .divWithClassAndStyle(
                        classNameBuilder.build(),
                        safeStylesBuilder.toSafeStyles(),
                        SafeHtmlUtil.getSafeHtml("Custom"));
    }

    public static ClassNameBuilder getTextAttributeClassNames(final TextAttributes textAttributes) {
        final ClassNameBuilder classNameBuilder = new ClassNameBuilder();
        if (textAttributes != null) {
            if (textAttributes.isBold()) {
                classNameBuilder.addClassName(CF_BOLD);
            }
            if (textAttributes.isItalic()) {
                classNameBuilder.addClassName(CF_ITALIC);
            }
        }
        return classNameBuilder;
    }
}
