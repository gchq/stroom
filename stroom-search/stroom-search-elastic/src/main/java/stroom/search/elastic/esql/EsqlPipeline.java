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

package stroom.search.elastic.esql;

import stroom.ai.api.ElasticEsql.Reason;
import stroom.ai.api.ElasticEsql.Refusal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/// The ES|QL commands a caller gives after `FROM`, read into their stages and checked against the commands allowed.
///
/// A small lexer reads the text into tokens (words, quoted names, strings, numbers and punctuation), skipping
/// whitespace and comments, so that a `|` inside a string, a quoted name, a comment or brackets never splits a
/// stage, and a command word inside a string is never taken for a command. Each stage's command is its first word.
/// Only commands that work on the rows already read are allowed ([#ALLOWED]); a command that reads another index
/// (`FROM`, `ENRICH`, `LOOKUP JOIN`), a source command (`ROW`, `SHOW`, `TS`), anything that runs another query
/// (`FORK`) or reaches outside the cluster (`COMPLETION`, `RERANK`), and anything unknown, is refused.
///
/// The query sent puts each stage on its own line after `FROM <index>`, so a line comment at the end of one can't
/// hide what follows; an unterminated string, quoted name or block comment, or unbalanced brackets, are refused for
/// the same reason.
final class EsqlPipeline {

    /// The commands allowed, each working on the rows already read from the index.
    static final Set<String> ALLOWED = Set.of("WHERE", "EVAL", "STATS", "SORT", "LIMIT", "KEEP", "DROP", "RENAME",
            "DISSECT", "GROK", "MV_EXPAND");

    // Why some commands are refused, beyond not being allowed.
    private static final Map<String, String> REFUSED_BECAUSE = Map.of(
            "FROM", "the index is the Elastic Index document's own, and FROM is written for you: give only the "
                    + "commands after it",
            "ENRICH", "it reads an enrich index",
            "LOOKUP", "it reads another index",
            "FORK", "it runs further queries",
            "ROW", "it is a source command: the source is the index, written for you",
            "SHOW", "it is a source command: the source is the index, written for you",
            "TS", "it is a source command: the source is the index, written for you",
            "METRICS", "it is a source command: the source is the index, written for you",
            "COMPLETION", "it calls an inference endpoint",
            "RERANK", "it calls an inference endpoint");

    private static final String LIMIT = "LIMIT";
    private static final String STATS = "STATS";
    private static final String SORT = "SORT";
    private static final String MV_EXPAND = "MV_EXPAND";
    private static final String BY = "BY";
    private static final String TOTAL_COLUMN = "stroom_total_rows";

    private final List<Stage> stages;

    private EsqlPipeline(final List<Stage> stages) {
        this.stages = List.copyOf(stages);
    }

    /// Reads and checks the commands after `FROM`.
    ///
    /// @param text The commands, e.g. `| WHERE bytes > 0 | STATS total = SUM(bytes) BY user`; the leading `|` may be
    ///             left out. Null or blank for none.
    /// @return The stages.
    /// @throws Refusal if a command isn't allowed, or the text can't be read.
    static EsqlPipeline parse(final String text) {
        final List<Token> tokens = Lexer.tokens(text == null
                ? ""
                : text);
        final List<List<Token>> split = split(tokens);
        final List<Stage> stages = new ArrayList<>();
        for (final List<Token> stageTokens : split) {
            stages.add(stage(text, stageTokens));
        }
        return new EsqlPipeline(stages);
    }

    /// @return The stages, in order.
    List<Stage> stages() {
        return stages;
    }

    /// Caps the rows the query may return.
    ///
    /// The rows are bounded by the lowest `LIMIT` after the last command that can make more rows than it reads
    /// (`STATS`, `MV_EXPAND`); a `LIMIT` before one of those limits what it reads instead. If there is no such
    /// `LIMIT`, or it is over the cap, `LIMIT <cap>` is added at the end.
    ///
    /// @param cap The most rows allowed.
    /// @return The limit that bounds the rows, and whether one was added.
    Limited limited(final int cap) {
        final OptionalLong own = outputLimit();
        if (own.isEmpty()) {
            return new Limited(append(new Stage(LIMIT, LIMIT + " " + cap, List.of())), cap,
                    "The query had no LIMIT, so LIMIT " + cap + " was added.");
        } else if (own.getAsLong() > cap) {
            return new Limited(append(new Stage(LIMIT, LIMIT + " " + cap, List.of())), cap,
                    "The query's LIMIT was over " + cap + ", the most rows allowed, so LIMIT " + cap
                    + " was added.");
        }
        return new Limited(this, (int) own.getAsLong(), null);
    }

    /// The query that counts the rows this one gives without the limits on its output: for `STATS ... BY`, the
    /// number of groups. The `LIMIT` and `SORT` commands after the last `STATS` or `MV_EXPAND` are left out, as
    /// they don't change how many rows there are without a limit, and `STATS stroom_total_rows = COUNT(*)` is
    /// added.
    ///
    /// @return The counting query's stages.
    EsqlPipeline forTotal() {
        final int from = lastRowMaker() + 1;
        final List<Stage> kept = new ArrayList<>(stages.subList(0, from));
        for (final Stage stage : stages.subList(from, stages.size())) {
            if (!LIMIT.equals(stage.command()) && !SORT.equals(stage.command())) {
                kept.add(stage);
            }
        }
        kept.add(new Stage(STATS, STATS + " " + TOTAL_COLUMN + " = COUNT(*)", List.of()));
        return new EsqlPipeline(kept);
    }

    /// @return True if the last `STATS` groups its rows (`BY`).
    boolean groups() {
        for (int i = stages.size() - 1; i >= 0; i--) {
            final Stage stage = stages.get(i);
            if (STATS.equals(stage.command())) {
                return byIndex(stage.tokens()) >= 0;
            }
        }
        return false;
    }

    /// The fields the `STATS` commands group by, where a grouping is a field as it is (`BY user.name`, or
    /// `BY name = user.name`), not an expression; leaving out names an earlier command made (e.g. with `EVAL`).
    ///
    /// @return The field names, in order, each once.
    List<String> groupingFields() {
        final Set<String> fields = new LinkedHashSet<>();
        final Set<String> made = new LinkedHashSet<>();
        for (final Stage stage : stages) {
            if (STATS.equals(stage.command())) {
                final int by = byIndex(stage.tokens());
                if (by >= 0) {
                    for (final List<Token> item : items(stage.tokens().subList(by + 1, stage.tokens().size()))) {
                        final Token field = item.size() == 1
                                ? item.getFirst()
                                : (item.size() == 3 && "=".equals(item.get(1).text())
                                        ? item.get(2)
                                        : null);
                        if (field != null && field.isName() && !made.contains(field.name())) {
                            fields.add(field.name());
                        }
                    }
                }
            }
            made.addAll(madeNames(stage));
        }
        return List.copyOf(fields);
    }

    /// @param indexName The index (or pattern) to read, as `FROM` takes it.
    /// @return The whole query: `FROM <index>`, then each stage on its own line after `|`.
    String query(final String indexName) {
        final StringBuilder sb = new StringBuilder("FROM ").append(indexName);
        for (final Stage stage : stages) {
            sb.append("\n| ").append(stage.text());
        }
        return sb.toString();
    }

    // The lowest LIMIT after the last command that can make more rows than it reads.
    private OptionalLong outputLimit() {
        long lowest = Long.MAX_VALUE;
        boolean found = false;
        for (final Stage stage : stages.subList(lastRowMaker() + 1, stages.size())) {
            if (LIMIT.equals(stage.command())) {
                lowest = Math.min(lowest, stage.limit());
                found = true;
            }
        }
        return found
                ? OptionalLong.of(lowest)
                : OptionalLong.empty();
    }

    // The index of the last STATS or MV_EXPAND, or -1 if none.
    private int lastRowMaker() {
        for (int i = stages.size() - 1; i >= 0; i--) {
            final String command = stages.get(i).command();
            if (STATS.equals(command) || MV_EXPAND.equals(command)) {
                return i;
            }
        }
        return -1;
    }

    private EsqlPipeline append(final Stage stage) {
        final List<Stage> appended = new ArrayList<>(stages);
        appended.add(stage);
        return new EsqlPipeline(appended);
    }

    // Splits the tokens at each `|` outside brackets, dropping a leading one.
    private static List<List<Token>> split(final List<Token> tokens) {
        final List<List<Token>> stages = new ArrayList<>();
        List<Token> current = new ArrayList<>();
        int depth = 0;
        boolean leading = true;
        for (final Token token : tokens) {
            switch (token.type()) {
                case OPEN -> depth++;
                case CLOSE -> {
                    depth--;
                    if (depth < 0) {
                        throw invalid("A closing bracket at " + position(token) + " has no opening one");
                    }
                }
                case SEMICOLON -> {
                    if (depth == 0) {
                        throw new Refusal(Reason.COMMAND_NOT_ALLOWED, "A ';' at " + position(token)
                                                                      + " starts another statement: give one "
                                                                      + "query's commands only");
                    }
                }
                default -> {
                    // Nothing to track.
                }
            }
            if (token.type() == TokenType.PIPE && depth == 0) {
                if (leading && current.isEmpty()) {
                    leading = false;
                    continue;
                }
                if (current.isEmpty()) {
                    throw invalid("There is no command before the '|' at " + position(token));
                }
                stages.add(current);
                current = new ArrayList<>();
            } else {
                current.add(token);
            }
            leading = false;
        }
        if (depth > 0) {
            throw invalid("A bracket is not closed");
        }
        if (!current.isEmpty()) {
            stages.add(current);
        } else if (!stages.isEmpty() || !tokens.isEmpty()) {
            throw invalid("There is no command after the last '|'");
        }
        return stages;
    }

    private static Stage stage(final String text, final List<Token> tokens) {
        final Token first = tokens.getFirst();
        if (first.type() != TokenType.WORD) {
            throw new Refusal(Reason.COMMAND_NOT_ALLOWED, "'" + first.text() + "' at " + position(first)
                                                          + " isn't an ES|QL command. " + allowedList());
        }
        final String command = first.text().toUpperCase(Locale.ROOT);
        if ("MV".equals(command) && tokens.size() > 1 && "EXPAND".equalsIgnoreCase(tokens.get(1).text())) {
            // Seen: an agent writing MV EXPAND, refused as a command MV.
            throw new Refusal(Reason.INVALID, "'" + first.text() + " " + tokens.get(1).text() + "' at "
                                              + position(first) + " is written MV_EXPAND, with an underscore: e.g. "
                                              + "'| MV_EXPAND tags'", List.of("MV EXPAND"));
        }
        if (!ALLOWED.contains(command)) {
            final String said = ("LOOKUP".equals(command) || "INLINE".equals(command)) && tokens.size() > 1
                    ? command + " " + tokens.get(1).text().toUpperCase(Locale.ROOT)
                    : command;
            final String because = REFUSED_BECAUSE.get(command);
            throw new Refusal(Reason.COMMAND_NOT_ALLOWED, "The ES|QL command " + said + " isn't allowed here"
                                                          + (because == null
                    ? ""
                    : ": " + because) + ". " + allowedList(), List.of(said));
        }
        final String stageText = text.substring(first.start(), tokens.getLast().end());
        final Stage stage = new Stage(command, stageText, tokens);
        if (LIMIT.equals(command)) {
            // Checked now, so the limit is known before the query is sent.
            stage.limit();
        }
        return stage;
    }

    private static String allowedList() {
        return "Only these commands, which work on the rows read from the index, are allowed: WHERE, EVAL, STATS, "
               + "SORT, LIMIT, KEEP, DROP, RENAME, DISSECT, GROK and MV_EXPAND.";
    }

    // The index of the top-level BY in a STATS stage, or -1.
    private static int byIndex(final List<Token> tokens) {
        int depth = 0;
        for (int i = 1; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.type() == TokenType.OPEN) {
                depth++;
            } else if (token.type() == TokenType.CLOSE) {
                depth--;
            } else if (depth == 0 && token.type() == TokenType.WORD
                       && BY.equals(token.text().toUpperCase(Locale.ROOT))) {
                return i;
            }
        }
        return -1;
    }

    // Splits tokens at top-level commas.
    private static List<List<Token>> items(final List<Token> tokens) {
        final List<List<Token>> items = new ArrayList<>();
        List<Token> current = new ArrayList<>();
        int depth = 0;
        for (final Token token : tokens) {
            if (token.type() == TokenType.OPEN) {
                depth++;
            } else if (token.type() == TokenType.CLOSE) {
                depth--;
            }
            if (depth == 0 && token.type() == TokenType.COMMA) {
                items.add(current);
                current = new ArrayList<>();
            } else {
                current.add(token);
            }
        }
        items.add(current);
        return items.stream().filter(item -> !item.isEmpty()).toList();
    }

    // The column names a stage makes: `name = ...` in EVAL and STATS, and `... AS name` in RENAME.
    private static Set<String> madeNames(final Stage stage) {
        final Set<String> made = new LinkedHashSet<>();
        final List<Token> tokens = stage.tokens();
        if (tokens.isEmpty()) {
            return made;
        }
        final List<Token> rest = tokens.subList(1, tokens.size());
        for (final List<Token> item : items(rest)) {
            if ("EVAL".equals(stage.command()) || STATS.equals(stage.command())) {
                if (item.size() >= 2 && item.getFirst().isName() && "=".equals(item.get(1).text())) {
                    made.add(item.getFirst().name());
                }
            } else if ("RENAME".equals(stage.command())) {
                for (int i = 0; i + 1 < item.size(); i++) {
                    if (item.get(i).type() == TokenType.WORD
                        && "AS".equals(item.get(i).text().toUpperCase(Locale.ROOT)) && item.get(i + 1).isName()) {
                        made.add(item.get(i + 1).name());
                    }
                }
                if (item.size() >= 2 && item.getFirst().isName() && "=".equals(item.get(1).text())) {
                    // RENAME new = old, as newer versions also take it.
                    made.add(item.getFirst().name());
                }
            }
        }
        return made;
    }

    private static Refusal invalid(final String message) {
        return new Refusal(Reason.INVALID, message);
    }

    private static String position(final Token token) {
        return "character " + (token.start() + 1);
    }


    // --------------------------------------------------------------------------------


    /// The kinds of token.
    enum TokenType {
        /// A command, keyword, function or unquoted name, e.g. `STATS`, `BY`, `user.name`.
        WORD,
        /// A name in backticks.
        QUOTED_NAME,
        /// A string in double quotes, or triple double quotes.
        STRING,
        /// A number.
        NUMBER,
        /// `|`.
        PIPE,
        /// `(` or `[`.
        OPEN,
        /// `)` or `]`.
        CLOSE,
        /// `,`.
        COMMA,
        /// `;`.
        SEMICOLON,
        /// Any other character, e.g. an operator.
        OTHER
    }


    // --------------------------------------------------------------------------------


    /// A token of the text.
    ///
    /// @param type  Its kind.
    /// @param text  Its text, as given.
    /// @param start Where it starts in the text.
    /// @param end   Where it ends in the text (exclusive).
    record Token(TokenType type, String text, int start, int end) {

        /// @return True if it can name a column: an unquoted or a quoted name.
        boolean isName() {
            return type == TokenType.WORD || type == TokenType.QUOTED_NAME;
        }

        /// @return The column it names: a quoted name without its backticks.
        String name() {
            return type == TokenType.QUOTED_NAME
                    ? text.substring(1, text.length() - 1).replace("``", "`")
                    : text;
        }
    }


    // --------------------------------------------------------------------------------


    /// One command and what follows it, up to the next `|`.
    ///
    /// @param command The command, in capitals.
    /// @param text    Its text, as given (comments inside it included).
    /// @param tokens  Its tokens; empty for one added here.
    record Stage(String command, String text, List<Token> tokens) {

        /// @return The number a `LIMIT` takes.
        /// @throws Refusal if it doesn't take a whole number.
        long limit() {
            if (tokens.isEmpty()) {
                return Long.parseLong(text.substring(LIMIT.length()).strip());
            }
            if (tokens.size() != 2 || tokens.get(1).type() != TokenType.NUMBER) {
                throw invalid("LIMIT takes a whole number of rows, e.g. LIMIT 10: '" + text + "'");
            }
            try {
                return Long.parseLong(tokens.get(1).text());
            } catch (final NumberFormatException e) {
                throw invalid("LIMIT takes a whole number of rows, e.g. LIMIT 10: '" + text + "'");
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// The stages with the rows capped.
    ///
    /// @param pipeline The stages, with any `LIMIT` added.
    /// @param limit    The most rows the query can return.
    /// @param note     What was done to the limit, or null if nothing.
    record Limited(EsqlPipeline pipeline, int limit, String note) {

    }


    // --------------------------------------------------------------------------------


    // Reads text into tokens, skipping whitespace and comments.
    private static final class Lexer {

        private final String text;
        private final List<Token> tokens = new ArrayList<>();
        private int pos;

        private Lexer(final String text) {
            this.text = text;
        }

        static List<Token> tokens(final String text) {
            final Lexer lexer = new Lexer(text);
            lexer.read();
            return lexer.tokens;
        }

        private void read() {
            while (pos < text.length()) {
                final char c = text.charAt(pos);
                if (Character.isWhitespace(c)) {
                    pos++;
                } else if (startsWith("//")) {
                    skipLineComment();
                } else if (startsWith("/*")) {
                    skipBlockComment();
                } else if (startsWith("\"\"\"")) {
                    tripleQuoted();
                } else if (c == '"') {
                    quoted();
                } else if (c == '`') {
                    backticked();
                } else if (Character.isDigit(c)) {
                    number();
                } else if (Character.isLetter(c) || c == '_' || c == '@') {
                    word();
                } else {
                    punctuation(c);
                }
            }
        }

        private boolean startsWith(final String prefix) {
            return text.startsWith(prefix, pos);
        }

        private void skipLineComment() {
            while (pos < text.length() && text.charAt(pos) != '\n' && text.charAt(pos) != '\r') {
                pos++;
            }
        }

        // Block comments nest, as ES|QL's do.
        private void skipBlockComment() {
            final int start = pos;
            int depth = 0;
            while (pos < text.length()) {
                if (startsWith("/*")) {
                    depth++;
                    pos += 2;
                } else if (startsWith("*/")) {
                    depth--;
                    pos += 2;
                    if (depth == 0) {
                        return;
                    }
                } else {
                    pos++;
                }
            }
            throw invalid("The comment at character " + (start + 1) + " is not closed with */");
        }

        // A string in triple double quotes: no escapes, on one line, ending with up to two more quotes.
        private void tripleQuoted() {
            final int start = pos;
            pos += 3;
            while (pos < text.length() && !startsWith("\"\"\"")) {
                if (text.charAt(pos) == '\n' || text.charAt(pos) == '\r') {
                    throw invalid("The string at character " + (start + 1) + " is not closed on its line");
                }
                pos++;
            }
            if (pos >= text.length()) {
                throw invalid("The string at character " + (start + 1) + " is not closed with \"\"\"");
            }
            pos += 3;
            for (int extra = 0; extra < 2 && pos < text.length() && text.charAt(pos) == '"'; extra++) {
                pos++;
            }
            add(TokenType.STRING, start);
        }

        // A string in double quotes, with backslash escapes, on one line.
        private void quoted() {
            final int start = pos;
            pos++;
            while (pos < text.length()) {
                final char c = text.charAt(pos);
                if (c == '\\') {
                    pos += 2;
                } else if (c == '"') {
                    pos++;
                    add(TokenType.STRING, start);
                    return;
                } else if (c == '\n' || c == '\r') {
                    break;
                } else {
                    pos++;
                }
            }
            throw invalid("The string at character " + (start + 1) + " is not closed with \"");
        }

        // A name in backticks, a doubled backtick standing for one.
        private void backticked() {
            final int start = pos;
            pos++;
            while (pos < text.length()) {
                if (startsWith("``")) {
                    pos += 2;
                } else if (text.charAt(pos) == '`') {
                    pos++;
                    add(TokenType.QUOTED_NAME, start);
                    return;
                } else {
                    pos++;
                }
            }
            throw invalid("The name at character " + (start + 1) + " is not closed with `");
        }

        private void number() {
            final int start = pos;
            while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
                pos++;
            }
            if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                pos++;
                if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            add(TokenType.NUMBER, start);
        }

        private void word() {
            final int start = pos;
            while (pos < text.length()) {
                final char c = text.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '@' || c == '.') {
                    pos++;
                } else {
                    break;
                }
            }
            add(TokenType.WORD, start);
        }

        private void punctuation(final char c) {
            final int start = pos;
            pos++;
            final TokenType type = switch (c) {
                case '|' -> TokenType.PIPE;
                case '(', '[' -> TokenType.OPEN;
                case ')', ']' -> TokenType.CLOSE;
                case ',' -> TokenType.COMMA;
                case ';' -> TokenType.SEMICOLON;
                default -> TokenType.OTHER;
            };
            add(type, start);
        }

        private void add(final TokenType type, final int start) {
            tokens.add(new Token(type, text.substring(start, pos), start, pos));
        }
    }
}
