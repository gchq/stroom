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

import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.JoinType;
import stroom.query.api.Param;
import stroom.query.api.Query;
import stroom.query.api.QueryJoin;
import stroom.query.api.TableSettings;
import stroom.query.api.TimeRange;
import stroom.query.api.token.AbstractToken;
import stroom.query.api.token.KeywordGroup;
import stroom.query.api.token.QuotedStringToken;
import stroom.query.api.token.TokenException;
import stroom.query.api.token.TokenGroup;
import stroom.query.api.token.TokenType;
import stroom.query.language.JoinWhereSplitter.JoinedSource;
import stroom.query.language.JoinWhereSplitter.Split;
import stroom.query.language.functions.FieldIndex;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The from clause and join lines of a StroomQL query while [SearchRequestFactory] parses it, with
/// the `exists` and `in` forms of where that become joins, and the placing of where and filter once
/// the joins are known.
final class JoinClauses {

    private final Parser parser;
    private final FieldIndex fieldIndex;
    // True if the query is a bracketed query inside another query.
    private final boolean bracketed;
    // The from and sql clauses, if there are any, for pointing errors at.
    private AbstractToken fromToken;
    private AbstractToken sqlToken;
    // The hidden columns that hold the fields named by the where conditions that run on joined
    // rows, by column name. The conditions name these columns, as a filter names columns, so they
    // always see the field itself, whatever columns of the same name the query has.
    private final Map<String, String> postJoinColumns = new LinkedHashMap<>();
    // The number of fields named in the from and join lines; any later ones are named elsewhere.
    private int fieldCountInKeys;
    // The alias of the first source, and the bracketed query in from, if there is one.
    private String firstAlias;
    private ParsedQuery fromQuery;
    // The join line being parsed, which still needs its keys, and the finished ones.
    private PendingJoin pendingJoin;
    private final List<PendingJoin> joins = new ArrayList<>();

    /// @param parser     The parser of the query, for the parts of it the joins need.
    /// @param fieldIndex The field index of the query, to which the fields of join keys are added.
    /// @param bracketed  True if the query is a bracketed query inside another query.
    JoinClauses(final Parser parser, final FieldIndex fieldIndex, final boolean bracketed) {
        this.parser = parser;
        this.fieldIndex = fieldIndex;
        this.bracketed = bracketed;
    }

    /// Gets the from clause, for pointing errors at.
    ///
    /// @return The from clause, or `null` if none has been parsed.
    AbstractToken getFromToken() {
        return fromToken;
    }

    /// Gets the sql clause of the first source, for pointing errors at.
    ///
    /// @return The sql clause, or `null` if it has none.
    AbstractToken getSqlToken() {
        return sqlToken;
    }

    /// Finds whether the first source is a bracketed query, whose rows are not events.
    ///
    /// @return True if from names a bracketed query.
    boolean hasBracketedFrom() {
        return fromQuery != null;
    }

    /// Finds whether any join line parsed so far has a time window, as in `within 10m after`.
    ///
    /// @return True if a join has a time window.
    boolean hasWindowedJoin() {
        return joins.stream().anyMatch(join -> join.window != null)
               || (pendingJoin != null && pendingJoin.window != null);
    }

    /// Gets the hidden columns that the where conditions on joined rows test, once where is placed.
    ///
    /// @return The fields they hold by column name.
    Map<String, String> getPostJoinColumns() {
        return postJoinColumns;
    }

    /// Checks that the last join line had its keys, at the end of the query.
    ///
    /// @throws TokenException If a join line has no on clause.
    void checkNoPendingJoin() {
        if (pendingJoin != null) {
            throw new TokenException(pendingJoin.token, JoinLineParser.getMissingKeysMessage(pendingJoin.alias));
        }
    }

    // The SQL is a single quoted string. A triple-quoted one is taken as written, so it can hold
    // double quotes and backslashes without escaping.
    private void processSql(final KeywordGroup keywordGroup, final Query.Builder queryBuilder) {
        final List<AbstractToken> children = keywordGroup.getChildren();
        if (children.isEmpty()) {
            throw new TokenException(keywordGroup, "Expected the SQL to run after sql, as a quoted string, "
                                                   + "e.g. sql \"\"\"select * from my_table\"\"\"");
        }
        final AbstractToken sql = children.getFirst();
        if (!(sql instanceof QuotedStringToken)) {
            throw new TokenException(sql, "The SQL must be a quoted string, e.g. "
                                          + "sql \"\"\"select * from my_table\"\"\"");
        }
        if (children.size() > 1) {
            throw new TokenException(children.get(1), "Unexpected token after the SQL");
        }
        queryBuilder.nativeQuery(sql.getUnescapedText());
    }

    /// Parses the from clause: a data source or a bracketed query, then an optional alias.
    ///
    /// @param keywordGroup The from clause.
    /// @param queryBuilder The query, which takes the data source.
    void processFrom(final KeywordGroup keywordGroup, final Query.Builder queryBuilder) {
        fromToken = keywordGroup;
        final List<AbstractToken> children = keywordGroup.getChildren();
        if (children.isEmpty()) {
            throw new TokenException(keywordGroup, "Expected data source value");
        }

        final AbstractToken source = children.getFirst();
        if (source instanceof final TokenGroup bracketedQuery) {
            fromQuery = parser.parseBracketedQuery(bracketedQuery);
        } else {
            queryBuilder.dataSource(parser.resolveDataSource(source));
        }
        firstAlias = JoinLineParser.parseAlias(children);
    }

    /// Parses a sql clause, which belongs to the source straight before it: the from source, or the
    /// source of the join line being parsed.
    ///
    /// @param keywordGroup   The sql clause.
    /// @param consumedTokens The clauses parsed so far.
    /// @param queryBuilder   The query, which takes the first source's SQL.
    void processSqlClause(final KeywordGroup keywordGroup,
                          final List<TokenType> consumedTokens,
                          final Query.Builder queryBuilder) {
        if (pendingJoin != null) {
            if (pendingJoin.bracketedQuery != null) {
                throw new TokenException(keywordGroup, "Put the sql clause of a bracketed query inside its "
                                                       + "brackets, straight after its from");
            } else if (pendingJoin.sqlToken != null) {
                throw new TokenException(keywordGroup, "A join line can only have one sql clause");
            }
            pendingJoin.sqlToken = keywordGroup;
            processSql(keywordGroup, pendingJoin.queryBuilder);
        } else if (!consumedTokens.isEmpty() && TokenType.FROM.equals(consumedTokens.getLast())) {
            if (fromQuery != null) {
                throw new TokenException(keywordGroup, "Put the sql clause of a bracketed query inside its "
                                                       + "brackets, straight after its from");
            }
            sqlToken = keywordGroup;
            processSql(keywordGroup, queryBuilder);
        } else {
            throw new TokenException(keywordGroup, "A sql clause must come straight after the data source "
                                                   + "it is for, after from or in a join line");
        }
    }

    /// Parses a join line: its kind and `join`, then a data source or a bracketed query, then an
    /// optional alias. Its keys follow in an on clause.
    ///
    /// @param keywordGroup The join line.
    void processJoin(final KeywordGroup keywordGroup) {
        final JoinType type = JoinLineParser.getJoinType(keywordGroup);
        final List<AbstractToken> children = keywordGroup.getChildren();
        if (children.isEmpty()) {
            throw new TokenException(keywordGroup, "Expected the data source to join after "
                                                   + type.getDisplayValue());
        }

        final AbstractToken source = children.getFirst();
        final PendingJoin join;
        if (source instanceof final TokenGroup bracketedQuery) {
            final ParsedQuery parsedQuery = parser.parseBracketedQuery(bracketedQuery);
            join = new PendingJoin(keywordGroup, type, parsedQuery.query().copy(), parsedQuery);
        } else {
            join = new PendingJoin(keywordGroup, type, createJoinedQuery(parser.resolveDataSource(source)), null);
        }

        final String givenAlias = JoinLineParser.parseAlias(children);
        if (givenAlias == null && join.bracketedQuery != null) {
            throw new TokenException(keywordGroup, "A bracketed query in a join line needs an alias for "
                                                   + "its fields, e.g. ( ... ) as x");
        }
        final String alias = givenAlias != null
                ? givenAlias
                : join.queryBuilder.build().getDataSource().getName();
        checkAliasUnused(alias, firstAlias, keywordGroup, "Give this join line its own, e.g. as x");
        join.alias = alias;
        pendingJoin = join;
    }

    // The search of a joined data source, which shares the params and time range of the query.
    private Query.Builder createJoinedQuery(final DocRef dataSource) {
        return Query.builder()
                .dataSource(dataSource)
                .params(parser.getParams())
                .timeRange(parser.getTimeRange());
    }

    // Refuses an alias that the first source or another join already uses. The first source is
    // known by its alias, or for the exists and in forms by its data source's name if it has none.
    private void checkAliasUnused(final String alias,
                                  final String firstSourceName,
                                  final AbstractToken errorToken,
                                  final String advice) {
        if (alias.equals(firstSourceName) || joins.stream().anyMatch(other -> other.alias.equals(alias))) {
            throw new TokenException(errorToken, "The alias '" + alias + "' is already used in this query. "
                                                 + advice);
        }
    }

    /// Rewrites the `exists` and `in` forms at the top level of where into semi and anti joins, as if
    /// they were join lines. See [WhereSubqueries].
    ///
    /// @param where        The where clause.
    /// @param queryBuilder The query, whose data source names the first source's fields if it has no
    ///                     alias.
    /// @return The where clause without the forms.
    KeywordGroup addWhereSubqueryJoins(final KeywordGroup where, final Query.Builder queryBuilder) {
        final WhereSubqueries.Extracted extracted = WhereSubqueries.extractForms(where, bracketed);
        if (!extracted.forms().isEmpty()) {
            final String outerName = firstAlias != null
                    ? firstAlias
                    : NullSafe.get(queryBuilder.build().getDataSource(), DocRef::getName);
            if (outerName == null) {
                throw new TokenException(extracted.forms().getFirst().errorToken(), "Give the bracketed query "
                        + "in from an alias to name its fields in where exists or in (from ...), e.g. as x");
            }
            extracted.forms().forEach(form -> addSubqueryJoin(form, outerName));
        }
        return extracted.where();
    }

    // Adds the semi or anti join an exists or in form stands for.
    private void addSubqueryJoin(final WhereSubqueries.Form form, final String outerName) {
        final WhereSubqueries.InnerQuery inner = WhereSubqueries.readInnerQuery(form);
        final DocRef dataSource = parser.resolveDataSource(inner.source());
        final Query.Builder joinQueryBuilder = createJoinedQuery(dataSource);
        final PendingJoin join = new PendingJoin(form.errorToken(), form.type(), joinQueryBuilder, null);
        if (inner.sql() != null) {
            join.sqlToken = inner.sql();
            processSql(inner.sql(), joinQueryBuilder);
        }
        final String alias = inner.alias() != null
                ? inner.alias()
                : dataSource.getName();
        checkAliasUnused(alias, outerName, form.errorToken(), "Give the bracketed query its own, "
                                                              + "e.g. (from X as x ...)");
        join.alias = alias;

        final WhereSubqueries.Correlation correlation =
                WhereSubqueries.splitCorrelation(inner.where(), outerName, alias);
        final List<WhereSubqueries.KeyFields> keyFields =
                WhereSubqueries.getKeyFields(form, inner, correlation, alias, outerName, firstAlias != null);
        keyFields.forEach(fields -> {
            fieldIndex.create(fields.first());
            fieldIndex.create(fields.joined());
        });
        join.keys = keyFields.stream().map(WhereSubqueries.KeyFields::toJoinKey).toList();

        // The bracketed query's other conditions go into its own search, as a joined source's
        // conditions moved out of where do.
        if (!correlation.rest().isEmpty()) {
            final ExpressionOperator own = ExpressionUtil.simplify(parser.processLogic(correlation.rest()));
            joinQueryBuilder.expression(JoinWhereSplitter.withoutAlias(own, alias));
        }
        parser.checkNativeQuery(joinQueryBuilder.build(), join.sqlToken, join.token);
        joins.add(join);
        fieldCountInKeys = fieldIndex.size();
    }

    /// Parses the keys of the join line being parsed, as in `on UserId = hr.Username and Host = hr.Host`,
    /// optionally followed by a time window, as in `within 10m after logon.EventTime`.
    ///
    /// @param keywordGroup The on clause.
    void processOn(final KeywordGroup keywordGroup) {
        if (pendingJoin == null) {
            throw new TokenException(keywordGroup, "on can only follow a join line");
        }
        final PendingJoin join = pendingJoin;
        final List<AbstractToken> children = keywordGroup.getChildren();
        final int windowStart = JoinLineParser.findWindowStart(children);
        join.keys = JoinLineParser.parseKeys(
                children.subList(0, windowStart),
                keywordGroup,
                join.alias,
                JoinLineParser.getFieldlessAliases(joins),
                parser::parseKeyExpression);
        if (windowStart < children.size()) {
            join.window = JoinLineParser.parseWindow(children.subList(windowStart, children.size()), keywordGroup);
        }

        if (join.bracketedQuery == null) {
            parser.checkNativeQuery(join.queryBuilder.build(), join.sqlToken, join.token);
        }
        joins.add(join);
        pendingJoin = null;
        // Every field named after this, by eval, select and the rest, is named outside the keys.
        fieldCountInKeys = fieldIndex.size();
    }

    // A right or full join gives its unmatched rows once every row has been probed, so it must be the
    // last join line, with no later join to apply to them, and can't have a time window, whose rows
    // are joined another way.
    private void checkRightAndFullJoins() {
        for (int i = 0; i < joins.size(); i++) {
            final PendingJoin join = joins.get(i);
            if (join.type.givesUnmatchedJoinedRows()) {
                final String kind = join.type.getDisplayValue();
                if (i < joins.size() - 1) {
                    throw new TokenException(join.token, "A " + kind + " must be the last join line, so it "
                                                         + "can't be followed by another, or used with where "
                                                         + "exists, where not exists or in (from ...), which "
                                                         + "become joins after it");
                }
                if (join.window != null) {
                    throw new TokenException(join.token, "A " + kind + " can't have a time window");
                }
            }
        }
    }

    /// Checks that a join line has had its keys before a clause other than its sql or keys ends it.
    ///
    /// @param keywordGroup The next clause.
    void checkJoinHasKeys(final KeywordGroup keywordGroup) {
        final TokenType tokenType = keywordGroup.getTokenType();
        if (pendingJoin != null && !TokenType.SQL.equals(tokenType) && !TokenType.ON.equals(tokenType)) {
            throw new TokenException(pendingJoin.token, JoinLineParser.getMissingKeysMessage(pendingJoin.alias));
        }
    }

    /// Places the where and filter clauses. With no joins, alias or bracketed query, where is the
    /// search expression as it always has been. Otherwise where is split between the searches and the
    /// joined rows, and the joins are added to the query.
    ///
    /// @param queryBuilder         The query, which takes the first source's search and the joins.
    /// @param tableSettingsBuilder The table, whose value filter takes the conditions on joined rows.
    /// @param whereExpression      The where clause, or `null`.
    /// @param filterExpression     The filter clause, or `null`.
    void placeConditions(final Query.Builder queryBuilder,
                         final TableSettings.Builder tableSettingsBuilder,
                         final ExpressionOperator whereExpression,
                         final ExpressionOperator filterExpression) {
        if (joins.isEmpty() && fromQuery == null && firstAlias == null) {
            queryBuilder.expression(whereExpression);
            tableSettingsBuilder.valueFilter(filterExpression);
            return;
        }

        checkRightAndFullJoins();
        final List<JoinedSource> joinedSources = joins.stream()
                .map(join -> new JoinedSource(join.alias,
                        join.type.allowsJoinedConditionPushDown() && join.bracketedQuery == null))
                .toList();
        // A right or full join gives rows with blank first-source fields, which where tests too, as in
        // SQL, so the first source's conditions run on the joined rows rather than in its search.
        final boolean firstPushDownAllowed = joins.stream()
                .noneMatch(join -> join.type.givesUnmatchedJoinedRows());
        final Split split = JoinWhereSplitter.split(
                whereExpression, firstAlias, fromQuery != null, firstPushDownAllowed, joinedSources);

        if (fromQuery != null) {
            final Query innerQuery = fromQuery.query();
            queryBuilder
                    .dataSource(innerQuery.getDataSource())
                    .expression(innerQuery.getExpression())
                    .nativeQuery(innerQuery.getNativeQuery())
                    .sourceTableSettings(fromQuery.tableSettings());
        } else {
            queryBuilder.expression(split.first());
        }

        final List<QueryJoin> queryJoins = new ArrayList<>();
        for (final PendingJoin join : joins) {
            final ExpressionOperator pushedDown = split.joined().get(join.alias);
            if (pushedDown != null) {
                // An exists or in query's own conditions are already there.
                final ExpressionOperator own = join.queryBuilder.build().getExpression();
                join.queryBuilder.expression(ExpressionUtil.combine(own, pushedDown));
            }
            Query joinQuery = join.queryBuilder.build();
            if (joinQuery.getExpression() == null) {
                joinQuery = joinQuery.copy().expression(ExpressionOperator.builder().build()).build();
            }
            queryJoins.add(new QueryJoin(
                    join.type,
                    join.alias,
                    joinQuery,
                    NullSafe.get(join.bracketedQuery, ParsedQuery::tableSettings),
                    join.keys,
                    join.window));
        }
        queryBuilder
                .alias(firstAlias)
                .joins(queryJoins.isEmpty()
                        ? null
                        : queryJoins);

        checkFieldlessAliasesUnused(split.postJoin());
        final ExpressionOperator postJoin = JoinWhereSplitter.renameFields(split.postJoin(), field ->
                postJoinColumns.computeIfAbsent(field, k ->
                        JoinWhereSplitter.POST_JOIN_COLUMN_PREFIX + (postJoinColumns.size() + 1) + "__"));
        tableSettingsBuilder.valueFilter(ExpressionUtil.combine(postJoin, filterExpression));
    }

    // A semi or anti join adds no fields to the rows, so its fields can only be named in its own
    // keys or in where conditions moved into its own search.
    private void checkFieldlessAliasesUnused(final ExpressionOperator postJoin) {
        final List<String> fieldsUsed = new ArrayList<>();
        final String[] fields = fieldIndex.getFields();
        fieldsUsed.addAll(Arrays.asList(fields).subList(fieldCountInKeys, fields.length));
        if (postJoin != null) {
            fieldsUsed.addAll(ExpressionUtil.fields(postJoin));
        }
        JoinLineParser.checkFieldlessFieldsUnused(fieldsUsed, joins);
    }


    // --------------------------------------------------------------------------------


    /// The parts of [SearchRequestFactory]'s parser that the from clause and join lines need.
    interface Parser {

        /// Parses a bracketed query with the params and time range of the query it is in.
        ///
        /// @param tokenGroup The brackets and their contents.
        /// @return The parsed query.
        ParsedQuery parseBracketedQuery(TokenGroup tokenGroup);

        /// Resolves a data source from its name.
        ///
        /// @param dataSourceToken The data source's name.
        /// @return The data source.
        DocRef resolveDataSource(AbstractToken dataSourceToken);

        /// Parses conditions joined by `and`, `or` and `not` into an expression.
        ///
        /// @param tokens The conditions.
        /// @return The expression.
        ExpressionOperator processLogic(List<AbstractToken> tokens);

        /// Parses one side of a join key into its expression text.
        ///
        /// @param tokens The tokens of the side.
        /// @return The expression text.
        String parseKeyExpression(List<AbstractToken> tokens);

        /// Checks that a data source that takes a native query has one, and no other has.
        ///
        /// @param query     The query of the data source.
        /// @param sqlToken  The sql clause, or `null`, for pointing errors at.
        /// @param fromToken The from clause or join line, for pointing errors at.
        void checkNativeQuery(Query query, AbstractToken sqlToken, AbstractToken fromToken);

        /// Gets the params of the request, which joined queries share.
        ///
        /// @return The params.
        List<Param> getParams();

        /// Gets the time range of the request, which joined queries share.
        ///
        /// @return The time range.
        TimeRange getTimeRange();
    }
}
