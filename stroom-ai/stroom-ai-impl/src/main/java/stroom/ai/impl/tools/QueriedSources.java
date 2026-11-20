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

package stroom.ai.impl.tools;

import stroom.query.api.token.Token;
import stroom.query.api.token.TokenType;
import stroom.query.language.token.Tokeniser;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/// The data sources a session's queries read, for the hint that points to the joins guide when a query reads a
/// data source other than one queried a little before: the moment an agent may join two queries' rows by reading
/// them (seen with Haiku in eval cases 71 to 74). In a chat, the sources of the current question (the agent run);
/// over MCP, those of the session's queries within a window. Each pair of sources is hinted once.
final class QueriedSources {

    // The query tools, and the argument that names the data source each reads.
    private static final Map<String, String> SOURCE_ARG = Map.of(
            "run_stroomql", "query",
            "esql_query", "index",
            "search_events", "index");

    // A data source given by uuid, not by a name a query can be written with.
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);
    // The words a name's alias is made from.
    private static final Pattern NOT_WORD = Pattern.compile("[^A-Za-z0-9]+");
    // The longest alias made from a one-word name.
    private static final int MAX_ALIAS = 8;
    // Words StroomQL reads as its own, which an alias can't be.
    private static final Set<String> KEYWORDS = Set.of("and", "anti", "as", "between", "by", "eval", "filter",
            "from", "full", "group", "having", "in", "inner", "join", "left", "limit", "not", "on", "or", "outer",
            "right", "select", "semi", "show", "sort", "sql", "where", "window", "within");

    // Each source's name as first given, by its key, and when it was last queried.
    private final Map<String, Queried> queried = new LinkedHashMap<>();
    // The pairs already hinted, each as its two keys in order.
    private final Set<List<String>> hinted = new HashSet<>();
    private ToolRunContext run;

    /// The data source a query tool's call reads: an esql_query or search_events call's index, a run_stroomql
    /// query's first source (read with Stroom's tokeniser).
    ///
    /// @param tool      The tool called.
    /// @param arguments Its arguments, or null.
    /// @return The data source's name as given, or null for another tool, or one that names none.
    static String source(final String tool, final Map<String, Object> arguments) {
        final String arg = SOURCE_ARG.get(tool);
        if (arg == null || arguments == null || !(arguments.get(arg) instanceof final String value)
            || value.isBlank()) {
            return null;
        }
        return "run_stroomql".equals(tool)
                ? firstSource(value)
                : value.strip();
    }

    /// Records a successful query of a data source.
    ///
    /// @param run    The agent run it is in (a chat's question), or null outside one.
    /// @param source The data source, as given.
    /// @param now    The time now, in milliseconds.
    /// @param window How long before a query of another source still counts, outside a run.
    /// @return The other sources queried before it (in the run, or within the window) not yet hinted with it, by
    /// name as first given; empty if there are none.
    List<String> queried(final ToolRunContext run, final String source, final long now, final Duration window) {
        if (this.run != run) {
            this.run = run;
            queried.clear();
            hinted.clear();
        }
        final String key = source.toLowerCase(Locale.ROOT);
        final List<String> others = new ArrayList<>();
        queried.forEach((otherKey, other) -> {
            final boolean recent = run != null || now - other.lastMs <= window.toMillis();
            if (!otherKey.equals(key) && recent && hinted.add(pair(key, otherKey))) {
                others.add(other.name);
            }
        });
        final Queried before = queried.get(key);
        queried.put(key, new Queried(before == null
                ? source
                : before.name, now));
        return others;
    }

    /// The join line of two data sources a session queried apart, as a fact for the hint: their names quoted as
    /// StroomQL takes them, the joined one with a short alias made from its name, the keys left as placeholders
    /// (the tools don't know which fields match).
    ///
    /// @param first  The data source queried first, as given.
    /// @param joined The one queried since, as given.
    /// @return E.g. `from "Staff activity" join "HR" as hr on <key> = hr.<key>`; null when either is a uuid or
    /// holds a double quote, so isn't a name to write in a query.
    static String joinLine(final String first, final String joined) {
        if (!writable(first) || !writable(joined)) {
            return null;
        }
        final String alias = alias(joined);
        return "from \"" + first + "\" join \"" + joined + "\" as " + alias + " on <key> = " + alias + ".<key>";
    }

    private static boolean writable(final String name) {
        return name != null && !name.isBlank() && name.indexOf('"') < 0 && !UUID.matcher(name.strip()).matches();
    }

    // The initials of a name of several words, or a one-word name cut short, lower case; made to start with a
    // letter and not to be a StroomQL keyword.
    private static String alias(final String name) {
        final List<String> words = new ArrayList<>();
        for (final String word : NOT_WORD.split(name.strip())) {
            if (!word.isEmpty()) {
                words.add(word.toLowerCase(Locale.ROOT));
            }
        }
        if (words.isEmpty()) {
            return "j";
        }
        final StringBuilder sb = new StringBuilder();
        if (words.size() == 1) {
            sb.append(words.getFirst(), 0, Math.min(MAX_ALIAS, words.getFirst().length()));
        } else {
            words.forEach(word -> sb.append(word.charAt(0)));
        }
        if (!Character.isLetter(sb.charAt(0))) {
            sb.insert(0, 's');
        }
        if (KEYWORDS.contains(sb.toString())) {
            sb.append('2');
        }
        return sb.toString();
    }

    private static List<String> pair(final String a, final String b) {
        return a.compareTo(b) < 0
                ? List.of(a, b)
                : List.of(b, a);
    }

    // The query's first data source: the one after its own from, outside any bracketed query.
    private static String firstSource(final String query) {
        final List<Token> tokens;
        try {
            tokens = Tokeniser.parse(query);
        } catch (final RuntimeException e) {
            return null;
        }
        int depth = 0;
        for (int i = 0; i < tokens.size(); i++) {
            final TokenType type = tokens.get(i).getTokenType();
            if (TokenType.OPEN_BRACKET.equals(type)) {
                depth++;
            } else if (TokenType.CLOSE_BRACKET.equals(type)) {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && TokenType.FROM.equals(type)) {
                for (int j = i + 1; j < tokens.size(); j++) {
                    final Token next = tokens.get(j);
                    if (TokenType.ALL_STRINGS.contains(next.getTokenType())) {
                        return next.getUnescapedText().strip();
                    }
                    if (!TokenType.WHITESPACE.equals(next.getTokenType())) {
                        return null;
                    }
                }
                return null;
            }
        }
        return null;
    }

    // A source's name as first given, and when it was last queried.
    private record Queried(String name, long lastMs) {

    }
}
