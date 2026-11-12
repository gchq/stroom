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

import stroom.query.api.JoinKey;
import stroom.query.api.Query;
import stroom.query.api.QueryJoin;
import stroom.query.language.JoinWhereSplitter;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// What running one join line needs: the joined source's fields its table must hold, and the keys
/// of both sides compiled.
///
/// The joined source's rows are held with only the fields the query names, in the order of
/// [#getStoredFields()], without the alias prefix. The left keys are compiled against the field index
/// of the rows the join is applied to, which adds the fields they name so the first source's search
/// extracts them.
public final class JoinPlan {

    // A join table row holds its number of values in one byte.
    private static final int MAX_STORED_FIELDS = 255;

    private final QueryJoin join;
    private final List<String> storedFields;
    private final JoinKeys leftKeys;
    private final JoinKeys rightKeys;

    private JoinPlan(final QueryJoin join,
                     final List<String> storedFields,
                     final JoinKeys leftKeys,
                     final JoinKeys rightKeys) {
        this.join = join;
        this.storedFields = storedFields;
        this.leftKeys = leftKeys;
        this.rightKeys = rightKeys;
    }

    /// Plans every join line of a query.
    ///
    /// Call this once the query's coprocessors exist, so the field index holds every field the query
    /// names, and before the first source's search starts, as the left keys add the fields they name.
    ///
    /// @param query             The query with joins.
    /// @param fieldIndex        The field index of the query's coprocessors.
    /// @param expressionContext The query's expression context.
    /// @param paramMap          The query's parameters.
    /// @return A plan for each join line, in order.
    public static List<JoinPlan> createAll(final Query query,
                                           final FieldIndex fieldIndex,
                                           final ExpressionContext expressionContext,
                                           final Map<String, String> paramMap) {
        final List<QueryJoin> joins = NullSafe.list(query.getJoins());
        for (final QueryJoin join : joins) {
            if (join.getWindow() != null) {
                throw new IllegalArgumentException("Joins with a time window, as in 'within 10m after', "
                                                   + "can't be run yet");
            }
        }

        // The left keys first, as a later join's keys can name an earlier join's fields, which its
        // table must then hold.
        final List<JoinKeys> leftKeys = new ArrayList<>(joins.size());
        for (final QueryJoin join : joins) {
            leftKeys.add(JoinKeys.compile(expressionContext, getLeftExpressions(join), fieldIndex, paramMap));
        }

        final List<String> aliases = getAliases(query);
        final List<JoinPlan> plans = new ArrayList<>(joins.size());
        for (int i = 0; i < joins.size(); i++) {
            final QueryJoin join = joins.get(i);
            final List<String> storedFields = getStoredFields(join, fieldIndex, aliases, expressionContext, paramMap);
            if (storedFields.size() > MAX_STORED_FIELDS) {
                throw new IllegalArgumentException("A join can use at most " + MAX_STORED_FIELDS + " fields of '"
                                                   + join.getAlias() + "', but the query uses "
                                                   + storedFields.size());
            }
            final FieldIndex rightFieldIndex = new FieldIndex();
            storedFields.forEach(field -> rightFieldIndex.create(join.getAlias() + "." + field));
            final JoinKeys rightKeys = JoinKeys.compile(
                    expressionContext, getRightExpressions(join), rightFieldIndex, paramMap);
            plans.add(new JoinPlan(join, storedFields, leftKeys.get(i), rightKeys));
        }
        return Collections.unmodifiableList(plans);
    }

    /// Gets the joined source's fields a join must hold: those its keys name, then those the rest of
    /// the query names, without the alias prefix.
    ///
    /// @param join              The join line.
    /// @param fieldIndex        The fields the query names.
    /// @param aliases           Every alias of the query.
    /// @param expressionContext The query's expression context.
    /// @param paramMap          The query's parameters.
    /// @return The field names, without the join's alias.
    static List<String> getStoredFields(final QueryJoin join,
                                                final FieldIndex fieldIndex,
                                                final List<String> aliases,
                                                final ExpressionContext expressionContext,
                                                final Map<String, String> paramMap) {
        final Set<String> fields = new LinkedHashSet<>();
        final FieldIndex keyFieldIndex = new FieldIndex();
        JoinKeys.compile(expressionContext, getRightExpressions(join), keyFieldIndex, paramMap);
        addFieldsOf(join.getAlias(), keyFieldIndex.getFields(), aliases, fields);
        addFieldsOf(join.getAlias(), fieldIndex.getFields(), aliases, fields);
        return List.copyOf(fields);
    }

    private static void addFieldsOf(final String alias,
                                    final String[] names,
                                    final List<String> aliases,
                                    final Set<String> fields) {
        for (final String name : names) {
            if (alias.equals(JoinWhereSplitter.findAlias(name, aliases))) {
                fields.add(name.substring(alias.length() + 1));
            }
        }
    }

    /// Gets every alias of a query: those of its join lines and its first source's, if it has one.
    ///
    /// @param query The query.
    /// @return The aliases.
    public static List<String> getAliases(final Query query) {
        final List<String> aliases = new ArrayList<>();
        NullSafe.list(query.getJoins()).forEach(join -> aliases.add(join.getAlias()));
        if (query.getAlias() != null) {
            aliases.add(query.getAlias());
        }
        return aliases;
    }

    /// Finds whether a field of a query is named with an alias: a joined source's field, which the
    /// joins fill in, or the first source's field written with its alias, which is given the value of
    /// the field it names. A data source's search supplies neither, so it should not warn about them.
    ///
    /// @param query The query.
    /// @param field The field name.
    /// @return True if the field is named with one of the query's aliases.
    public static boolean isAliasedField(final Query query, final String field) {
        return query != null && JoinWhereSplitter.findAlias(field, getAliases(query)) != null;
    }

    private static List<String> getLeftExpressions(final QueryJoin join) {
        return join.getKeys().stream().map(JoinKey::getLeft).toList();
    }

    private static List<String> getRightExpressions(final QueryJoin join) {
        return join.getKeys().stream().map(JoinKey::getRight).toList();
    }

    /// Gets the join line.
    ///
    /// @return The join line.
    public QueryJoin getJoin() {
        return join;
    }

    /// Gets the joined source's fields its table holds, in the order of the values of each held row,
    /// without the alias prefix.
    ///
    /// @return The fields, e.g. `Username` and `Department` for `hr.Username` and `hr.Department`.
    public List<String> getStoredFields() {
        return storedFields;
    }

    /// Gets the left keys, compiled against the field index of the rows the join is applied to.
    ///
    /// @return The left keys.
    public JoinKeys getLeftKeys() {
        return leftKeys;
    }

    /// Works out the join key of a joined source's row.
    ///
    /// @param storedRow The row's values for the fields of [#getStoredFields()], in that order.
    /// @return The key, or `null` if it has none.
    public String getRightKey(final Val[] storedRow) {
        return rightKeys.getKey(storedRow);
    }
}
