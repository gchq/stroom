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

package stroom.hyperlink.client;

import stroom.svg.shared.SvgImage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.URL;

import java.util.Objects;

/**
 * This class is used to detect table cell values that contain URL's to be turned into hyperlinks.
 * <p>
 * [:text](http://some-url/:id){:hyperlinkTarget}
 */
public class Hyperlink {

    private final String text;
    private final String href;
    private final String type;
    private final SvgImage icon;
    private final String target;

    public Hyperlink(final String text,
                     final String href,
                     final String type,
                     final SvgImage icon,
                     final String target) {
        this.text = text;
        this.href = href;
        this.type = type;
        this.icon = icon;
        this.target = target;
    }

    /**
     * @param value The value to parse.
     * @return The parsed {@link Hyperlink} or null if value could not be parsed
     * for any reason.
     */
    public static Hyperlink create(final String value) {
        return create(value, 0);
    }

    /**
     * @param value The value to parse.
     * @param pos   The index in value to start at.
     * @return The parsed {@link Hyperlink} or null if value could not be parsed
     * for any reason.
     */
    public static Hyperlink create(final String value, final int pos) {
        Hyperlink hyperlink = null;

        try {
            int index = pos;
            final String text = nextToken(value, index, '[', ']');
            if (text != null) {
                index = index + text.length() + 2;
                final String href = nextToken(value, index, '(', ')');
                if (href != null) {
                    index = index + href.length() + 2;
                    final String type = nextToken(value, index, '{', '}');
                    if (type != null) {
                        index = index + type.length() + 2;
                    }
                    final String target = nextToken(value, index, '<', '>');
                    hyperlink = new Builder()
                            .text(text)
                            .href(decode(href))
                            .type(type)
                            .target(target)
                            .build();
                }
            }
        } catch (final Exception e) {
            // Can't parse the link for some reason, so just swallow the error
            // as the value may not be intended as a link, e.g. could just be '[foo]'
            // If it was meant to be a link then hopefully the user will realise something
            // is wrong as they will just see the raw value.
        }

        return hyperlink;
    }

    private static String nextToken(final String value,
                                    final int pos,
                                    final char startChar,
                                    final char endChar) {
        if (value.length() <= pos + 2 || value.charAt(pos) != startChar) {
            // Not valid
            return null;
//            throwException(value, pos, startChar, endChar);
        }

        final StringBuilder sb = new StringBuilder();
        for (int i = pos + 1; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == endChar) {
                return sb.toString();
            } else if (c == '[' || c == ']' || c == '(' || c == ')') {
                // Unexpected char
//                throwException(value, pos, startChar, endChar);
                return null;
            } else {
                sb.append(c);
            }
        }
        // If we get here it wasn't a valid token
//        throwException(value, pos, startChar, endChar);
        return null;
    }

//    private static void throwException(final String value,
//                                       final int pos,
//                                       final char startChar,
//                                       final char endChar) throws MalformedLinkException {
//        throw new MalformedLinkException("Invalid required token in value '" + value
//                                         + "', pos: " + pos
//                                         + ", startChar: '" + startChar
//                                         + "' endChar: '" + endChar + "'");
//    }

    public String getText() {
        // Why are we decoding the plain text part?
        return text;
//        return text;
    }

    public String getHref() {
        return href;
    }

    public String getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    public SvgImage getIcon() {
        return icon;
    }

    public HyperlinkTargetType getTargetType() {
        try {
            return HyperlinkTargetType.valueOf(target.toUpperCase());
        } catch (final RuntimeException ignored) {
            return null;
        }
    }


    private static String decode(final String string) {
        // Hyperlink values are URLEncoded within the link dashboard function, so they need to be decoded when used.
        if (string == null) {
            return null;
        }
        if (GWT.isClient()) {
            return URL.decodeQueryString(string);
        }
        return string;
    }

    private static String encode(final String string) {
        // Hyperlink values are URLEncoded within the link dashboard function, so they need to be decoded when used.
        if (string == null) {
            return null;
        }
        if (GWT.isClient()) {
            return URL.encodeQueryString(string);
        }
        return string;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Hyperlink hyperlink = (Hyperlink) o;
        return Objects.equals(text, hyperlink.text) &&
               Objects.equals(href, hyperlink.href) &&
               Objects.equals(type, hyperlink.type) &&
               Objects.equals(target, hyperlink.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, href, type, target);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (text != null) {
            sb.append("[");
            sb.append(text);
            sb.append("]");
        }
        if (href != null) {
            sb.append("(");
            sb.append(encode(href));
            sb.append(")");
        }
        if (type != null) {
            sb.append("{");
            sb.append(type);
            sb.append("}");
        }
        if (target != null) {
            sb.append("<");
            sb.append(target);
            sb.append(">");
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String text;
        private String href;
        private String type;
        private SvgImage icon;
        private String target;

        private Builder() {
        }

        private Builder(final Hyperlink hyperlink) {
            this.text = hyperlink.text;
            this.href = hyperlink.href;
            this.type = hyperlink.type;
            this.icon = hyperlink.icon;
            this.target = hyperlink.target;
        }

        public Builder text(final String text) {
            this.text = text;
            return this;
        }

        public Builder href(final String href) {
            this.href = href;
            return this;
        }

        public Builder type(final String type) {
            this.type = type;
            return this;
        }

        public Builder icon(final SvgImage icon) {
            this.icon = icon;
            return this;
        }

        public Builder target(final String target) {
            this.target = target;
            return this;
        }

        public Hyperlink build() {
            return new Hyperlink(
                    text,
                    href,
                    type,
                    icon,
                    target);
        }
    }
}
