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

package stroom.gwt.workbench.client.app.rest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// A small, strict JSON parser (RFC 8259), so that request bodies can be compared by value
/// (ignoring white space and the order of object members), e.g. for
/// [RequestMatcher#withJsonBody(String)]. It is plain Java, so works both in GWT and in JVM tests.
///
/// Objects become [Map]s, arrays [List]s, `true`/`false` [Boolean]s and `null` `null`. Numbers
/// become [BigDecimal]s without trailing zeros, so they compare exactly: `1`, `1.0` and `10e-1` are
/// equal, but two long ids above 2^53 that a `double` can't tell apart are not. Anything JSON
/// doesn't allow is refused, e.g. `+1`, `01`, `1.`, `1e`, a raw control character in a string or
/// an unknown escape such as `\x`.
public final class JsonValues {

    private final String text;
    private int pos;

    private JsonValues(final String text) {
        this.text = text;
    }

    /// Parses JSON text.
    ///
    /// @param json The JSON text.
    /// @return The value, see the class description for the types used.
    /// @throws IllegalArgumentException If the text isn't valid JSON.
    public static Object parse(final String json) {
        Objects.requireNonNull(json, "json");
        final JsonValues parser = new JsonValues(json);
        parser.skipWhiteSpace();
        final Object value = parser.readValue();
        parser.skipWhiteSpace();
        if (parser.pos != json.length()) {
            throw parser.error("Unexpected text after the JSON value");
        }
        return value;
    }

    /// Compares two JSON texts by value.
    ///
    /// @param json1 Some JSON, or null.
    /// @param json2 Some JSON, or null.
    /// @return True if both are null, or both are valid JSON with equal values. False if only one
    /// is null or either isn't valid JSON.
    public static boolean jsonEquals(final String json1, final String json2) {
        if (json1 == null || json2 == null) {
            return json1 == null && json2 == null;
        }
        try {
            return Objects.equals(parse(json1), parse(json2));
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    /// Whether a JSON value contains another, as Jest's `toMatchObject` compares: an expected
    /// object matches an object that has (at least) its members, each containing the expected
    /// member's value; an expected array matches an array of the same length whose items each
    /// contain the expected item; any other expected value must be equal. E.g. `{"a": 1, "b": [{"c":
    /// 2, "d": 3}]}` contains `{"b": [{"c": 2}]}`, but not `{"b": []}` or `{"a": "1"}`.
    ///
    /// @param actual   The actual value, as returned by [#parse(String)].
    /// @param expected The expected value, as returned by [#parse(String)].
    /// @return True if the actual value contains the expected one.
    public static boolean contains(final Object actual, final Object expected) {
        if (expected instanceof Map) {
            if (!(actual instanceof Map)) {
                return false;
            }
            final Map<?, ?> actualMap = (Map<?, ?>) actual;
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) expected).entrySet()) {
                if (!actualMap.containsKey(entry.getKey())
                        || !contains(actualMap.get(entry.getKey()), entry.getValue())) {
                    return false;
                }
            }
            return true;
        } else if (expected instanceof List) {
            if (!(actual instanceof List)) {
                return false;
            }
            final List<?> actualList = (List<?>) actual;
            final List<?> expectedList = (List<?>) expected;
            if (actualList.size() != expectedList.size()) {
                return false;
            }
            for (int i = 0; i < expectedList.size(); i++) {
                if (!contains(actualList.get(i), expectedList.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(actual, expected);
    }

    /// Whether some JSON text contains other JSON, see [#contains(Object, Object)].
    ///
    /// @param actualJson   The actual JSON, or null.
    /// @param expectedJson The expected JSON.
    /// @return True if the actual JSON is valid and contains the expected JSON. False if it is
    /// null or not valid JSON.
    /// @throws IllegalArgumentException If the expected JSON isn't valid.
    public static boolean jsonContains(final String actualJson, final String expectedJson) {
        final Object expected = parse(expectedJson);
        if (actualJson == null) {
            return false;
        }
        try {
            return contains(parse(actualJson), expected);
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    private Object readValue() {
        if (pos >= text.length()) {
            throw error("Unexpected end of JSON");
        }
        final char chr = text.charAt(pos);
        switch (chr) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
                readLiteral("true");
                return Boolean.TRUE;
            case 'f':
                readLiteral("false");
                return Boolean.FALSE;
            case 'n':
                readLiteral("null");
                return null;
            default:
                return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        final Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skipWhiteSpace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhiteSpace();
            if (peek() != '"') {
                throw error("Expected a member name");
            }
            final String name = readString();
            skipWhiteSpace();
            expect(':');
            skipWhiteSpace();
            map.put(name, readValue());
            skipWhiteSpace();
            final char next = next();
            if (next == '}') {
                return map;
            } else if (next != ',') {
                throw error("Expected ',' or '}'");
            }
        }
    }

    private List<Object> readArray() {
        final List<Object> list = new ArrayList<>();
        pos++;
        skipWhiteSpace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhiteSpace();
            list.add(readValue());
            skipWhiteSpace();
            final char next = next();
            if (next == ']') {
                return list;
            } else if (next != ',') {
                throw error("Expected ',' or ']'");
            }
        }
    }

    private String readString() {
        expect('"');
        final StringBuilder sb = new StringBuilder();
        while (true) {
            final char chr = next();
            if (chr == '"') {
                return sb.toString();
            } else if (chr == '\\') {
                final char escaped = next();
                switch (escaped) {
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        sb.append(readUnicodeEscape());
                        break;
                    case '"':
                    case '\\':
                    case '/':
                        sb.append(escaped);
                        break;
                    default:
                        throw error("Bad escape '\\" + escaped + "'");
                }
            } else if (chr < ' ') {
                throw error("Unescaped control character in a string");
            } else {
                sb.append(chr);
            }
        }
    }

    private char readUnicodeEscape() {
        if (pos + 4 > text.length()) {
            throw error("Bad unicode escape");
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            final int digit = Character.digit(text.charAt(pos + i), 16);
            if (digit < 0) {
                throw error("Bad unicode escape");
            }
            value = value * 16 + digit;
        }
        pos += 4;
        return (char) value;
    }

    private BigDecimal readNumber() {
        // number = [ "-" ] int [ frac ] [ exp ], int = "0" / digit1-9 *DIGIT
        final int start = pos;
        if (pos < text.length() && text.charAt(pos) == '-') {
            pos++;
        }
        if (pos < text.length() && text.charAt(pos) == '0') {
            pos++;
        } else if (pos < text.length() && isDigit(text.charAt(pos))) {
            skipDigits();
        } else {
            throw pos < text.length()
                    ? error("Unexpected character '" + text.charAt(pos) + "'")
                    : error("Unexpected end of JSON");
        }
        if (pos < text.length() && text.charAt(pos) == '.') {
            pos++;
            requireDigits(start);
        }
        if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            pos++;
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                pos++;
            }
            requireDigits(start);
        }
        final String number = text.substring(start, pos);
        try {
            return normalise(new BigDecimal(number));
        } catch (final NumberFormatException | ArithmeticException e) {
            throw error("Bad number '" + number + "'");
        }
    }

    /// @param number A number.
    /// @return The number without trailing zeros, so that equal numbers are [Object#equals].
    static BigDecimal normalise(final BigDecimal number) {
        // Zero is special-cased as some stripTrailingZeros implementations leave 0.0 alone
        return number.signum() == 0
                ? BigDecimal.ZERO
                : number.stripTrailingZeros();
    }

    private void requireDigits(final int numberStart) {
        if (pos >= text.length() || !isDigit(text.charAt(pos))) {
            throw error("Bad number '" + text.substring(numberStart, Math.min(pos + 1, text.length())) + "'");
        }
        skipDigits();
    }

    private void skipDigits() {
        while (pos < text.length() && isDigit(text.charAt(pos))) {
            pos++;
        }
    }

    private static boolean isDigit(final char chr) {
        return chr >= '0' && chr <= '9';
    }

    private void readLiteral(final String literal) {
        if (!text.startsWith(literal, pos)) {
            throw error("Expected '" + literal + "'");
        }
        pos += literal.length();
    }

    private void skipWhiteSpace() {
        // JSON's white space characters
        while (pos < text.length() && " \t\n\r".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("Unexpected end of JSON");
        }
        return text.charAt(pos);
    }

    private char next() {
        final char chr = peek();
        pos++;
        return chr;
    }

    private void expect(final char expected) {
        if (next() != expected) {
            throw error("Expected '" + expected + "'");
        }
    }

    private IllegalArgumentException error(final String message) {
        return new IllegalArgumentException(message + " at position " + pos + " of JSON: " + text);
    }
}
