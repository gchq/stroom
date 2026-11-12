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

package stroom.query.language;

import stroom.query.api.JoinKey;
import stroom.query.api.JoinType;
import stroom.query.api.JoinWindow;
import stroom.query.api.token.AbstractToken;
import stroom.query.api.token.AbstractTokenGroup;
import stroom.query.api.token.TokenException;
import stroom.query.api.token.TokenType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/// Parses the parts of a StroomQL join line that need nothing but its tokens: its kind, its alias,
/// its keys and its time window.
///
/// A field of a joined source is written `<alias>.<field>`, or `${<alias>.<field>}` when the alias or
/// field holds characters such as spaces.
final class JoinLineParser {

    private static final String WITHIN = "within";

    private JoinLineParser() {
        // Static methods only.
    }

    /// Gets the kind of a join line from its keyword token, whose text includes the kind, as in
    /// `left join`.
    ///
    /// @param joinToken The join keyword token or the keyword group it starts.
    /// @return The kind of join.
    static JoinType getJoinType(final AbstractToken joinToken) {
        final String firstWord = joinToken.getText()
                .trim()
                .split("\\s+")[0]
                .toLowerCase(Locale.ROOT);
        return switch (firstWord) {
            case "left" -> JoinType.LEFT;
            case "keep" -> JoinType.KEEP;
            case "drop" -> JoinType.DROP;
            default -> JoinType.INNER;
        };
    }

    /// Parses the optional `as <alias>` after the source of a from clause or join line.
    ///
    /// @param children The children of the clause, the first of which is its source.
    /// @return The alias, or `null` if there is none.
    static String parseAlias(final List<AbstractToken> children) {
        if (children.size() == 1) {
            return null;
        }
        final AbstractToken as = children.get(1);
        if (!TokenType.AS.equals(as.getTokenType())) {
            throw new TokenException(as, "Unexpected token after the data source. Expected as and an alias, "
                                         + "or the next clause");
        }
        if (children.size() == 2) {
            throw new TokenException(as, "Expected an alias after as");
        }
        final AbstractToken alias = children.get(2);
        if (!TokenType.isString(alias)
            || TokenType.PARAM.equals(alias.getTokenType())
            || alias.getUnescapedText().isBlank()) {
            throw new TokenException(alias, "Expected an alias after as");
        }
        if (children.size() > 3) {
            throw new TokenException(children.get(3), "Unexpected token after the alias");
        }
        return alias.getUnescapedText();
    }

    /// Finds where the time window starts among the children of an on clause.
    ///
    /// @param children The children of the on clause.
    /// @return The index of the `within` token, or the number of children if there is no window.
    static int findWindowStart(final List<AbstractToken> children) {
        for (int i = 0; i < children.size(); i++) {
            final AbstractToken child = children.get(i);
            if (TokenType.STRING.equals(child.getTokenType()) && WITHIN.equalsIgnoreCase(child.getText())) {
                return i;
            }
        }
        return children.size();
    }

    /// Parses the keys of a join line, as in `UserId = hr.Username and Host = hr.Host`.
    ///
    /// @param tokens            The key tokens of the on clause, without any window.
    /// @param errorToken        The token to point an error at when there are no keys.
    /// @param alias             The alias of the join line.
    /// @param fieldlessAliases  The aliases of earlier keep and drop joins, whose fields don't exist.
    /// @param expressionParser  Parses the tokens of one side of a key into its expression text.
    /// @return The keys, never empty.
    static List<JoinKey> parseKeys(final List<AbstractToken> tokens,
                                   final AbstractToken errorToken,
                                   final String alias,
                                   final Collection<String> fieldlessAliases,
                                   final Function<List<AbstractToken>, String> expressionParser) {
        if (tokens.isEmpty()) {
            throw new TokenException(errorToken, "Expected the keys to join on after on, "
                                                 + getKeyExample(alias));
        }
        final List<JoinKey> keys = new ArrayList<>();
        List<AbstractToken> keyTokens = new ArrayList<>();
        for (final AbstractToken token : tokens) {
            if (TokenType.AND.equals(token.getTokenType())) {
                keys.add(parseKey(keyTokens, token, alias, fieldlessAliases, expressionParser));
                keyTokens = new ArrayList<>();
            } else {
                keyTokens.add(token);
            }
        }
        keys.add(parseKey(keyTokens, tokens.getLast(), alias, fieldlessAliases, expressionParser));
        return keys;
    }

    // One pair of keys, as in 'lowerCase(UserId) = hr.Username'. The side that names fields of the
    // joined source is its right side, whichever way round it is written.
    private static JoinKey parseKey(final List<AbstractToken> tokens,
                                    final AbstractToken errorToken,
                                    final String alias,
                                    final Collection<String> fieldlessAliases,
                                    final Function<List<AbstractToken>, String> expressionParser) {
        final String example = getKeyExample(alias);
        if (tokens.isEmpty()) {
            throw new TokenException(errorToken, "Expected a key, " + example);
        }
        int equalsIndex = -1;
        for (int i = 0; i < tokens.size(); i++) {
            if (TokenType.EQUALS.equals(tokens.get(i).getTokenType())) {
                if (equalsIndex != -1) {
                    throw new TokenException(tokens.get(i), "A key can only have one =, " + example);
                }
                equalsIndex = i;
            }
        }
        if (equalsIndex <= 0 || equalsIndex == tokens.size() - 1) {
            throw new TokenException(tokens.getFirst(), "A key must be two expressions with = between them, "
                                                        + example);
        }
        for (final String fieldlessAlias : fieldlessAliases) {
            if (namesAliasField(tokens, fieldlessAlias)) {
                throw new TokenException(tokens.getFirst(), getFieldlessMessage(fieldlessAlias));
            }
        }

        List<AbstractToken> left = tokens.subList(0, equalsIndex);
        List<AbstractToken> right = tokens.subList(equalsIndex + 1, tokens.size());
        final boolean leftNamesJoined = namesAliasField(left, alias);
        final boolean rightNamesJoined = namesAliasField(right, alias);
        if (leftNamesJoined && !rightNamesJoined) {
            final List<AbstractToken> swap = left;
            left = right;
            right = swap;
        } else if (leftNamesJoined || !rightNamesJoined) {
            throw new TokenException(tokens.getFirst(), "A key must compare a field of '" + alias
                                                        + "' with a field of the rows it is joined to, "
                                                        + example);
        }
        return new JoinKey(expressionParser.apply(left), expressionParser.apply(right));
    }

    /// Finds whether any field in the tokens, or in the functions and brackets among them, is a field
    /// of the given alias. Unquoted text and `${...}` name fields; quoted text is a value.
    ///
    /// @param tokens The tokens of an expression.
    /// @param alias  The alias.
    /// @return True if a field with the alias as its prefix is named.
    static boolean namesAliasField(final List<AbstractToken> tokens, final String alias) {
        final String prefix = alias + ".";
        for (final AbstractToken token : tokens) {
            if (token instanceof final AbstractTokenGroup group) {
                if (namesAliasField(group.getChildren(), alias)) {
                    return true;
                }
            } else if ((TokenType.STRING.equals(token.getTokenType()) || TokenType.PARAM.equals(token.getTokenType()))
                       && token.getUnescapedText().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /// Parses the time window of an event-to-event join, as in `within 10m after logon.EventTime`.
    ///
    /// @param tokens     The tokens from `within` to the end of the on clause.
    /// @param errorToken The token to point an error at when the window is empty.
    /// @return The window.
    static JoinWindow parseWindow(final List<AbstractToken> tokens, final AbstractToken errorToken) {
        final String example = "e.g. within 10m after EventTime";
        if (tokens.size() != 4) {
            throw new TokenException(tokens.isEmpty()
                    ? errorToken
                    : tokens.getFirst(), "Expected a window after the keys, " + example);
        }
        final AbstractToken duration = tokens.get(1);
        if (!TokenType.DURATION.equals(duration.getTokenType())) {
            throw new TokenException(duration, "Expected a duration such as 10m, " + example);
        }
        final AbstractToken direction = tokens.get(2);
        final String directionText = direction.getText().toLowerCase(Locale.ROOT);
        if (!TokenType.STRING.equals(direction.getTokenType())
            || !("after".equals(directionText) || "before".equals(directionText))) {
            throw new TokenException(direction, "Expected after or before, " + example);
        }
        final AbstractToken timeField = tokens.get(3);
        if (!TokenType.STRING.equals(timeField.getTokenType())
            && !TokenType.PARAM.equals(timeField.getTokenType())) {
            throw new TokenException(timeField, "Expected the time field the window starts or ends at, "
                                                + example);
        }
        return new JoinWindow(duration.getText(), "after".equals(directionText), timeField.getUnescapedText());
    }

    /// Gets the message for a join line that has no keys.
    ///
    /// @param alias The alias of the join line.
    /// @return The message.
    static String getMissingKeysMessage(final String alias) {
        return "Expected on with the keys to join on, " + getKeyExample(alias);
    }

    /// Checks that no field of a keep or drop join is used outside its own keys and search. Such a
    /// join adds no fields to the rows, so the field would never have a value.
    ///
    /// @param fieldsUsed The fields named outside the join lines' keys and searches.
    /// @param joins      The join lines of the query.
    static void checkFieldlessFieldsUnused(final Collection<String> fieldsUsed, final List<PendingJoin> joins) {
        final List<String> fieldlessAliases = getFieldlessAliases(joins);
        for (final String field : fieldsUsed) {
            final String alias = JoinWhereSplitter.findAlias(field, fieldlessAliases);
            if (alias != null) {
                final PendingJoin join = joins.stream()
                        .filter(other -> other.alias.equals(alias))
                        .findFirst()
                        .orElseThrow();
                throw new TokenException(join.token, getFieldlessMessage(alias));
            }
        }
    }

    /// Gets the aliases of the keep and drop joins, which add no fields to the rows.
    ///
    /// @param joins The join lines so far.
    /// @return The aliases of the keep and drop joins among them.
    static List<String> getFieldlessAliases(final List<PendingJoin> joins) {
        return joins.stream()
                .filter(join -> !join.type.addsFields())
                .map(join -> join.alias)
                .toList();
    }

    /// Gets the message for a field of a keep or drop join used where its rows' fields would be needed.
    ///
    /// @param alias The alias of the keep or drop join.
    /// @return The message.
    static String getFieldlessMessage(final String alias) {
        return "The fields of '" + alias + "' can only be used in its own keys, as a keep or drop join adds no "
               + "fields to the rows. To match only some of its rows, filter them in a bracketed query, "
               + "e.g. keep join (from source where Field = 'x' select Key) as " + alias + " on ...";
    }

    // An example key for the alias, written so that it tokenises whatever the alias holds.
    private static String getKeyExample(final String alias) {
        return "e.g. on UserId = " + getFieldReference(alias, "UserId");
    }

    /// Writes a reference to a joined field so that it tokenises as a field, using `${...}` when the
    /// alias holds anything but letters, digits and underscores.
    ///
    /// @param alias The alias.
    /// @param field The field name.
    /// @return The reference, e.g. `hr.UserId` or `${HR Users.UserId}`.
    static String getFieldReference(final String alias, final String field) {
        final String name = alias + "." + field;
        return alias.matches("[A-Za-z_]\\w*")
                ? name
                : "${" + name + "}";
    }
}
