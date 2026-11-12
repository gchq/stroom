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

package stroom.query.common.v2;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.JoinKey;
import stroom.query.api.JoinType;
import stroom.query.api.Query;
import stroom.query.api.QueryJoin;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.JoinWhereSplitter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Narrows the search of a query's first source using the keys of its join tables, so the data
/// source skips rows the joins would drop anyway.
///
/// A join whose table has at most [#MAX_KEYS] keys, with one key whose left side is a plain field
/// of the first source that the data source can search with `in`, adds a term to the first source's
/// query:
///
/// * An inner or keep join adds `Field in (the keys)`, as a row with any other value has no match.
///   A data source that matches more loosely than the join, ignoring case for example, only lets
///   through rows the join then drops.
/// * A drop join adds `not (Field in (the keys))`, but only for an ID, integer or long field, where
///   the data source matches the keys exactly as the join does. For other fields a data source that
///   matches more loosely would drop rows the join keeps.
///
/// A key the data source might read as something other than plain text, such as one with spaces,
/// commas, quotes or wildcards, stops the join being pushed down. So does a number key the data
/// source can't hold exactly: one too big for an integer field, one beyond the 15 digits a double
/// holds exactly (some data sources compare numbers as doubles), or one written as no field value
/// is, such as `-0` or `007`. Pushing down never changes the result, as the joins still check every
/// row.
public class JoinPushdown {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JoinPushdown.class);

    /// The most keys a join table may have for its join to be pushed down. An `in` term on a text
    /// field is a clause for each key to Lucene, Solr and Elasticsearch, which refuse queries of more
    /// than 1,024 clauses by default, so this stays below that.
    public static final int MAX_KEYS = 1_000;

    // A left key that is just a field, e.g. ${UserId}.
    private static final Pattern FIELD_REFERENCE = Pattern.compile("^\\$\\{([^{}]+)}$");
    // A key every data source reads as the same plain text in an 'in' term.
    private static final Pattern PLAIN_KEY = Pattern.compile("^[A-Za-z0-9_.@:-]+$");
    // A key that is a whole number, written as a number field's value is, of at most 15 digits.
    private static final Pattern WHOLE_NUMBER_KEY = Pattern.compile("^(0|-?[1-9][0-9]{0,14})$");

    private final SearchProviderRegistry searchProviderRegistry;

    @Inject
    public JoinPushdown(final SearchProviderRegistry searchProviderRegistry) {
        this.searchProviderRegistry = searchProviderRegistry;
    }

    /// Adds the terms of the joins that can be pushed down to the query of the first source.
    ///
    /// @param query    The query with joins, whose first source is a data source, not a bracketed
    ///                 query.
    /// @param keys     The keys of each join's table, in the order of the query's join lines.
    /// @param paramMap The query's parameters.
    /// @return The query, with the joins' terms added to its expression, or the query as it was if no
    /// join can be pushed down.
    public Query apply(final Query query,
                       final List<Keys> keys,
                       final Map<String, String> paramMap) {
        final List<QueryJoin> joins = query.getJoins();
        final List<ExpressionOperator> pushedDown = new ArrayList<>();
        // The first source's fields, found once if a join needs them.
        final Map<String, QueryField> fields = new HashMap<>();
        for (int i = 0; i < joins.size(); i++) {
            createPushedDown(query, joins.get(i), keys.get(i), paramMap, fields).ifPresent(pushedDown::add);
        }
        if (pushedDown.isEmpty()) {
            return query;
        }
        final ExpressionOperator.Builder expression = ExpressionOperator.builder().op(Op.AND);
        if (query.getExpression() != null) {
            expression.addOperator(query.getExpression());
        }
        expression.addOperators(pushedDown);
        return query.copy().expression(expression.build()).build();
    }

    // The condition a join adds to the first source's query, if it can be pushed down.
    private Optional<ExpressionOperator> createPushedDown(final Query query,
                                                          final QueryJoin join,
                                                          final Keys keys,
                                                          final Map<String, String> paramMap,
                                                          final Map<String, QueryField> fields) {
        final JoinType type = join.getType();
        if (!Keys.mayPushDown(join) || !keys.hasValues() || keys.getValues().isEmpty()) {
            return Optional.empty();
        }
        final String field = getFirstSourceField(query, join.getKeys().getFirst(), paramMap);
        if (field == null) {
            return Optional.empty();
        }
        if (fields.isEmpty()) {
            fields.putAll(findFields(query));
        }
        final QueryField queryField = fields.get(field);
        if (queryField == null || !canSearchWithIn(queryField)) {
            return Optional.empty();
        }
        final boolean drop = type == JoinType.DROP;
        if (drop && !isMatchedExactly(queryField.getFldType())) {
            return Optional.empty();
        }
        final Set<String> values = keys.getValues();
        if (!values.stream().allMatch(value -> isPlainKey(queryField.getFldType(), value))) {
            LOGGER.debug("Not pushing down the join of '{}', as a key isn't plain", join.getAlias());
            return Optional.empty();
        }

        LOGGER.debug(() -> LogUtil.message("Pushing down the {} of '{}' as {} keys of {}",
                type.getDisplayValue(), join.getAlias(), values.size(), field));
        final ExpressionTerm in = ExpressionTerm.builder()
                .field(field)
                .condition(Condition.IN)
                .value(String.join(",", values))
                .build();
        return Optional.of(ExpressionOperator.builder()
                .op(drop
                        ? Op.NOT
                        : Op.AND)
                .addTerm(in)
                .build());
    }

    // The first source field a left key is, without any alias of the first source, or null if the
    // key is anything else, such as an expression, a parameter or a joined source's field.
    private static String getFirstSourceField(final Query query,
                                              final JoinKey key,
                                              final Map<String, String> paramMap) {
        final Matcher matcher = FIELD_REFERENCE.matcher(key.getLeft());
        if (!matcher.matches()) {
            return null;
        }
        final String name = matcher.group(1);
        if (paramMap.containsKey(name)) {
            return null;
        }
        final String alias = JoinWhereSplitter.findAlias(name, JoinPlan.getAliases(query));
        if (alias == null) {
            return name;
        }
        return alias.equals(query.getAlias())
                ? name.substring(alias.length() + 1)
                : null;
    }

    // Finds the fields of the first source, by name.
    private Map<String, QueryField> findFields(final Query query) {
        final DocRef dataSource = query.getDataSource();
        final Map<String, QueryField> fields = new HashMap<>();
        try {
            final FindFieldCriteria criteria = new FindFieldCriteria(
                    PageRequest.unlimited(),
                    FindFieldCriteria.DEFAULT_SORT_LIST,
                    dataSource,
                    null,
                    null,
                    query.getNativeQuery());
            searchProviderRegistry.getSearchProvider(dataSource)
                    .ifPresent(provider -> provider.getFieldInfo(criteria)
                            .getValues()
                            .forEach(queryField -> fields.putIfAbsent(queryField.getFldName(), queryField)));
        } catch (final RuntimeException e) {
            // Pushing down is only a way to search less, so a join that can't be pushed down still
            // works.
            LOGGER.debug(() -> "Unable to find the fields of " + dataSource + ": " + e.getMessage(), e);
        }
        return fields;
    }

    // True if the data source reads the key as the join does, as a value of the field.
    private static boolean isPlainKey(final FieldType fieldType, final String key) {
        if (!fieldType.isNumeric()) {
            return PLAIN_KEY.matcher(key).matches();
        }
        if (!WHOLE_NUMBER_KEY.matcher(key).matches()) {
            return false;
        }
        if (fieldType == FieldType.INTEGER) {
            final long number = Long.parseLong(key);
            return number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE;
        }
        return true;
    }

    private static boolean canSearchWithIn(final QueryField queryField) {
        if (!queryField.queryable() || queryField.getFldType() == null) {
            return false;
        }
        final ConditionSet conditionSet = Objects.requireNonNullElse(
                queryField.getConditionSet(),
                ConditionSet.getDefault(queryField.getFldType()));
        if (!conditionSet.supportsCondition(Condition.IN)) {
            return false;
        }
        // A key is the text of a value, which a date, decimal, boolean or address field may hold
        // written another way.
        return switch (queryField.getFldType()) {
            case ID, INTEGER, LONG, KEYWORD, TEXT -> true;
            default -> false;
        };
    }

    // A field whose values every data source matches exactly as the join does.
    private static boolean isMatchedExactly(final FieldType fieldType) {
        return fieldType == FieldType.ID || fieldType == FieldType.INTEGER || fieldType == FieldType.LONG;
    }


    // --------------------------------------------------------------------------------


    /// Collects the distinct values of a join table's keys while it is built, until there are too many
    /// to push down. Only the keys of joins that might be pushed down are collected.
    public static final class Keys {

        // Null once there are too many, or if not collected.
        private Set<String> values;

        private Keys(final Set<String> values) {
            this.values = values;
        }

        /// Creates the collector of a join's keys, which collects nothing for a join that can't be
        /// pushed down, such as a left join or a join on several keys.
        ///
        /// @param join The join line.
        /// @return The collector.
        public static Keys forJoin(final QueryJoin join) {
            return new Keys(mayPushDown(join)
                    ? new LinkedHashSet<>()
                    : null);
        }

        // A left join keeps every row, and the keys of a join on several keys can't be one 'in' term.
        private static boolean mayPushDown(final QueryJoin join) {
            return join.getType() != JoinType.LEFT && NullSafe.size(join.getKeys()) == 1;
        }

        /// Adds a join key.
        ///
        /// @param key The key, as [JoinKeys#getKey] makes it, or `null` for a row with no key, which is
        ///            ignored.
        public void add(final String key) {
            if (key == null || values == null) {
                return;
            }
            values.add(getSingleValue(key));
            if (values.size() > MAX_KEYS) {
                values = null;
            }
        }

        /// Finds whether the keys were collected, as they are for a join that might be pushed down
        /// whose table has at most [#MAX_KEYS] keys.
        ///
        /// @return True if they were.
        public boolean hasValues() {
            return values != null;
        }

        /// Gets the values of the keys.
        ///
        /// @return The values, in the order first seen.
        /// @throws IllegalStateException If they weren't collected.
        public Set<String> getValues() {
            if (values == null) {
                throw new IllegalStateException("The keys weren't collected");
            }
            return values;
        }

        // A key of one value is its length, a colon, then the value.
        private static String getSingleValue(final String key) {
            return key.substring(key.indexOf(':') + 1);
        }
    }
}
