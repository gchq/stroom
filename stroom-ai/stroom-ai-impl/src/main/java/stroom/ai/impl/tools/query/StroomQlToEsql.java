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

package stroom.ai.impl.tools.query;

import stroom.query.api.datasource.FieldType;
import stroom.query.api.token.Token;
import stroom.query.api.token.TokenType;
import stroom.query.language.token.Tokeniser;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// Translates a StroomQL aggregation on an Elasticsearch index into the ES|QL pipeline that gives the same rows,
/// for run_stroomql's hint that esql_query would have grouped them in Elasticsearch.
///
/// Only what translates exactly is translated; anything else gives nothing, so a hint never offers a different
/// answer. That is a query of `from`, `where` (terms joined by `and`: `=` on a keyword field with a value holding no
/// wildcard, or `=`, `>`, `>=`, `<`, `<=` on a number field with a number), one `group by` of keyword or number
/// fields, `sort by` the selected columns, `select` (the grouped fields, and `count()`, `sum`, `min`, `max` and
/// `average`/`mean` of a number field, each optionally `as` a name) and `limit` with one number. `!=` is left
/// out: Stroom's matches a document without the field, and ES|QL's doesn't. Anything else (`eval`, `filter`,
/// `having`, `window`, `or`, `not`, brackets, other functions, dates, text fields, a second `group by`) gives nothing.
final class StroomQlToEsql {

    private static final Set<TokenType> SKIPPED = Set.of(TokenType.WHITESPACE, TokenType.COMMENT);
    private static final Set<TokenType> CLAUSES = Set.of(TokenType.FROM, TokenType.WHERE, TokenType.GROUP,
            TokenType.SORT, TokenType.SELECT, TokenType.LIMIT);
    private static final Set<FieldType> NUMBERS = Set.of(FieldType.ID, FieldType.INTEGER, FieldType.LONG,
            FieldType.FLOAT, FieldType.DOUBLE);
    private static final Set<TokenType> TEXTS = Set.of(TokenType.STRING, TokenType.DOUBLE_QUOTED_STRING,
            TokenType.SINGLE_QUOTED_STRING);
    private static final Map<TokenType, String> COMPARISONS = new EnumMap<>(Map.of(
            TokenType.EQUALS, "==",
            TokenType.GREATER_THAN, ">",
            TokenType.GREATER_THAN_OR_EQUAL_TO, ">=",
            TokenType.LESS_THAN, "<",
            TokenType.LESS_THAN_OR_EQUAL_TO, "<="));
    private static final Map<String, String> FUNCTIONS = Map.of("sum", "SUM", "min", "MIN", "max", "MAX",
            "average", "AVG", "mean", "AVG");

    private StroomQlToEsql() {
        // Static only.
    }

    /// @param query  A StroomQL query.
    /// @param fields The index's fields, each with its type.
    /// @return The ES|QL pipeline (the commands after FROM) and its WHERE alone, or empty if the query isn't an
    /// aggregation that translates exactly.
    static Optional<Translation> translate(final String query, final Map<String, FieldType> fields) {
        try {
            return Optional.ofNullable(new Reader(fields).read(query));
        } catch (final RuntimeException e) {
            // Not a query read here: no hint.
            return Optional.empty();
        }
    }


    // --------------------------------------------------------------------------------


    /// A translated query.
    ///
    /// @param pipeline The ES|QL commands after FROM.
    /// @param where    Its `| WHERE ...` command alone, or empty if it has none.
    record Translation(String pipeline, String where) {

    }


    // --------------------------------------------------------------------------------


    // Reads one query; null wherever it doesn't translate exactly.
    private static final class Reader {

        private final Map<String, FieldType> fields;

        private Reader(final Map<String, FieldType> fields) {
            this.fields = fields;
        }

        private Translation read(final String query) {
            final Map<TokenType, List<Token>> clauses = clauses(query);
            if (clauses == null || !clauses.containsKey(TokenType.GROUP) || !clauses.containsKey(TokenType.SELECT)) {
                return null;
            }
            final List<String> groups = groups(clauses.get(TokenType.GROUP));
            final String where = clauses.containsKey(TokenType.WHERE)
                    ? where(clauses.get(TokenType.WHERE))
                    : "";
            final Select select = select(clauses.get(TokenType.SELECT), groups);
            final String sort = clauses.containsKey(TokenType.SORT)
                    ? sort(clauses.get(TokenType.SORT), select)
                    : "";
            final String limit = clauses.containsKey(TokenType.LIMIT)
                    ? limit(clauses.get(TokenType.LIMIT))
                    : "";
            if (groups == null || where == null || select == null || sort == null || limit == null) {
                return null;
            }
            final StringBuilder sb = new StringBuilder(where);
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append("| STATS ").append(String.join(", ", select.aggregates()))
                    .append(" BY ").append(String.join(", ", groups.stream().map(EsqlHints::name).toList()));
            if (!select.renames().isEmpty()) {
                sb.append(" | RENAME ").append(String.join(", ", select.renames()));
            }
            if (!sort.isEmpty()) {
                sb.append(" | SORT ").append(sort);
            }
            sb.append(" | KEEP ").append(String.join(", ", select.columns().stream().map(EsqlHints::name)
                    .toList()));
            if (!limit.isEmpty()) {
                sb.append(" | LIMIT ").append(limit);
            }
            return new Translation(sb.toString(), where);
        }

        // Each clause's tokens after its keyword, by keyword; null for a clause not read here, or one given twice.
        private static Map<TokenType, List<Token>> clauses(final String query) {
            final Map<TokenType, List<Token>> clauses = new EnumMap<>(TokenType.class);
            List<Token> current = null;
            for (final Token token : Tokeniser.parse(query)) {
                final TokenType type = token.getTokenType();
                if (SKIPPED.contains(type)) {
                    continue;
                }
                if (TokenType.ALL_KEYWORDS.contains(type)) {
                    if (!CLAUSES.contains(type) || clauses.containsKey(type)) {
                        return null;
                    }
                    current = new ArrayList<>();
                    clauses.put(type, current);
                } else if (current == null) {
                    return null;
                } else {
                    current.add(token);
                }
            }
            return clauses;
        }

        // The fields grouped by: keyword or number fields, after BY.
        private List<String> groups(final List<Token> tokens) {
            final List<List<Token>> items = items(afterBy(tokens));
            if (items == null || items.isEmpty()) {
                return null;
            }
            final List<String> groups = new ArrayList<>();
            for (final List<Token> item : items) {
                if (item.size() != 1 || item.getFirst().getTokenType() != TokenType.STRING) {
                    return null;
                }
                final String name = item.getFirst().getText();
                final FieldType type = fields.get(name);
                if (type != FieldType.KEYWORD && !NUMBERS.contains(type)) {
                    return null;
                }
                groups.add(name);
            }
            return groups;
        }

        // `| WHERE` with each term, or null.
        private String where(final List<Token> tokens) {
            final List<List<Token>> terms = new ArrayList<>();
            List<Token> current = new ArrayList<>();
            for (final Token token : tokens) {
                if (token.getTokenType() == TokenType.AND) {
                    terms.add(current);
                    current = new ArrayList<>();
                } else {
                    current.add(token);
                }
            }
            terms.add(current);
            final List<String> said = new ArrayList<>();
            for (final List<Token> term : terms) {
                final String one = term(term);
                if (one == null) {
                    return null;
                }
                said.add(one);
            }
            return "| WHERE " + String.join(" AND ", said);
        }

        private String term(final List<Token> term) {
            if (term.size() != 3 || term.getFirst().getTokenType() != TokenType.STRING) {
                return null;
            }
            final String field = term.getFirst().getText();
            final String operator = COMPARISONS.get(term.get(1).getTokenType());
            final Token value = term.get(2);
            final FieldType type = fields.get(field);
            if (operator == null || type == null) {
                return null;
            }
            if (type == FieldType.KEYWORD && "==".equals(operator) && TEXTS.contains(value.getTokenType())) {
                final String text = text(value);
                if (text.contains("*") || text.contains("?")) {
                    // A wildcard through Stroom.
                    return null;
                }
                return EsqlHints.name(field) + " == \"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            } else if (NUMBERS.contains(type) && value.getTokenType() == TokenType.NUMBER) {
                return EsqlHints.name(field) + " " + operator + " " + value.getText().strip();
            }
            return null;
        }

        // The selected columns: grouped fields (renamed by `as`) and aggregates.
        private Select select(final List<Token> tokens, final List<String> groups) {
            final List<List<Token>> items = items(tokens);
            if (items == null || groups == null) {
                return null;
            }
            final List<String> aggregates = new ArrayList<>();
            final List<String> renames = new ArrayList<>();
            final List<String> columns = new ArrayList<>();
            final Set<String> names = new LinkedHashSet<>();
            for (final List<Token> item : items) {
                final int as = indexOf(item, TokenType.AS);
                final List<Token> what = as < 0
                        ? item
                        : item.subList(0, as);
                final String alias = as < 0
                        ? null
                        : aliasOf(item.subList(as + 1, item.size()));
                if (as >= 0 && alias == null) {
                    return null;
                }
                if (what.size() == 1 && what.getFirst().getTokenType() == TokenType.STRING) {
                    final String field = what.getFirst().getText();
                    if (!groups.contains(field)) {
                        return null;
                    }
                    if (alias != null && !alias.equals(field)) {
                        renames.add(EsqlHints.name(field) + " AS " + EsqlHints.name(alias));
                    }
                    columns.add(alias == null
                            ? field
                            : alias);
                } else {
                    final String aggregate = aggregate(what);
                    if (aggregate == null) {
                        return null;
                    }
                    final String name = alias != null
                            ? alias
                            : defaultName(what);
                    aggregates.add(EsqlHints.name(name) + " = " + aggregate);
                    columns.add(name);
                }
            }
            for (final String column : columns) {
                if (!names.add(column)) {
                    return null;
                }
            }
            return aggregates.isEmpty()
                    ? null
                    : new Select(aggregates, renames, columns);
        }

        // count(), or sum, min, max, average or mean of a number field.
        private String aggregate(final List<Token> what) {
            if (what.isEmpty() || what.getFirst().getTokenType() != TokenType.FUNCTION_NAME) {
                return null;
            }
            final String function = what.getFirst().getText().toLowerCase(Locale.ROOT);
            final List<Token> args = what.subList(1, what.size());
            if (args.size() < 2 || args.getFirst().getTokenType() != TokenType.OPEN_BRACKET
                || args.getLast().getTokenType() != TokenType.CLOSE_BRACKET) {
                return null;
            }
            final List<Token> inside = args.subList(1, args.size() - 1);
            if ("count".equals(function)) {
                return inside.isEmpty()
                        ? "COUNT(*)"
                        : null;
            }
            final String esql = FUNCTIONS.get(function);
            if (esql == null || inside.size() != 1 || inside.getFirst().getTokenType() != TokenType.STRING
                || !NUMBERS.contains(fields.get(inside.getFirst().getText()))) {
                return null;
            }
            return esql + "(" + EsqlHints.name(inside.getFirst().getText()) + ")";
        }

        // The name Stroom gives a function's column, made one ES|QL takes unquoted: count, sum_bytes.
        private static String defaultName(final List<Token> what) {
            final StringBuilder sb = new StringBuilder(what.getFirst().getText().toLowerCase(Locale.ROOT));
            for (final Token token : what.subList(1, what.size())) {
                if (token.getTokenType() == TokenType.STRING) {
                    sb.append('_').append(token.getText().replaceAll("[^A-Za-z0-9_]", "_"));
                }
            }
            return sb.toString();
        }

        private static String aliasOf(final List<Token> tokens) {
            return tokens.size() == 1 && TEXTS.contains(tokens.getFirst().getTokenType())
                    ? text(tokens.getFirst())
                    : null;
        }

        // `SORT` of selected columns, each ASC or DESC; null for anything else.
        private static String sort(final List<Token> tokens, final Select select) {
            final List<List<Token>> items = items(afterBy(tokens));
            if (items == null || items.isEmpty() || select == null) {
                return null;
            }
            final List<String> said = new ArrayList<>();
            for (final List<Token> item : items) {
                if (item.isEmpty() || item.size() > 2 || !TEXTS.contains(item.getFirst().getTokenType())) {
                    return null;
                }
                final String column = text(item.getFirst());
                if (!select.columns().contains(column)) {
                    return null;
                }
                String direction = "ASC";
                if (item.size() == 2) {
                    direction = item.get(1).getText().strip().toUpperCase(Locale.ROOT);
                    if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
                        return null;
                    }
                }
                said.add(EsqlHints.name(column) + " " + direction);
            }
            return String.join(", ", said);
        }

        private static String limit(final List<Token> tokens) {
            return tokens.size() == 1 && tokens.getFirst().getTokenType() == TokenType.NUMBER
                   && tokens.getFirst().getText().strip().matches("\\d+")
                    ? tokens.getFirst().getText().strip()
                    : null;
        }

        // The tokens after a leading BY, or null without one.
        private static List<Token> afterBy(final List<Token> tokens) {
            return !tokens.isEmpty() && tokens.getFirst().getTokenType() == TokenType.BY
                    ? tokens.subList(1, tokens.size())
                    : null;
        }

        // Tokens split at commas outside brackets; null for none.
        private static List<List<Token>> items(final List<Token> tokens) {
            if (tokens == null) {
                return null;
            }
            final List<List<Token>> items = new ArrayList<>();
            List<Token> current = new ArrayList<>();
            int depth = 0;
            for (final Token token : tokens) {
                if (token.getTokenType() == TokenType.OPEN_BRACKET) {
                    depth++;
                } else if (token.getTokenType() == TokenType.CLOSE_BRACKET) {
                    depth--;
                }
                if (depth == 0 && token.getTokenType() == TokenType.COMMA) {
                    items.add(current);
                    current = new ArrayList<>();
                } else {
                    current.add(token);
                }
            }
            items.add(current);
            return items.stream().anyMatch(List::isEmpty)
                    ? null
                    : items;
        }

        private static int indexOf(final List<Token> tokens, final TokenType type) {
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).getTokenType() == type) {
                    return i;
                }
            }
            return -1;
        }

        // A token's text without its quotes.
        private static String text(final Token token) {
            final String text = token.getUnescapedText();
            if (token.getTokenType() != TokenType.STRING && text.length() >= 2
                && (text.startsWith("\"") || text.startsWith("'"))
                && text.charAt(text.length() - 1) == text.charAt(0)) {
                return text.substring(1, text.length() - 1);
            }
            return text;
        }
    }


    // --------------------------------------------------------------------------------


    // The selected columns.
    private record Select(List<String> aggregates, List<String> renames, List<String> columns) {

    }
}
