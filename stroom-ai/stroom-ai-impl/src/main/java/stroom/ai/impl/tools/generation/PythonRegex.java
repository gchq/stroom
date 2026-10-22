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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.PyText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// A regular expression read as Python's `re` module reads it (its `_parser.py`), for what the Stroom MCP server's
/// generator asks of an extraction's regex: how many capture groups it has, or, if Python can't compile it, the
/// message Python gives (`missing ), unterminated subpattern at position 0`). The generator reports either, so the
/// count and the wording must be Python's, not Java's: an XPath regex such as `\p{L}+` doesn't compile in Python
/// (`bad escape \p at position 0`), and the server said so.
final class PythonRegex {

    private static final String SPECIAL_CHARS = ".\\[{()*+?^$|";
    private static final String REPEAT_CHARS = "*+?{";
    private static final String DIGITS = "0123456789";
    private static final String OCT_DIGITS = "01234567";
    private static final String HEX_DIGITS = "0123456789abcdefABCDEF";
    private static final String WHITESPACE = " \t\n\r\u000b\f";
    private static final String FLAGS = "iLmsxatu";
    private static final Map<String, Integer> ESCAPES = Map.of("\\a", 7, "\\b", 8, "\\f", 12, "\\n", 10,
            "\\r", 13, "\\t", 9, "\\v", 11, "\\\\", 92);
    private static final Set<String> CATEGORIES_AT = Set.of("\\A", "\\b", "\\B", "\\Z");
    private static final Set<String> CATEGORIES_IN = Set.of("\\d", "\\D", "\\s", "\\S", "\\w", "\\W");
    private static final int FLAG_TEMPLATE = 1;
    private static final int FLAG_LOCALE = 4;
    private static final int FLAG_VERBOSE = 64;
    private static final int FLAG_DEBUG = 128;
    private static final int FLAG_ASCII = 256;
    private static final int FLAG_UNICODE = 32;
    private static final int TYPE_FLAGS = FLAG_ASCII | FLAG_LOCALE | FLAG_UNICODE;
    private static final int GLOBAL_FLAGS = FLAG_DEBUG | FLAG_TEMPLATE;
    private static final int MAX_REPEAT = Integer.MAX_VALUE;

    private final int[] text;
    private final String pattern;
    private int index;
    private String next;
    private int flags;
    private int groups = 1;
    private final Set<Integer> open = new HashSet<>();
    private final Map<String, Integer> named = new HashMap<>();
    private final Map<Integer, Integer> groupReferences = new HashMap<>();

    private PythonRegex(final String pattern) {
        this.pattern = pattern;
        this.text = pattern.codePoints().toArray();
        advance();
    }

    /// @param pattern A regular expression.
    /// @return The number of capture groups Python's `re.compile(pattern).groups` gives.
    /// @throws PythonRegexException with Python's message if Python can't compile it.
    static int groups(final String pattern) {
        final PythonRegex parser = new PythonRegex(pattern);
        parser.parseSub(false, 0);
        if (parser.next != null) {
            throw parser.error("unbalanced parenthesis", 0);
        }
        for (final Map.Entry<Integer, Integer> reference : parser.groupReferences.entrySet()) {
            if (reference.getKey() >= parser.groups) {
                throw parser.errorAt("invalid group reference " + reference.getKey(), reference.getValue());
            }
        }
        return parser.groups - 1;
    }

    // --- the tokenizer ---

    private void advance() {
        if (index >= text.length) {
            next = null;
            return;
        }
        String ch = Character.toString(text[index]);
        if ("\\".equals(ch)) {
            if (index + 1 >= text.length) {
                throw errorAt("bad escape (end of pattern)", text.length - 1);
            }
            ch += Character.toString(text[index + 1]);
            index++;
        }
        index++;
        next = ch;
    }

    private boolean match(final String ch) {
        if (ch.equals(next)) {
            advance();
            return true;
        }
        return false;
    }

    private String get() {
        final String current = next;
        advance();
        return current;
    }

    private String getWhile(final int n, final String charset) {
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (next == null || next.codePointCount(0, next.length()) != 1 || charset.indexOf(next) < 0) {
                break;
            }
            result.append(next);
            advance();
        }
        return result.toString();
    }

    private String getUntil(final String terminator, final String name) {
        final StringBuilder result = new StringBuilder();
        while (true) {
            final String ch = next;
            advance();
            if (ch == null) {
                if (result.isEmpty()) {
                    throw error("missing " + name, 0);
                }
                throw error("missing " + terminator + ", unterminated name", length(result.toString()));
            }
            if (ch.equals(terminator)) {
                if (result.isEmpty()) {
                    throw error("missing " + name, 1);
                }
                break;
            }
            result.append(ch);
        }
        return result.toString();
    }

    private int tell() {
        return index - (next == null
                ? 0
                : length(next));
    }

    private void seek(final int to) {
        index = to;
        advance();
    }

    private PythonRegexException error(final String message, final int offset) {
        return errorAt(message, tell() - offset);
    }

    // re.error's message: the place, and the line and column when the pattern has more than one line.
    private PythonRegexException errorAt(final String message, final int position) {
        String full = message + " at position " + position;
        if (pattern.indexOf('\n') >= 0) {
            int line = 1;
            int lastNewline = -1;
            for (int i = 0; i < position && i < text.length; i++) {
                if (text[i] == '\n') {
                    line++;
                    lastNewline = i;
                }
            }
            full += " (line " + line + ", column " + (position - lastNewline) + ")";
        }
        return new PythonRegexException(full);
    }

    private static int length(final String text) {
        return text.codePointCount(0, text.length());
    }

    private static boolean in(final String ch, final String set) {
        return ch != null && length(ch) == 1 && set.contains(ch);
    }

    // --- the parser: what matters for the group count and the errors ---

    private void parseSub(final boolean verbose, final int nested) {
        boolean sub = verbose;
        boolean first = nested == 0;
        while (true) {
            parse(sub, nested + 1, first);
            first = false;
            if (!match("|")) {
                break;
            }
            if (nested == 0) {
                sub = (flags & FLAG_VERBOSE) != 0;
            }
        }
    }

    private void parse(final boolean verboseGiven, final int nested, final boolean first) {
        boolean verbose = verboseGiven;
        final List<Item> items = new ArrayList<>();
        while (true) {
            final String current = next;
            if (current == null || "|".equals(current) || ")".equals(current)) {
                break;
            }
            get();
            if (verbose) {
                if (in(current, WHITESPACE)) {
                    continue;
                }
                if ("#".equals(current)) {
                    while (true) {
                        final String skipped = get();
                        if (skipped == null || "\n".equals(skipped)) {
                            break;
                        }
                    }
                    continue;
                }
            }
            if (current.startsWith("\\")) {
                items.add(escape(current));
            } else if (!in(current, SPECIAL_CHARS)) {
                items.add(Item.OTHER);
            } else if ("[".equals(current)) {
                characterSet();
                items.add(Item.OTHER);
            } else if (in(current, REPEAT_CHARS)) {
                if (!repeat(current, items)) {
                    items.add(Item.OTHER);
                }
            } else if (".".equals(current)) {
                items.add(Item.OTHER);
            } else if ("(".equals(current)) {
                final Item item = group(verbose, nested, first && items.isEmpty());
                if (item == Item.GLOBAL_FLAGS) {
                    verbose = (flags & FLAG_VERBOSE) != 0;
                } else if (item != null) {
                    items.add(item);
                }
            } else {
                items.add(Item.AT);
            }
        }
    }

    // A quantifier: false if a brace isn't one (then it is a literal).
    private boolean repeat(final String current, final List<Item> items) {
        final int here = tell();
        if ("{".equals(current)) {
            if ("}".equals(next)) {
                return false;
            }
            final StringBuilder lo = new StringBuilder();
            final StringBuilder hi = new StringBuilder();
            while (in(next, DIGITS)) {
                lo.append(get());
            }
            if (match(",")) {
                while (in(next, DIGITS)) {
                    hi.append(get());
                }
            } else {
                hi.append(lo);
            }
            if (!match("}")) {
                seek(here);
                return false;
            }
            final long min = lo.isEmpty()
                    ? 0
                    : Long.parseLong(lo.toString());
            if (min >= MAX_REPEAT) {
                throw new PythonRegexException("the repetition number is too large");
            }
            if (!hi.isEmpty()) {
                final long max = Long.parseLong(hi.toString());
                if (max >= MAX_REPEAT) {
                    throw new PythonRegexException("the repetition number is too large");
                }
                if (max < min) {
                    throw error("min repeat greater than max repeat", tell() - here);
                }
            }
        }
        final Item last = items.isEmpty()
                ? null
                : items.getLast();
        if (last == null || last == Item.AT) {
            throw error("nothing to repeat", tell() - here + length(current));
        }
        if (last == Item.REPEAT) {
            throw error("multiple repeat", tell() - here + length(current));
        }
        if (!match("?")) {
            match("+");
        }
        items.set(items.size() - 1, Item.REPEAT);
        return true;
    }

    private Item group(final boolean verbose, final int nested, final boolean first) {
        final int start = tell() - 1;
        boolean capture = true;
        String name = null;
        int addFlags = 0;
        int delFlags = 0;
        if (match("?")) {
            final String ch = get();
            if (ch == null) {
                throw error("unexpected end of pattern", 0);
            }
            switch (ch) {
                case "P" -> {
                    if (match("<")) {
                        name = getUntil(">", "group name");
                        checkGroupName(name, 1);
                    } else if (match("=")) {
                        name = getUntil(")", "group name");
                        checkGroupName(name, 1);
                        final Integer id = named.get(name);
                        if (id == null) {
                            throw error("unknown group name " + PyText.repr(name), length(name) + 1);
                        }
                        if (open.contains(id)) {
                            throw error("cannot refer to an open group", length(name) + 1);
                        }
                        return Item.OTHER;
                    } else {
                        final String after = get();
                        if (after == null) {
                            throw error("unexpected end of pattern", 0);
                        }
                        throw error("unknown extension ?P" + after, length(after) + 2);
                    }
                }
                case ":" -> capture = false;
                case "#" -> {
                    while (true) {
                        if (next == null) {
                            throw error("missing ), unterminated comment", tell() - start);
                        }
                        if (")".equals(get())) {
                            break;
                        }
                    }
                    return null;
                }
                case "=", "!", "<" -> {
                    if ("<".equals(ch)) {
                        final String after = get();
                        if (after == null) {
                            throw error("unexpected end of pattern", 0);
                        }
                        if (!"=".equals(after) && !"!".equals(after)) {
                            throw error("unknown extension ?<" + after, length(after) + 2);
                        }
                    }
                    parseSub(verbose, nested + 1);
                    if (!match(")")) {
                        throw error("missing ), unterminated subpattern", tell() - start);
                    }
                    return Item.OTHER;
                }
                case "(" -> {
                    conditional(verbose, nested, start);
                    return Item.OTHER;
                }
                case ">" -> capture = false;
                default -> {
                    if (in(ch, FLAGS) || "-".equals(ch)) {
                        final int[] parsed = parseFlags(ch);
                        if (parsed == null) {
                            if (!first) {
                                throw error("global flags not at the start of the expression", tell() - start);
                            }
                            return Item.GLOBAL_FLAGS;
                        }
                        addFlags = parsed[0];
                        delFlags = parsed[1];
                        capture = false;
                    } else {
                        throw error("unknown extension ?" + ch, length(ch) + 1);
                    }
                }
            }
        }
        Integer opened = null;
        if (capture) {
            opened = groups++;
            if (name != null) {
                named.put(name, opened);
            }
            open.add(opened);
        }
        final boolean subVerbose = (verbose || (addFlags & FLAG_VERBOSE) != 0) && (delFlags & FLAG_VERBOSE) == 0;
        parseSub(subVerbose, nested + 1);
        if (!match(")")) {
            throw error("missing ), unterminated subpattern", tell() - start);
        }
        if (opened != null) {
            open.remove(opened);
        }
        return Item.OTHER;
    }

    private void conditional(final boolean verbose, final int nested, final int start) {
        final String condition = getUntil(")", "group name");
        final boolean decimal = condition.chars().allMatch(ch -> ch >= '0' && ch <= '9');
        if (!decimal) {
            checkGroupName(condition, 1);
            if (!named.containsKey(condition)) {
                throw error("unknown group name " + PyText.repr(condition), length(condition) + 1);
            }
        } else {
            final long group = Long.parseLong(condition);
            if (group == 0) {
                throw error("bad group number", length(condition) + 1);
            }
            if (group >= Integer.MAX_VALUE) {
                throw error("invalid group reference " + group, length(condition) + 1);
            }
            groupReferences.putIfAbsent((int) group, tell() - length(condition) - 1);
        }
        parse(verbose, nested + 1, false);
        if (match("|")) {
            parse(verbose, nested + 1, false);
            if ("|".equals(next)) {
                throw error("conditional backref with more than two branches", 0);
            }
        }
        if (!match(")")) {
            throw error("missing ), unterminated subpattern", tell() - start);
        }
    }

    private void checkGroupName(final String name, final int offset) {
        if (!isIdentifier(name)) {
            throw error("bad character in group name " + PyText.repr(name), length(name) + offset);
        }
    }

    private static boolean isIdentifier(final String name) {
        if (name.isEmpty() || !(Character.isUnicodeIdentifierStart(name.codePointAt(0)) || name.charAt(0) == '_')) {
            return false;
        }
        return name.codePoints().skip(1).allMatch(Character::isUnicodeIdentifierPart);
    }

    // Inline flags: null for global ones ((?i) at the start), else those turned on and off for a group.
    private int[] parseFlags(final String given) {
        String ch = given;
        int add = 0;
        if (!"-".equals(ch)) {
            while (true) {
                final int flag = flag(ch);
                if ("L".equals(ch)) {
                    throw error("bad inline flags: cannot use 'L' flag with a str pattern", 0);
                }
                add |= flag;
                if ((flag & TYPE_FLAGS) != 0 && (add & TYPE_FLAGS) != flag) {
                    throw error("bad inline flags: flags 'a', 'u' and 'L' are incompatible", 0);
                }
                ch = get();
                if (ch == null) {
                    throw error("missing -, : or )", 0);
                }
                if (")".equals(ch) || "-".equals(ch) || ":".equals(ch)) {
                    break;
                }
                if (!in(ch, FLAGS)) {
                    throw error(Character.isLetter(ch.codePointAt(0))
                            ? "unknown flag"
                            : "missing -, : or )", length(ch));
                }
            }
        }
        if (")".equals(ch)) {
            flags |= add;
            return null;
        }
        if ((add & GLOBAL_FLAGS) != 0) {
            throw error("bad inline flags: cannot turn on global flag", 1);
        }
        int del = 0;
        if ("-".equals(ch)) {
            ch = get();
            if (ch == null) {
                throw error("missing flag", 0);
            }
            if (!in(ch, FLAGS)) {
                throw error(Character.isLetter(ch.codePointAt(0))
                        ? "unknown flag"
                        : "missing flag", length(ch));
            }
            while (true) {
                final int flag = flag(ch);
                if ((flag & TYPE_FLAGS) != 0) {
                    throw error("bad inline flags: cannot turn off flags 'a', 'u' and 'L'", 0);
                }
                del |= flag;
                ch = get();
                if (ch == null) {
                    throw error("missing :", 0);
                }
                if (":".equals(ch)) {
                    break;
                }
                if (!in(ch, FLAGS)) {
                    throw error(Character.isLetter(ch.codePointAt(0))
                            ? "unknown flag"
                            : "missing :", length(ch));
                }
            }
        }
        if ((del & GLOBAL_FLAGS) != 0) {
            throw error("bad inline flags: cannot turn off global flag", 1);
        }
        if ((add & del) != 0) {
            throw error("bad inline flags: flag turned on and off", 1);
        }
        return new int[]{add, del};
    }

    private static int flag(final String ch) {
        return switch (ch) {
            case "i" -> 2;
            case "L" -> FLAG_LOCALE;
            case "m" -> 8;
            case "s" -> 16;
            case "x" -> FLAG_VERBOSE;
            case "a" -> FLAG_ASCII;
            case "t" -> FLAG_TEMPLATE;
            case "u" -> FLAG_UNICODE;
            default -> 0;
        };
    }

    private void characterSet() {
        final int here = tell() - 1;
        match("^");
        boolean any = false;
        while (true) {
            final String current = get();
            if (current == null) {
                throw error("unterminated character set", tell() - here);
            }
            if ("]".equals(current) && any) {
                break;
            }
            final Member first = current.startsWith("\\")
                    ? classEscape(current)
                    : Member.literal(current.codePointAt(0));
            if (match("-")) {
                final String that = get();
                if (that == null) {
                    throw error("unterminated character set", tell() - here);
                }
                if ("]".equals(that)) {
                    break;
                }
                final Member second = that.startsWith("\\")
                        ? classEscape(that)
                        : Member.literal(that.codePointAt(0));
                if (!first.isLiteral() || !second.isLiteral() || second.codePoint() < first.codePoint()) {
                    throw error("bad character range " + current + "-" + that, length(current) + 1 + length(that));
                }
            }
            any = true;
        }
    }

    // An escape inside a character set.
    private Member classEscape(final String given) {
        String escape = given;
        if (ESCAPES.containsKey(escape)) {
            return Member.literal(ESCAPES.get(escape));
        }
        if (CATEGORIES_IN.contains(escape)) {
            return Member.CATEGORY;
        }
        final String c = escape.substring(1);
        switch (c) {
            case "x" -> {
                escape += getWhile(2, HEX_DIGITS);
                if (length(escape) != 4) {
                    throw error("incomplete escape " + escape, length(escape));
                }
                return Member.literal(Integer.parseInt(escape.substring(2), 16));
            }
            case "u", "U" -> {
                return Member.literal(unicodeEscape(escape, "u".equals(c)
                        ? 4
                        : 8));
            }
            case "N" -> {
                return Member.literal(namedEscape());
            }
            default -> {
                if (in(c, OCT_DIGITS)) {
                    escape += getWhile(2, OCT_DIGITS);
                    final int value = Integer.parseInt(escape.substring(1), 8);
                    if (value > 0377) {
                        throw error("octal escape value " + escape + " outside of range 0-0o377", length(escape));
                    }
                    return Member.literal(value);
                }
                if (in(c, DIGITS) || isAsciiLetter(c)) {
                    throw error("bad escape " + escape, length(escape));
                }
                return Member.literal(c.codePointAt(0));
            }
        }
    }

    // An escape outside a character set.
    private Item escape(final String given) {
        String escape = given;
        if (CATEGORIES_AT.contains(escape)) {
            return Item.AT;
        }
        if (CATEGORIES_IN.contains(escape) || ESCAPES.containsKey(escape)) {
            return Item.OTHER;
        }
        final String c = escape.substring(1);
        switch (c) {
            case "x" -> {
                escape += getWhile(2, HEX_DIGITS);
                if (length(escape) != 4) {
                    throw error("incomplete escape " + escape, length(escape));
                }
                return Item.OTHER;
            }
            case "u", "U" -> {
                unicodeEscape(escape, "u".equals(c)
                        ? 4
                        : 8);
                return Item.OTHER;
            }
            case "N" -> {
                namedEscape();
                return Item.OTHER;
            }
            case "0" -> {
                getWhile(2, OCT_DIGITS);
                return Item.OTHER;
            }
            default -> {
                if (in(c, DIGITS)) {
                    return groupReference(escape);
                }
                if (isAsciiLetter(c)) {
                    throw error("bad escape " + escape, length(escape));
                }
                return Item.OTHER;
            }
        }
    }

    // \1 to \99: a group reference, unless three octal digits make an octal escape.
    private Item groupReference(final String given) {
        String escape = given;
        if (in(next, DIGITS)) {
            escape += get();
            if (in(escape.substring(1, 2), OCT_DIGITS) && in(escape.substring(2, 3), OCT_DIGITS)
                && in(next, OCT_DIGITS)) {
                escape += get();
                final int value = Integer.parseInt(escape.substring(1), 8);
                if (value > 0377) {
                    throw error("octal escape value " + escape + " outside of range 0-0o377", length(escape));
                }
                return Item.OTHER;
            }
        }
        final int group = Integer.parseInt(escape.substring(1));
        if (group < groups) {
            if (open.contains(group)) {
                throw error("cannot refer to an open group", length(escape));
            }
            return Item.OTHER;
        }
        throw error("invalid group reference " + group, length(escape) - 1);
    }

    private int unicodeEscape(final String given, final int digits) {
        final String escape = given + getWhile(digits, HEX_DIGITS);
        if (length(escape) != digits + 2) {
            throw error("incomplete escape " + escape, length(escape));
        }
        final long value = Long.parseLong(escape.substring(2), 16);
        if (value > Character.MAX_CODE_POINT) {
            throw error("bad escape " + escape, length(escape));
        }
        return (int) value;
    }

    private int namedEscape() {
        if (!match("{")) {
            throw error("missing {", 0);
        }
        final String name = getUntil("}", "character name");
        try {
            return Character.codePointOf(name);
        } catch (final IllegalArgumentException e) {
            throw error("undefined character name " + PyText.repr(name), length(name) + 4);
        }
    }

    private static boolean isAsciiLetter(final String c) {
        return c.length() == 1 && ((c.charAt(0) >= 'a' && c.charAt(0) <= 'z') || (c.charAt(0) >= 'A'
                                                                                  && c.charAt(0) <= 'Z'));
    }


    // --------------------------------------------------------------------------------


    /// A regular expression Python's `re` can't compile, with Python's message.
    static final class PythonRegexException extends IllegalArgumentException {

        /// @param message Python's message.
        PythonRegexException(final String message) {
            super(message);
        }
    }


    // --------------------------------------------------------------------------------


    // What an item of a pattern is, as far as a quantifier after it cares.
    private enum Item {
        AT,
        REPEAT,
        OTHER,
        GLOBAL_FLAGS
    }


    // --------------------------------------------------------------------------------


    // A member of a character set: a literal character (which may start or end a range) or a category.
    private record Member(int codePoint, boolean isLiteral) {

        private static final Member CATEGORY = new Member(-1, false);

        /// @param codePoint A character.
        /// @return It as a member.
        static Member literal(final int codePoint) {
            return new Member(codePoint, true);
        }
    }
}
