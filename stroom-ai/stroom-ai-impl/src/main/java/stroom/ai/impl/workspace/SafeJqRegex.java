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

package stroom.ai.impl.workspace;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import net.thisptr.jackson.jq.Expression;
import net.thisptr.jackson.jq.PathOutput;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Version;
import net.thisptr.jackson.jq.exception.JsonQueryException;
import net.thisptr.jackson.jq.path.Path;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// jq's two regular expression built-ins, {@code _match_impl/3} and {@code _sub_impl/3}, on which jq defines
/// {@code test}, {@code match}, {@code capture}, {@code scan}, {@code split}, {@code splits}, {@code sub} and
/// {@code gsub}. jackson-jq's own versions match with a library that can't be stopped part way, so these match with
/// Java's regular expressions over a [DeadlineCharSequence] instead, as the grep tool does: a pattern that
/// backtracks badly is stopped at the tool call's time limit. A pattern is limited in length as grep's is.
///
/// The flags are jq's: {@code g} (every match), {@code i} (ignore case), {@code x} (extended, with comments),
/// {@code n} (ignore empty matches), {@code s} (single line: {@code ^} and {@code $} match only at the ends, as they
/// do anyway), {@code m} (. matches a line end, as Oniguruma means it) and {@code p} (both). {@code l} (longest
/// match) is not available.
final class SafeJqRegex {

    // Compiled patterns, by flags and text, as a query usually runs the same pattern on many values.
    private static final int MAX_CACHED_PATTERNS = 256;
    private static final Map<String, CompiledPattern> PATTERNS = new ConcurrentHashMap<>();

    private SafeJqRegex() {
        // Static only.
    }

    /// {@code _match_impl(re; flags; test)}: whether the input matches, if test is true, or else an array of its
    /// matches, each {@code {offset, length, string, captures}}, with offsets and lengths in code points as jq gives
    /// them.
    static void match(final Scope scope,
                      final List<Expression> args,
                      final JsonNode in,
                      final Path path,
                      final PathOutput output,
                      final Version version) throws JsonQueryException {
        final String text = inputText("test, match, capture, scan and split", in);
        args.get(2).apply(scope, in, test -> {
            if (!test.isBoolean()) {
                throw new JsonQueryException("_match_impl/3: the third argument must be a boolean.");
            }
            args.get(1).apply(scope, in, flags -> {
                if (!flags.isTextual() && !flags.isNull()) {
                    throw new JsonQueryException("The flags of a regular expression must be a string, e.g. \"gi\".");
                }
                args.get(0).apply(scope, in, regex -> {
                    final CompiledPattern pattern = compile(regex, flags.isNull()
                            ? null
                            : flags.textValue());
                    output.emit(test.booleanValue()
                            ? BooleanNode.valueOf(!matches(pattern, text, true).isEmpty())
                            : matchObjects(pattern, text), null);
                });
            });
        });
    }

    /// {@code _sub_impl(re; replacement; flags)}: the input with its first match (or, with the {@code g} flag,
    /// every match) replaced by the replacement, which is given an object of the match's named captures.
    static void sub(final Scope scope,
                    final List<Expression> args,
                    final JsonNode in,
                    final Path path,
                    final PathOutput output,
                    final Version version) throws JsonQueryException {
        final String text = inputText("sub and gsub", in);
        args.get(0).apply(scope, in, regex -> args.get(2).apply(scope, in, flags -> {
            if (!flags.isTextual()) {
                throw new JsonQueryException("The flags of a regular expression must be a string, e.g. \"gi\".");
            }
            final CompiledPattern pattern = compile(regex, flags.textValue());
            final List<JsonNode> parts = new ArrayList<>();
            int textStart = 0;
            for (final Matcher matcher : matches(pattern, text, false)) {
                parts.add(TextNode.valueOf(text.substring(textStart, matcher.start())));
                parts.add(namedCaptures(pattern, matcher));
                textStart = matcher.end();
            }
            parts.add(TextNode.valueOf(text.substring(textStart)));
            replaceAndJoin(scope, args.get(1), parts, 0, new ArrayDeque<>(), output);
        }));
    }

    // The input, which must be a string.
    private static String inputText(final String functions, final JsonNode in) throws JsonQueryException {
        if (!in.isTextual()) {
            throw new JsonQueryException(functions + " can only be applied to strings, not "
                                         + in.getNodeType().name().toLowerCase(Locale.ROOT) + ".");
        }
        return in.textValue();
    }

    private static CompiledPattern compile(final JsonNode regex, final String flags) throws JsonQueryException {
        if (!regex.isTextual()) {
            throw new JsonQueryException("A regular expression must be a string.");
        }
        final String text = regex.textValue();
        if (text.length() > SafePattern.MAX_PATTERN_LENGTH) {
            throw new JsonQueryException("A regular expression may be at most " + SafePattern.MAX_PATTERN_LENGTH
                                         + " characters.");
        }
        final String key = flags + "/" + text;
        final CompiledPattern cached = PATTERNS.get(key);
        if (cached != null) {
            return cached;
        }
        final String flagsText = NullSafe.string(flags);
        final CompiledPattern compiled = new CompiledPattern(compileWithFlags(text, flagsText),
                flagsText.indexOf('g') >= 0,
                flagsText.indexOf('n') >= 0);
        if (PATTERNS.size() >= MAX_CACHED_PATTERNS) {
            PATTERNS.clear();
        }
        PATTERNS.put(key, compiled);
        return compiled;
    }

    private static Pattern compileWithFlags(final String regex, final String flags) throws JsonQueryException {
        int javaFlags = 0;
        for (final char flag : flags.toCharArray()) {
            switch (flag) {
                case 'g', 'n', 's' -> {
                    // 'g' and 'n' are applied as matches are found; 's' is how Java matches anyway.
                }
                case 'i' -> javaFlags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                case 'x' -> javaFlags |= Pattern.COMMENTS;
                // Oniguruma's 'multiline' lets . match a line end.
                case 'm', 'p' -> javaFlags |= Pattern.DOTALL;
                case 'l' -> throw new JsonQueryException("The l flag (longest match) is not available here.");
                default -> throw new JsonQueryException("'" + flags + "' are not valid regular expression flags. "
                                                        + "Use g, i, x, n, s, m or p.");
            }
        }
        try {
            return Pattern.compile(regex, javaFlags);
        } catch (final PatternSyntaxException e) {
            throw new JsonQueryException("'" + regex + "' is not a valid regular expression: "
                                         + e.getDescription() + ".");
        }
    }

    // The matches in the text: the first, or every one with the 'g' flag, leaving out empty ones with the 'n'
    // flag. Each is a matcher frozen at that match.
    private static List<Matcher> matches(final CompiledPattern pattern, final String text, final boolean firstOnly) {
        final List<Matcher> matches = new ArrayList<>();
        final DeadlineCharSequence input = new DeadlineCharSequence(text);
        int from = 0;
        while (from <= text.length()) {
            final Matcher matcher = pattern.pattern().matcher(input);
            if (!matcher.find(from)) {
                break;
            }
            final boolean empty = matcher.end() == matcher.start();
            if (!(empty && pattern.ignoreEmpty())) {
                matches.add(matcher);
                if (firstOnly || !pattern.global()) {
                    break;
                }
            }
            from = nextSearchFrom(text, matcher);
            SafeJq.tick();
        }
        return matches;
    }

    // Where to search for the next match: where this one ended, or one character on after an empty match, so it
    // isn't found again.
    private static int nextSearchFrom(final String text, final Matcher matcher) {
        if (matcher.end() > matcher.start()) {
            return matcher.end();
        }
        return matcher.end() < text.length()
                ? matcher.end() + Character.charCount(text.codePointAt(matcher.end()))
                : matcher.end() + 1;
    }

    private static ArrayNode matchObjects(final CompiledPattern pattern, final String text) {
        final ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (final Matcher matcher : matches(pattern, text, false)) {
            final ObjectNode match = JsonNodeFactory.instance.objectNode();
            match.put("offset", text.codePointCount(0, matcher.start()));
            match.put("length", text.codePointCount(matcher.start(), matcher.end()));
            match.put("string", matcher.group());
            final ArrayNode captures = match.putArray("captures");
            // As jq gives it, an empty match has no captures.
            if (matcher.end() != matcher.start()) {
                final String[] names = pattern.names();
                for (int group = 1; group <= matcher.groupCount(); group++) {
                    final ObjectNode capture = captures.addObject();
                    if (matcher.start(group) >= 0) {
                        capture.put("offset", text.codePointCount(0, matcher.start(group)));
                        capture.put("length", text.codePointCount(matcher.start(group), matcher.end(group)));
                        capture.put("string", matcher.group(group));
                    } else {
                        capture.put("offset", -1);
                        capture.put("length", 0);
                        capture.putNull("string");
                    }
                    capture.put("name", names[group]);
                }
            }
            array.add(match);
        }
        return array;
    }

    // The match's named captures, for sub's replacement to use, e.g. "\(.host)".
    private static ObjectNode namedCaptures(final CompiledPattern pattern, final Matcher matcher) {
        final ObjectNode captures = JsonNodeFactory.instance.objectNode();
        final String[] names = pattern.names();
        for (int group = 1; group <= matcher.groupCount(); group++) {
            if (names[group] != null) {
                captures.set(names[group], matcher.start(group) >= 0
                        ? TextNode.valueOf(matcher.group(group))
                        : NullNode.getInstance());
            }
        }
        return captures;
    }

    // Joins the text between the matches with each match's replacement. A replacement that gives several values
    // gives a result for each, as jq's does.
    private static void replaceAndJoin(final Scope scope,
                                       final Expression replacement,
                                       final List<JsonNode> parts,
                                       final int index,
                                       final Deque<String> done,
                                       final PathOutput output) throws JsonQueryException {
        if (index == parts.size()) {
            output.emit(TextNode.valueOf(String.join("", done)), null);
            return;
        }
        final JsonNode part = parts.get(index);
        if (part.isTextual()) {
            done.addLast(part.textValue());
            replaceAndJoin(scope, replacement, parts, index + 1, done, output);
            done.removeLast();
        } else {
            replacement.apply(scope, part, value -> {
                if (!value.isTextual()) {
                    throw new JsonQueryException("The replacement in sub or gsub must give a string, not "
                                                 + value.getNodeType().name().toLowerCase(Locale.ROOT) + ".");
                }
                done.addLast(value.textValue());
                replaceAndJoin(scope, replacement, parts, index + 1, done, output);
                done.removeLast();
            });
        }
    }

    // A pattern, with whether to find every match and whether to leave out empty ones, and its groups' names by
    // number (null for a group with no name).
    private record CompiledPattern(Pattern pattern, boolean global, boolean ignoreEmpty, String[] names) {

        CompiledPattern(final Pattern pattern, final boolean global, final boolean ignoreEmpty) {
            this(pattern, global, ignoreEmpty, groupNames(pattern));
        }

        private static String[] groupNames(final Pattern pattern) {
            final String[] names = new String[pattern.matcher("").groupCount() + 1];
            pattern.namedGroups().forEach((name, group) -> names[group] = name);
            return names;
        }
    }
}
