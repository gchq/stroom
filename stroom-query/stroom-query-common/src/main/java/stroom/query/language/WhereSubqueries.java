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
import stroom.query.api.token.AbstractToken;
import stroom.query.api.token.AbstractTokenGroup;
import stroom.query.api.token.FunctionGroup;
import stroom.query.api.token.KeywordGroup;
import stroom.query.api.token.Token;
import stroom.query.api.token.TokenException;
import stroom.query.api.token.TokenType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// Finds and reads the SQL forms of semi and anti joins in a StroomQL where clause, which
/// [SearchRequestFactory] rewrites into join lines:
///
/// - `exists (from S where k = p.k)` is a semi join on the keys compared.
/// - `not exists (from S where k = p.k)` is an anti join on them.
/// - `X in (from S select k)` is a semi join on `X = k`.
/// - `X not in (from S select k)` is an anti join on `X = k`.
///
/// They may only be used at the top level of where, joined to the rest by `and`. The bracketed query
/// names the first source's fields by its alias, or by its data source's name if it has none. Only
/// `=` may compare them with the bracketed query's fields, and each comparison becomes a key. The
/// bracketed query's other conditions go into its own search.
///
/// Everything here works on tokens alone.
final class WhereSubqueries {

    static final String MISPLACED_MESSAGE = "A bracketed query can only be used at the top level of where, "
                                            + "joined to the rest by and, as in where exists (from ...), "
                                            + "where not exists (from ...) or where X in (from ...)";
    private static final String EXISTS = "exists";
    private static final String CLAUSES_MESSAGE = "An exists or in query can only have from, sql, where and "
                                                  + "select. To group or filter the joined source first, "
                                                  + "join a bracketed query instead, e.g. "
                                                  + "semi join (from ... group by ...) as x on ...";

    private WhereSubqueries() {
        // Static methods only.
    }

    /// Finds whether a token is a bracketed query, or `exists` with one, as only the forms at the top
    /// level of where may hold.
    ///
    /// @param token The token.
    /// @return True if the token holds a bracketed query of its own.
    static boolean isSubquery(final AbstractToken token) {
        return getQuery(token) != null;
    }

    /// Takes the `exists` and `in` forms out of the top level of a where clause.
    ///
    /// @param where     The where clause.
    /// @param bracketed True if the where clause is in a bracketed query, which can't have them yet.
    /// @return The forms, in order, and the where clause without them.
    static Extracted extractForms(final KeywordGroup where, final boolean bracketed) {
        final List<AbstractToken> children = where.getChildren();
        final List<AbstractToken> ands = children.stream()
                .filter(child -> TokenType.AND.equals(child.getTokenType()))
                .toList();
        final List<List<AbstractToken>> conditions = splitAtAnd(children);
        final List<Form> forms = new ArrayList<>();
        final List<AbstractToken> rest = new ArrayList<>();
        for (int i = 0; i < conditions.size(); i++) {
            final Form form = readForm(conditions.get(i));
            if (form != null) {
                forms.add(form);
            } else {
                if (!rest.isEmpty()) {
                    rest.add(ands.get(i - 1));
                }
                rest.addAll(conditions.get(i));
            }
        }
        if (forms.isEmpty()) {
            return new Extracted(forms, where);
        }

        final AbstractToken firstForm = forms.getFirst().errorToken();
        if (bracketed) {
            throw new TokenException(firstForm, "where exists, where not exists and in (from ...) can't be used "
                                                + "inside a bracketed query yet");
        }
        // 'and' comes before 'or', so a form beside an 'or' isn't a condition of all the rows.
        if (children.stream().anyMatch(child -> TokenType.OR.equals(child.getTokenType()))) {
            throw new TokenException(firstForm, "where exists, where not exists and in (from ...) can't be used "
                                                + "beside or at the top level of where; put the conditions "
                                                + "joined by or in brackets");
        }
        return new Extracted(forms, new KeywordGroup(
                TokenType.WHERE, where.getChars(), where.getStart(), where.getEnd(), rest));
    }

    /// Gets the fields each key of a form compares: the field before `in` with the field its query
    /// selects, and each comparison in its query's where clause.
    ///
    /// @param form           The form.
    /// @param inner          The parts of its bracketed query.
    /// @param correlation    The comparisons in its bracketed query's where clause.
    /// @param alias          The alias of the join the form becomes.
    /// @param outerName      The alias of the first source, or its data source's name if it has none.
    /// @param outerHasAlias  True if the first source has an alias, by which its fields stay named, as
    ///                       in a join line's keys. Otherwise they are named by their own names.
    /// @return The fields of each key, never empty.
    static List<KeyFields> getKeyFields(final Form form,
                                        final InnerQuery inner,
                                        final Correlation correlation,
                                        final String alias,
                                        final String outerName,
                                        final boolean outerHasAlias) {
        final List<KeyFields> keys = new ArrayList<>();
        if (form.field() != null) {
            if (!isField(form.field())) {
                throw new TokenException(form.field(), "Only a field of the first source can come before in "
                                                       + "(from ...), e.g. where team in (from Teams select team)");
            }
            keys.add(new KeyFields(firstSourceField(form.field(), outerName, outerHasAlias),
                    joinedField(inner.select(), alias)));
        }
        for (final KeyPair pair : correlation.keys()) {
            keys.add(new KeyFields(firstSourceField(pair.outer(), outerName, outerHasAlias),
                    joinedField(pair.inner(), alias)));
        }
        if (keys.isEmpty()) {
            throw new TokenException(form.errorToken(), "An exists query must compare a field of '" + alias
                                                        + "' with a field of '" + outerName + "', e.g. "
                                                        + "where exists (from ... as " + alias + " where "
                                                        + alias + ".Field = " + outerName + ".Field)");
        }
        return keys;
    }

    // The name of a field of the bracketed query as the join names it, e.g. 't.team'.
    private static String joinedField(final AbstractToken token, final String alias) {
        return alias + "." + JoinWhereSplitter.withoutAlias(token.getUnescapedText(), alias);
    }

    // The name of a field of the first source as its rows name it: by its alias, as in a join line's
    // keys, or by its own name if the first source has no alias and it is written with the data
    // source's name.
    private static String firstSourceField(final AbstractToken token,
                                           final String outerName,
                                           final boolean outerHasAlias) {
        return outerHasAlias
                ? token.getUnescapedText()
                : JoinWhereSplitter.withoutAlias(token.getUnescapedText(), outerName);
    }

    /// Splits the children of a where clause at its top-level `and` tokens.
    ///
    /// @param children The children of the where clause.
    /// @return The tokens of each condition joined by `and`.
    static List<List<AbstractToken>> splitAtAnd(final List<AbstractToken> children) {
        final List<List<AbstractToken>> conditions = new ArrayList<>();
        List<AbstractToken> condition = new ArrayList<>();
        for (final AbstractToken child : children) {
            if (TokenType.AND.equals(child.getTokenType())) {
                conditions.add(condition);
                condition = new ArrayList<>();
            } else {
                condition.add(child);
            }
        }
        conditions.add(condition);
        return conditions;
    }

    /// Reads one condition of a where clause as an `exists` or `in` form.
    ///
    /// @param condition The tokens of a condition joined to the rest of where by `and`.
    /// @return The form, or `null` if the condition is not one.
    static Form readForm(final List<AbstractToken> condition) {
        final int size = condition.size();
        // exists (...) and not exists (...)
        final boolean not = isType(condition, 0, TokenType.NOT);
        final AbstractTokenGroup existsQuery = readExists(not
                ? condition.subList(1, size)
                : condition);
        if (existsQuery != null) {
            return new Form(not
                    ? JoinType.ANTI
                    : JoinType.SEMI, null, existsQuery, condition.getFirst());
        }
        // X in (...) and X not in (...)
        if (size == 3 && isType(condition, 1, TokenType.IN) && isQuery(condition.get(2))) {
            return new Form(JoinType.SEMI, condition.getFirst(), (AbstractTokenGroup) condition.get(2),
                    condition.getFirst());
        }
        if (size == 4 && isType(condition, 1, TokenType.NOT) && isType(condition, 2, TokenType.IN)
            && isQuery(condition.get(3))) {
            return new Form(JoinType.ANTI, condition.getFirst(), (AbstractTokenGroup) condition.get(3),
                    condition.getFirst());
        }
        return null;
    }

    /// Reads the bracketed query of a form into its parts, refusing clauses it can't have.
    ///
    /// @param form The form.
    /// @return The parts of its bracketed query.
    static InnerQuery readInnerQuery(final Form form) {
        AbstractToken source = null;
        String alias = null;
        KeywordGroup sql = null;
        List<AbstractToken> where = List.of();
        List<AbstractToken> select = null;
        AbstractToken selectToken = null;
        final Set<TokenType> seen = EnumSet.noneOf(TokenType.class);
        for (final AbstractToken child : form.query().getChildren()) {
            if (!(child instanceof final KeywordGroup group)) {
                throw new TokenException(child, "Unexpected token");
            }
            if (!seen.add(group.getTokenType())) {
                throw new TokenException(group, "An exists or in query can only have one "
                                                + group.getTokenType().toString().toLowerCase(Locale.ROOT)
                                                + " clause");
            }
            switch (group.getTokenType()) {
                case FROM -> {
                    if (group.getChildren().isEmpty()) {
                        throw new TokenException(group, "Expected the data source to search after from");
                    }
                    source = group.getChildren().getFirst();
                    if (source instanceof AbstractTokenGroup) {
                        throw new TokenException(source, CLAUSES_MESSAGE);
                    }
                    alias = JoinLineParser.parseAlias(group.getChildren());
                }
                case SQL -> sql = group;
                case WHERE -> where = group.getChildren();
                case SELECT -> {
                    select = group.getChildren();
                    selectToken = group;
                }
                default -> throw new TokenException(group, CLAUSES_MESSAGE);
            }
        }
        if (form.field() != null) {
            if (select == null || select.size() != 1 || !isField(select.getFirst())) {
                throw new TokenException(selectToken != null
                        ? selectToken
                        : form.query(), "An in query must select exactly one field, "
                                        + "e.g. where team in (from Teams select team)");
            }
        }
        return new InnerQuery(source, alias, sql, where, select == null
                ? null
                : select.getFirst());
    }

    /// Splits the conditions of a bracketed query's where clause into the comparisons with the first
    /// source's fields, which become keys, and the rest, which go into its own search.
    ///
    /// @param where      The children of the bracketed query's where clause.
    /// @param outerName  The alias of the first source, or its data source's name if it has none.
    /// @param innerName  The alias of the bracketed query, or its data source's name if it has none.
    /// @return The comparisons and the other conditions.
    static Correlation splitCorrelation(final List<AbstractToken> where,
                                        final String outerName,
                                        final String innerName) {
        final String example = "e.g. where " + innerName + ".Field = " + outerName + ".Field";
        final String message = "A field of '" + outerName + "' can only be compared with = to a field of '"
                               + innerName + "', joined to the rest of where by and, " + example;
        final List<KeyPair> keys = new ArrayList<>();
        final List<AbstractToken> rest = new ArrayList<>();
        for (final List<AbstractToken> condition : splitAtAnd(where)) {
            if (condition.isEmpty()) {
                continue;
            }
            if (!JoinLineParser.namesAliasField(condition, outerName)) {
                if (!rest.isEmpty()) {
                    rest.add(createAnd(condition.getFirst()));
                }
                rest.addAll(condition);
                continue;
            }
            if (condition.size() != 3 || !TokenType.EQUALS.equals(condition.get(1).getTokenType())
                || !isField(condition.get(0)) || !isField(condition.get(2))) {
                throw new TokenException(condition.getFirst(), message);
            }
            final AbstractToken left = condition.get(0);
            final AbstractToken right = condition.get(2);
            final boolean leftIsOuter = left.getUnescapedText().startsWith(outerName + ".");
            final boolean rightIsOuter = right.getUnescapedText().startsWith(outerName + ".");
            if (leftIsOuter == rightIsOuter) {
                throw new TokenException(condition.getFirst(), message);
            }
            keys.add(leftIsOuter
                    ? new KeyPair(left, right)
                    : new KeyPair(right, left));
        }
        // 'and' comes before 'or', so taking the comparisons out of where beside an 'or' would change
        // what the rest means.
        if (!keys.isEmpty() && where.stream().anyMatch(token -> TokenType.OR.equals(token.getTokenType()))) {
            throw new TokenException(where.getFirst(), "An exists or in query that compares fields of '"
                                                       + outerName + "' can't have or at the top level of its "
                                                       + "where; put the conditions joined by or in brackets");
        }
        return new Correlation(keys, rest);
    }

    // An 'and' token to join two conditions that weren't next to each other, pointing at the second.
    private static AbstractToken createAnd(final AbstractToken next) {
        return new Token(TokenType.AND, next.getChars(), next.getStart(), next.getStart());
    }

    // The bracketed query of a token, if it is one or is exists(...) with one.
    private static AbstractTokenGroup getQuery(final AbstractToken token) {
        if (token instanceof final FunctionGroup function) {
            return EXISTS.equals(function.getName().toLowerCase(Locale.ROOT)) && startsWithFrom(function)
                    ? function
                    : null;
        }
        return isQuery(token)
                ? (AbstractTokenGroup) token
                : null;
    }

    // True if the token is a bracketed query, as in '(from X ...)'.
    private static boolean isQuery(final AbstractToken token) {
        return token instanceof final AbstractTokenGroup group
               && !(group instanceof KeywordGroup)
               && !(group instanceof FunctionGroup)
               && startsWithFrom(group);
    }

    private static boolean startsWithFrom(final AbstractTokenGroup group) {
        return !group.getChildren().isEmpty()
               && TokenType.FROM.equals(group.getChildren().getFirst().getTokenType());
    }

    // The bracketed query of 'exists (...)', or of 'exists(...)', which is read as a function, or null
    // if the tokens are neither.
    private static AbstractTokenGroup readExists(final List<AbstractToken> tokens) {
        if (tokens.size() == 1 && tokens.getFirst() instanceof FunctionGroup) {
            return getQuery(tokens.getFirst());
        }
        if (tokens.size() == 2
            && TokenType.STRING.equals(tokens.getFirst().getTokenType())
            && EXISTS.equalsIgnoreCase(tokens.getFirst().getText().trim())
            && isQuery(tokens.get(1))) {
            return (AbstractTokenGroup) tokens.get(1);
        }
        return null;
    }

    private static boolean isType(final List<AbstractToken> tokens, final int index, final TokenType type) {
        return index < tokens.size() && type.equals(tokens.get(index).getTokenType());
    }

    // True if the token names a field: a plain word or ${...}.
    private static boolean isField(final AbstractToken token) {
        return TokenType.STRING.equals(token.getTokenType()) || TokenType.PARAM.equals(token.getTokenType());
    }


    // --------------------------------------------------------------------------------


    /// An `exists` or `in` form in a where clause.
    ///
    /// @param type       A semi join for `exists` and `in`, an anti join for `not exists` and `not in`.
    /// @param field      The first source's field before `in`, or `null` for `exists`.
    /// @param query      The bracketed query.
    /// @param errorToken The token to point errors at.
    record Form(JoinType type, AbstractToken field, AbstractTokenGroup query, AbstractToken errorToken) {

    }

    /// The parts of the bracketed query of a form.
    ///
    /// @param source The data source token.
    /// @param alias  The alias given after the data source, or `null`.
    /// @param sql    The sql clause, or `null`.
    /// @param where  The children of the where clause, empty if there is none.
    /// @param select The field selected, or `null` if there is no select.
    record InnerQuery(AbstractToken source,
                      String alias,
                      KeywordGroup sql,
                      List<AbstractToken> where,
                      AbstractToken select) {

    }

    /// One comparison of a first-source field with a field of the bracketed query.
    ///
    /// @param outer The first source's field.
    /// @param inner The bracketed query's field.
    record KeyPair(AbstractToken outer, AbstractToken inner) {

    }

    /// The fields a key of a form's join compares.
    ///
    /// @param first  The field of the first source, as named in its rows.
    /// @param joined The field of the joined source, named with the join's alias.
    record KeyFields(String first, String joined) {

        /// Gets the key as a join line's key is written.
        ///
        /// @return The key.
        JoinKey toJoinKey() {
            return new JoinKey("${" + first + "}", "${" + joined + "}");
        }
    }

    /// The forms taken out of a where clause.
    ///
    /// @param forms The forms, in order.
    /// @param where The where clause without them.
    record Extracted(List<Form> forms, KeywordGroup where) {

    }

    /// The where clause of a bracketed query, split.
    ///
    /// @param keys The comparisons with the first source's fields.
    /// @param rest The other conditions, joined by `and`, for its own search.
    record Correlation(List<KeyPair> keys, List<AbstractToken> rest) {

    }
}
