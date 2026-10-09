/*
 * Copyright 2022 Crown Copyright
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

package stroom.query.api.token;

public class QuotedStringUtil {

    private QuotedStringUtil() {
    }

    /**
     * Escapes a value for inclusion in a quoted string literal, so that
     * {@link #unescape} returns the original value.
     *
     * <p>The escape character makes the following character literal, so only two
     * characters need escaping: the escape character itself, and the quote that
     * would otherwise end the literal. Backslashes must be doubled <em>before</em>
     * quotes are escaped, or the backslash introduced by escaping a quote gets
     * doubled in turn.</p>
     *
     * <p>Use this wherever a value is interpolated into generated query text. A
     * value carrying a quote character otherwise terminates the literal early and
     * produces malformed query text — a store name or a schema path is quite
     * capable of containing one, and an XPath legitimately may
     * ({@code /entry[@type="gate"]}).</p>
     *
     * @param value      the raw value; {@code null} yields an empty string so
     *                   callers can append unconditionally
     * @param quoteChar  the quote character the literal is delimited by
     * @param escapeChar the escape character, normally a backslash
     * @return the escaped value, without surrounding quotes
     */
    public static String escape(final String value,
                                final char quoteChar,
                                final char escapeChar) {
        if (value == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == escapeChar || c == quoteChar) {
                sb.append(escapeChar);
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Escapes a value for a double-quoted literal using the standard backslash
     * escape character — the form StroomQL's {@code from} clause and function
     * arguments use.
     *
     * @param value the raw value; {@code null} yields an empty string
     * @return the escaped value, without surrounding quotes
     */
    public static String escapeDoubleQuoted(final String value) {
        return escape(value, '"', '\\');
    }

    /**
     * Remove the outer quotes (first and last char in the array) and unescape all
     * escaped characters.
     *
     * @param start Inclusive array index
     * @param end   Inclusive array index
     */
    public static String unescape(final char[] chars, final int start, final int end, final char escapeChar) {
        // Break the string into quoted text blocks.
        final char[] out = new char[end - start + 1];
        boolean escape = false;
        int index = 0;
        for (int i = start + 1; i < end; i++) {
            final char c = chars[i];
            if (escape) {
                escape = false;
                out[index++] = c;
            } else {
                if (c == escapeChar) {
                    escape = true;
                } else {
                    out[index++] = c;
                }
            }
        }
        final String output = new String(out, 0, index);

//        LOGGER.trace(() -> {
//            final String input = new String(chars, start, end - start + 1);
//            return LogUtil.message("input [{}], output: [{}]", input, output);
//        });

        return output;
    }
}
