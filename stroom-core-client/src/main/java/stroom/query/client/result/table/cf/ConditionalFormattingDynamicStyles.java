/*
 * Copyright 2024 Crown Copyright
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

package stroom.query.client.result.table.cf;

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
            // Only a theme with colours gets a style
            final SafeStyles light = makeStyleDeclarations(k.getLight());
            if (light != null) {
                DynamicStyles.put(SafeHtmlUtils.fromTrustedString(".stroom-theme-light ." + dynamicName), light);
            }
            final SafeStyles dark = makeStyleDeclarations(k.getDark());
            if (dark != null) {
                DynamicStyles.put(SafeHtmlUtils.fromTrustedString(".stroom-theme-dark ." + dynamicName), dark);
            }
            return dynamicName;
        });
    }

    private static SafeStyles makeStyleDeclarations(final CustomRowStyle customRowStyle) {
        // A theme the rule gives no colours for, e.g. only the light theme's are set
        if (customRowStyle == null) {
            return null;
        }
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
