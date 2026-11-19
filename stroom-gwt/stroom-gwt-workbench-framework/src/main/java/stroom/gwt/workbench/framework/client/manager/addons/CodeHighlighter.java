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

package stroom.gwt.workbench.framework.client.manager.addons;

import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.List;

/// Colours an interaction step, e.g. `userEvent.click(within(<div#workbench-root>).getByRole("button"))`,
/// the way the Interactions addon does: method names, strings and elements each get a colour.
public final class CodeHighlighter {

    private CodeHighlighter() {
        // Static utility
    }

    /// Appends the highlighted code.
    ///
    /// @param builder The builder.
    /// @param code    The code, may be null.
    public static void append(final SafeHtmlBuilder builder, final String code) {
        for (final Token token : tokenize(code)) {
            if (token.kind == Kind.TEXT) {
                builder.appendEscaped(token.text);
            } else {
                builder.appendHtmlConstant("<span class=\"wbm-code-" + token.kind.name().toLowerCase() + "\">")
                        .appendEscaped(token.text)
                        .appendHtmlConstant("</span>");
            }
        }
    }

    /// Splits code into coloured tokens.
    ///
    /// @param code The code, may be null.
    /// @return The tokens, which together are the whole code.
    public static List<Token> tokenize(final String code) {
        final List<Token> tokens = new ArrayList<>();
        if (code == null) {
            return tokens;
        }
        final StringBuilder text = new StringBuilder();
        int i = 0;
        while (i < code.length()) {
            final char chr = code.charAt(i);
            final int end;
            final Kind kind;
            if (chr == '"') {
                end = endOfString(code, i);
                kind = Kind.STRING;
            } else if (chr == '<' && i + 1 < code.length() && Character.isLetter(code.charAt(i + 1))) {
                final int close = code.indexOf('>', i);
                end = close < 0
                        ? code.length()
                        : close + 1;
                kind = Kind.ELEMENT;
            } else if (Character.isLetter(chr) && (i == 0 || !Character.isLetterOrDigit(code.charAt(i - 1)))) {
                int wordEnd = i;
                while (wordEnd < code.length() && Character.isLetterOrDigit(code.charAt(wordEnd))) {
                    wordEnd++;
                }
                end = wordEnd;
                // A word followed by '(' is a function call
                kind = wordEnd < code.length() && code.charAt(wordEnd) == '('
                        ? Kind.METHOD
                        : Kind.TEXT;
            } else {
                end = i + 1;
                kind = Kind.TEXT;
            }

            if (kind == Kind.TEXT) {
                text.append(code, i, end);
            } else {
                flushText(tokens, text);
                tokens.add(new Token(kind, code.substring(i, end)));
            }
            i = end;
        }
        flushText(tokens, text);
        return tokens;
    }

    private static int endOfString(final String code, final int start) {
        int i = start + 1;
        while (i < code.length()) {
            if (code.charAt(i) == '\\') {
                i += 2;
            } else if (code.charAt(i) == '"') {
                return i + 1;
            } else {
                i++;
            }
        }
        return code.length();
    }

    private static void flushText(final List<Token> tokens, final StringBuilder text) {
        if (text.length() > 0) {
            tokens.add(new Token(Kind.TEXT, text.toString()));
            text.setLength(0);
        }
    }


    // --------------------------------------------------------------------------------


    /// The kinds of token, each with its own colour.
    public enum Kind {
        /// Plain code.
        TEXT,
        /// A function or method name, e.g. `click`.
        METHOD,
        /// A string, e.g. `"button"`.
        STRING,
        /// An element, e.g. `<div#workbench-root>`.
        ELEMENT
    }


    // --------------------------------------------------------------------------------


    /// Part of the code.
    public static final class Token {

        private final Kind kind;
        private final String text;

        private Token(final Kind kind, final String text) {
            this.kind = kind;
            this.text = text;
        }

        /// @return The kind of token.
        public Kind getKind() {
            return kind;
        }

        /// @return The token's text.
        public String getText() {
            return text;
        }
    }
}
