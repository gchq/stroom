/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.framework.client.manager;

/// Builds URLs in plain Java (so it can be unit tested), e.g. adding a query parameter to the
/// current page's URL for a link the user copies.
public final class UrlParams {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private UrlParams() {
        // Static utility
    }

    /// Percent-encodes a value for use in a URL's query string. Letters, digits, `-`, `_`, `.`,
    /// `~` and `/` are left as they are, everything else is encoded as UTF-8.
    ///
    /// @param value The value, may be null.
    /// @return The encoded value, or an empty string for null.
    public static String encode(final String value) {
        if (value == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            final int codePoint = value.codePointAt(i);
            i += Character.charCount(codePoint);
            if (isUnreserved(codePoint)) {
                sb.append((char) codePoint);
            } else {
                appendUtf8(sb, codePoint);
            }
        }
        return sb.toString();
    }

    /// Sets a query parameter in a URL, replacing any existing values of the parameter and
    /// keeping the URL's other parameters and its fragment.
    ///
    /// @param url   The URL, e.g. `https://host/?path=/story/a--b#top`.
    /// @param name  The name of the parameter, which must not need encoding.
    /// @param value The value of the parameter, which is encoded.
    /// @return The URL with the parameter set.
    public static String withParameter(final String url, final String name, final String value) {
        final int hashIndex = url.indexOf('#');
        final String fragment = hashIndex >= 0
                ? url.substring(hashIndex)
                : "";
        final String withoutFragment = hashIndex >= 0
                ? url.substring(0, hashIndex)
                : url;
        final int queryIndex = withoutFragment.indexOf('?');
        final String base = queryIndex >= 0
                ? withoutFragment.substring(0, queryIndex)
                : withoutFragment;
        final String query = queryIndex >= 0
                ? withoutFragment.substring(queryIndex + 1)
                : "";

        final StringBuilder sb = new StringBuilder(base).append('?');
        boolean first = true;
        for (final String parameter : query.split("&")) {
            if (parameter.isEmpty() || isParameter(parameter, name)) {
                continue;
            }
            if (!first) {
                sb.append('&');
            }
            sb.append(parameter);
            first = false;
        }
        if (!first) {
            sb.append('&');
        }
        sb.append(name).append('=').append(encode(value)).append(fragment);
        return sb.toString();
    }

    private static boolean isParameter(final String parameter, final String name) {
        final int equalsIndex = parameter.indexOf('=');
        final String parameterName = equalsIndex >= 0
                ? parameter.substring(0, equalsIndex)
                : parameter;
        return parameterName.equals(name);
    }

    private static boolean isUnreserved(final int c) {
        return (c >= 'a' && c <= 'z')
               || (c >= 'A' && c <= 'Z')
               || (c >= '0' && c <= '9')
               || c == '-'
               || c == '_'
               || c == '.'
               || c == '~'
               || c == '/';
    }

    private static void appendUtf8(final StringBuilder sb, final int codePoint) {
        if (codePoint < 0x80) {
            appendByte(sb, codePoint);
        } else if (codePoint < 0x800) {
            appendByte(sb, 0xC0 | (codePoint >> 6));
            appendByte(sb, 0x80 | (codePoint & 0x3F));
        } else if (codePoint < 0x10000) {
            appendByte(sb, 0xE0 | (codePoint >> 12));
            appendByte(sb, 0x80 | ((codePoint >> 6) & 0x3F));
            appendByte(sb, 0x80 | (codePoint & 0x3F));
        } else {
            appendByte(sb, 0xF0 | (codePoint >> 18));
            appendByte(sb, 0x80 | ((codePoint >> 12) & 0x3F));
            appendByte(sb, 0x80 | ((codePoint >> 6) & 0x3F));
            appendByte(sb, 0x80 | (codePoint & 0x3F));
        }
    }

    private static void appendByte(final StringBuilder sb, final int value) {
        sb.append('%').append(HEX[(value >> 4) & 0xF]).append(HEX[value & 0xF]);
    }
}
