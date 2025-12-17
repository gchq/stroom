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

package stroom.widget.util.client;

import stroom.util.shared.NullSafe;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesBuilder;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.Optional;

public class SafeHtmlUtil {

    private static Template template;

    public static final SafeHtml NBSP = SafeHtmlUtils.fromSafeConstant("&nbsp;");
    public static final SafeHtml ENSP = SafeHtmlUtils.fromSafeConstant("&ensp;");

    private SafeHtmlUtil() {
    }

    public static Template getTemplate() {
        if (template == null) {
            template = GWT.create(Template.class);
        }
        return template;
    }

    public static SafeHtml nullSafe(final SafeHtml safeHtml) {
        return safeHtml != null
                ? safeHtml
                : SafeHtmlUtils.EMPTY_SAFE_HTML;
    }

    /**
     * Null safe way to escape string and return {@link SafeHtml}
     */
    public static SafeHtml getSafeHtml(final String string) {
        if (NullSafe.isBlankString(string)) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        } else {
            return SafeHtmlUtils.fromString(string);
        }
    }

    /**
     * Null safe way to escape string and return {@link SafeHtml}
     */
    public static SafeHtml getSafeHtmlFromSafeConstant(final String string) {
        if (NullSafe.isBlankString(string)) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        } else {
            return SafeHtmlUtils.fromSafeConstant(string);
        }
    }

    /**
     * Null safe way to escape string and return {@link SafeHtml}
     */
    public static SafeHtml getSafeHtmlFromTrustedString(final String string) {
        if (NullSafe.isBlankString(string)) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        } else {
            return SafeHtmlUtils.fromTrustedString(string);
        }
    }

    private static String nullSafe(final String string) {
        return string != null
                ? string
                : "";
    }

    /**
     * Replaces \n with &lt;br&gt;
     * Any reserved chars in string will be escaped.
     * Will always return a {@link SafeHtml} object.
     */
    public static SafeHtml withLineBreaks(final String string) {
        if (string == null) {
            return SafeHtmlUtils.fromString("");
        } else {
            String str = string;
            while (str.startsWith("\n")) {
                str = str.substring(1);
            }
            while (str.endsWith("\n")) {
                str = str.substring(0, str.length() - 1);
            }
            // One ...<br> tag at the end of each line
            final SafeHtmlBuilder builder = new SafeHtmlBuilder();
            int lineBreakIdx;
            int lineNo = 1;
            while (!str.isEmpty()) {
                lineBreakIdx = str.indexOf("\n");
                final String line;
                if (lineBreakIdx == -1) {
                    line = str;
                    str = "";
                } else {
                    line = str.substring(0, lineBreakIdx);
                    if (str.length() > lineBreakIdx + 1) {
                        // Remove the line just appended
                        str = str.substring(lineBreakIdx + 1);
                    } else {
                        str = "";
                    }
                }

                if (lineNo++ != 1) {
                    builder.appendHtmlConstant("<br>");
                }
                if (!line.isEmpty()) {
                    builder.appendEscaped(line);
                }
            }
            return builder.toSafeHtml();
        }
    }

    /**
     * One html paragraph block for each line where a line is delimited by \n.
     * Any reserved chars in string will be escaped.
     * Will always return a {@link SafeHtml} object.
     */
    public static SafeHtml toParagraphs(final String string) {
        if (string == null) {
            return SafeHtmlUtils.fromString("");
        } else {
            String str = string;
            if (str.startsWith("\n")) {
                str = str.substring(1);
            }
            if (str.endsWith("\n")) {
                str = str.substring(0, str.length() - 1);
            }
            // One <p>...</p> block for each line
            final SafeHtmlBuilder builder = new SafeHtmlBuilder();
            int lineBreakIdx;
            while (!str.isEmpty()) {
                lineBreakIdx = str.indexOf("\n");
                final String line;
                if (lineBreakIdx == -1) {
                    line = str;
                    str = "";
                } else {
                    line = str.substring(0, lineBreakIdx);
                    if (str.length() > lineBreakIdx + 1) {
                        // Remove the line just appended
                        str = str.substring(lineBreakIdx + 1);
                    } else {
                        str = "";
                    }
                }

                if (!line.isEmpty()) {
                    builder
                            .appendHtmlConstant("<p>")
                            .appendEscaped(line)
                            .appendHtmlConstant("</p>");
                }
            }
            return builder.toSafeHtml();
        }
    }

    /**
     * @return Text coloured grey if enabled is false
     */
    public static SafeHtml getSafeHtml(final String string, final boolean enabled) {
        if (enabled) {
            return getSafeHtml(string);
        }
        return getColouredText(string, "grey");
    }

    /**
     * @param trustedColour Must be trusted and known at compile time
     * @return Text coloured grey if enabled is false
     */
    public static SafeHtml getColouredText(final String text, final String trustedColour) {
        if (NullSafe.isEmptyString(trustedColour)) {
            return getSafeHtml(text);
        } else {
            return getTemplate().spanWithStyle(
                    new SafeStylesBuilder().trustedColor(trustedColour).toSafeStyles(),
                    SafeHtmlUtil.getSafeHtml(text));
        }
    }

    public static SafeHtml getColouredText(final String string,
                                           final String colour,
                                           final boolean isColoured) {
        if (isColoured) {
            return getColouredText(string, colour);
        } else {
            return getSafeHtml(string);
        }
    }

    public static SafeHtml from(final boolean b) {
        return SafeHtmlUtils.fromTrustedString(String.valueOf(b));
    }

    public static SafeHtml from(final byte num) {
        return SafeHtmlUtils.fromTrustedString(String.valueOf(num));
    }

    public static SafeHtml from(final char c) {
        return SafeHtmlUtils.fromTrustedString(SafeHtmlUtils.htmlEscape(c));
    }

    public static SafeHtml from(final double num) {
        return SafeHtmlUtils.fromTrustedString(String.valueOf(num));
    }

    public static SafeHtml from(final float num) {
        return SafeHtmlUtils.fromTrustedString(String.valueOf(num));
    }

    public static SafeHtml from(final int num) {
        return SafeHtmlUtils.fromTrustedString(String.valueOf(num));
    }

    public static SafeHtml from(final long num) {
        return SafeHtmlUtils.fromTrustedString(String.valueOf(num));
    }

    public static SafeHtml from(final Boolean b) {
        if (b == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }
        return SafeHtmlUtils.fromTrustedString(String.valueOf(b));
    }

    public static SafeHtml from(final Byte num) {
        if (num == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }
        return SafeHtmlUtils.fromTrustedString(String.valueOf(num));
    }

    public static SafeHtml from(final Character c) {
        if (c == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }
        return SafeHtmlUtils.fromTrustedString(SafeHtmlUtils.htmlEscape(c));
    }

    public static SafeHtml from(final Double num) {
        if (num == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }
        return SafeHtmlUtils.fromTrustedString(String.valueOf(num));
    }

    public static SafeHtml from(final Float num) {
        if (num == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }
        return SafeHtmlUtils.fromTrustedString(String.valueOf(num));
    }

    public static SafeHtml from(final Integer num) {
        if (num == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }
        return SafeHtmlUtils.fromTrustedString(String.valueOf(num));
    }

    public static SafeHtml from(final Long num) {
        if (num == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }
        return SafeHtmlUtils.fromTrustedString(String.valueOf(num));
    }

    public static SafeHtml from(final String string) {
        if (string == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }
        return SafeHtmlUtils.fromString(string);
    }

    /**
     * If untrustedColour is a valid HTML colour (i.e. a valid colour name or hex/rgb/rgba/hsl code)
     * then an {@link Optional} will be returned populated with that colour. The returned value
     * will also be html escaped for good measure.
     *
     * @param untrustedColour An untrusted colour value
     * @return A trusted colour value.
     */
    public static Optional<String> asTrustedColour(final String untrustedColour) {
        if (NullSafe.isNonBlankString(untrustedColour)) {
            final String escaped = SafeHtmlUtils.htmlEscape(untrustedColour.trim());
            if (nativeIsValidHtmlColour(escaped)
                && !"unset".equalsIgnoreCase(escaped)
                && !"initial".equalsIgnoreCase(escaped)
                && !"inherit".equalsIgnoreCase(escaped)) {
                return Optional.of(escaped);
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    @SuppressWarnings("GrazieInspection")
    private static native boolean nativeIsValidHtmlColour(final String untrustedColour) /*-{
        // Set the untrusted colour on a new Option element. If we can read it back non-empty
        // then it is a valid colour.
        var s = new Option().style;
        s.color = untrustedColour;
        return s.color !== '';
    }-*/;

    // --------------------------------------------------------------------------------


    public interface Template extends SafeHtmlTemplates {

        @SafeHtmlTemplates.Template("<div>{0}</div>")
        SafeHtml div(SafeHtml inner);

        @SafeHtmlTemplates.Template("<div class=\"{0}\">{1}</div>")
        SafeHtml divWithClass(String className, SafeHtml inner);

        @SafeHtmlTemplates.Template("<div title=\"{0}\">{1}</div>")
        SafeHtml divWithTitle(String title, SafeHtml inner);

        @SafeHtmlTemplates.Template("<div style=\"{0}\">{1}</div>")
        SafeHtml divWithStyle(SafeStyles style, SafeHtml inner);

        @SafeHtmlTemplates.Template("<div class=\"{0}\" title=\"{1}\">{2}</div>")
        SafeHtml divWithClassAndTitle(String className, String title, SafeHtml inner);

        @SafeHtmlTemplates.Template("<div class=\"{0}\" style=\"{1}\" title=\"{2}\">{3}</div>")
        SafeHtml divWithClassStyleAndTitle(String className, SafeStyles style, String title, SafeHtml inner);

        @SafeHtmlTemplates.Template("<div class=\"{0}\" style=\"{1}\">{2}</div>")
        SafeHtml divWithClassAndStyle(String className, SafeStyles style, SafeHtml inner);

        @SafeHtmlTemplates.Template("<span class=\"{0}\">{1}</span>")
        SafeHtml spanWithClass(String className, SafeHtml inner);

        @SafeHtmlTemplates.Template("<span style=\"{0}\">{1}</span>")
        SafeHtml spanWithStyle(SafeStyles style, SafeHtml inner);
    }
}
