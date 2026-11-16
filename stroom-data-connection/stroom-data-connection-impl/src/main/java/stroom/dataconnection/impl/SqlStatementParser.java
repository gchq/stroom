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

package stroom.dataconnection.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// Reads the SQL of a query before it is prepared.
///
/// It finds the named placeholders (`:name`, or `:name{format}`) and replaces each with `?`, so
/// their values can be bound on a `PreparedStatement` and are never pasted into the SQL. Text in
/// quotes (including PostgreSQL's `$$` and `$tag$` strings) and in `--` and `/* */` comments is left
/// alone, as is PostgreSQL's `::` cast. MySQL's `#` comments are not recognised, as `#` is an
/// operator in PostgreSQL, so a placeholder after `#` is still bound; use `--` comments instead.
///
/// It also makes sure the SQL is one statement. A semicolon is allowed only as the last character.
/// Anywhere else it is refused, even inside quotes or a comment, because quoting rules differ
/// between databases and a statement hidden by a misread quote must not get through.
public final class SqlStatementParser {

    private SqlStatementParser() {
    }

    /// Reads the SQL of a query.
    ///
    /// @param sql The SQL as the user wrote it.
    /// @return The SQL ready to prepare, with its placeholders.
    /// @throws IllegalArgumentException If there is no SQL, it is more than one statement, it uses
    ///                                  `?`, or a quote, comment or placeholder format is not closed.
    public static ParsedSql parse(final String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("No SQL has been given for the Data Connection. In StroomQL, "
                                               + "put it in a sql clause after from, e.g. "
                                               + "from \"my source\" sql \"\"\"select * from my_table\"\"\"");
        }
        final String statement = withoutFinalSemicolon(sql);
        if (statement.indexOf(';') >= 0) {
            throw new IllegalArgumentException("The SQL must be a single statement. A semicolon is only " +
                                               "allowed at the end, not inside it, even in quotes or comments.");
        }

        final StringBuilder jdbcSql = new StringBuilder(statement.length());
        final List<SqlPlaceholder> placeholders = new ArrayList<>();
        final int length = statement.length();
        int i = 0;
        while (i < length) {
            final char c = statement.charAt(i);
            final char next = i + 1 < length
                    ? statement.charAt(i + 1)
                    : 0;
            final int end;
            if (c == '\'' || c == '"' || c == '`') {
                end = endOfQuoted(statement, i);
                jdbcSql.append(statement, i, end);
            } else if (c == '$' && (i == 0 || !isNamePart(statement.charAt(i - 1)))
                       && dollarQuoteTag(statement, i) != null) {
                end = endOfDollarQuoted(statement, i);
                jdbcSql.append(statement, i, end);
            } else if (c == '-' && next == '-') {
                end = endOfLineComment(statement, i);
                jdbcSql.append(statement, i, end);
            } else if (c == '/' && next == '*') {
                end = endOfBlockComment(statement, i);
                jdbcSql.append(statement, i, end);
            } else if (c == ':' && next == ':') {
                // A PostgreSQL cast, such as value::text.
                end = i + 2;
                jdbcSql.append("::");
            } else if (c == ':' && isNameStart(next)) {
                end = readPlaceholder(statement, i, placeholders);
                jdbcSql.append('?');
            } else if (c == '?') {
                throw new IllegalArgumentException("The SQL contains '?'. Use a named placeholder such as " +
                                                   ":name instead, and give the query a parameter of that name.");
            } else {
                end = i + 1;
                jdbcSql.append(c);
            }
            i = end;
        }
        return new ParsedSql(sql, jdbcSql.toString(), placeholders);
    }

    /// Finds the first keyword of the SQL, skipping white space, comments and opening brackets.
    ///
    /// @param sql The SQL.
    /// @return The first word in lower case, or an empty string if there is none.
    public static String firstKeyword(final String sql) {
        if (sql == null) {
            return "";
        }
        int i = 0;
        while (i < sql.length()) {
            final char c = sql.charAt(i);
            final char next = i + 1 < sql.length()
                    ? sql.charAt(i + 1)
                    : 0;
            if (Character.isWhitespace(c) || c == '(') {
                i++;
            } else if (c == '-' && next == '-') {
                i = endOfLineComment(sql, i);
            } else if (c == '/' && next == '*') {
                i = endOfBlockComment(sql, i);
            } else {
                break;
            }
        }
        final int start = i;
        while (i < sql.length() && isNamePart(sql.charAt(i))) {
            i++;
        }
        return sql.substring(start, i).toLowerCase(Locale.ROOT);
    }

    private static String withoutFinalSemicolon(final String sql) {
        final String trimmed = sql.strip();
        return trimmed.endsWith(";")
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
    }

    // Returns the index after the closing quote. A doubled quote, or a backslash before one, does not
    // close it.
    private static int endOfQuoted(final String sql, final int start) {
        final char quote = sql.charAt(start);
        int i = start + 1;
        while (i < sql.length()) {
            final char c = sql.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;
                } else {
                    return i + 1;
                }
            } else {
                i++;
            }
        }
        throw new IllegalArgumentException("The SQL has a " + quote + " quote that is not closed.");
    }

    // The opening tag of a PostgreSQL dollar-quoted string, such as '$$' or '$body$', starting at the
    // given index, or null if there is none there. '$1' is a parameter, not a tag.
    private static String dollarQuoteTag(final String sql, final int start) {
        int i = start + 1;
        if (i < sql.length() && sql.charAt(i) != '$' && !isNameStart(sql.charAt(i))) {
            return null;
        }
        while (i < sql.length() && isNamePart(sql.charAt(i))) {
            i++;
        }
        return i < sql.length() && sql.charAt(i) == '$'
                ? sql.substring(start, i + 1)
                : null;
    }

    // Returns the index after the closing tag of a dollar-quoted string.
    private static int endOfDollarQuoted(final String sql, final int start) {
        final String tag = dollarQuoteTag(sql, start);
        final int close = sql.indexOf(tag, start + tag.length());
        if (close < 0) {
            throw new IllegalArgumentException("The SQL has a " + tag + " quoted string that is not closed.");
        }
        return close + tag.length();
    }

    private static int endOfLineComment(final String sql, final int start) {
        final int newLine = sql.indexOf('\n', start);
        return newLine < 0
                ? sql.length()
                : newLine;
    }

    private static int endOfBlockComment(final String sql, final int start) {
        final int close = sql.indexOf("*/", start + 2);
        if (close < 0) {
            throw new IllegalArgumentException("The SQL has a /* comment that is not closed.");
        }
        return close + 2;
    }

    // Reads ':name' or ':name{format}' starting at the colon, and returns the index after it.
    private static int readPlaceholder(final String sql, final int colon, final List<SqlPlaceholder> placeholders) {
        int i = colon + 1;
        while (i < sql.length() && isNamePart(sql.charAt(i))) {
            i++;
        }
        final String name = sql.substring(colon + 1, i);
        String format = null;
        if (i < sql.length() && sql.charAt(i) == '{') {
            final int close = sql.indexOf('}', i + 1);
            if (close < 0) {
                throw new IllegalArgumentException("The format of placeholder :" + name + " has no closing '}'.");
            }
            format = sql.substring(i + 1, close);
            i = close + 1;
        }
        placeholders.add(new SqlPlaceholder(name, format));
        return i;
    }

    private static boolean isNameStart(final char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isNamePart(final char c) {
        return isNameStart(c) || (c >= '0' && c <= '9');
    }
}
