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

import stroom.query.api.Column;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Generator;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/// One side of the keys of a join line, compiled so the key of a row can be worked out.
///
/// The key of a row is the values of its key expressions, compared exactly as text. A row with a
/// blank or null value for any key has no key, so it never matches, as in SQL.
public final class JoinKeys {

    private final Generator[] generators;
    private final ValueReferenceIndex valueReferenceIndex;
    // The length a row must have to hold every field the keys name.
    private final int rowLength;

    private JoinKeys(final Generator[] generators,
                     final ValueReferenceIndex valueReferenceIndex,
                     final int rowLength) {
        this.generators = generators;
        this.valueReferenceIndex = valueReferenceIndex;
        this.rowLength = rowLength;
    }

    /// Compiles the key expressions of one side of a join line.
    ///
    /// @param expressionContext The expression context of the query.
    /// @param expressions       The key expressions, e.g. `lowerCase(${UserId})`.
    /// @param fieldIndex        The positions of the fields in the rows the keys are worked out from.
    ///                          Fields the expressions name are added to it.
    /// @param paramMap          The query's parameters.
    /// @return The compiled keys.
    public static JoinKeys compile(final ExpressionContext expressionContext,
                                   final List<String> expressions,
                                   final FieldIndex fieldIndex,
                                   final Map<String, String> paramMap) {
        final List<Column> columns = new ArrayList<>(expressions.size());
        for (int i = 0; i < expressions.size(); i++) {
            columns.add(Column.builder()
                    .id("key" + i)
                    .name("key" + i)
                    .expression(expressions.get(i))
                    .build());
        }
        final CompiledColumns compiledColumns = CompiledColumns.create(
                expressionContext, columns, fieldIndex, paramMap);
        final CompiledColumn[] compiledColumnArray = compiledColumns.getCompiledColumns();
        final Generator[] generators = new Generator[compiledColumnArray.length];
        for (int i = 0; i < compiledColumnArray.length; i++) {
            final CompiledColumn compiledColumn = compiledColumnArray[i];
            if (compiledColumn.hasAggregate()) {
                throw new IllegalArgumentException("A join key can't use an aggregate function such as count(): "
                                                   + expressions.get(i));
            }
            generators[i] = compiledColumn.getGenerator();
        }
        return new JoinKeys(generators, compiledColumns.getValueReferenceIndex(), fieldIndex.size());
    }

    /// Works out the key of a row.
    ///
    /// @param row The row's values, at the positions of the field index the keys were compiled with.
    /// @return The key, or `null` if any of its values is blank or null.
    public String getKey(final Val[] row) {
        // A row too short to hold a key field has no value for it.
        final Val[] values = row.length < rowLength
                ? Arrays.copyOf(row, rowLength)
                : row;
        final StoredValues storedValues = valueReferenceIndex.createStoredValues();
        final StringBuilder key = new StringBuilder();
        for (final Generator generator : generators) {
            generator.set(values, storedValues);
            final Val val = generator.eval(storedValues, null);
            final String text = val == null || !val.type().isValue()
                    ? null
                    : val.toString();
            if (text == null || text.isBlank()) {
                return null;
            }
            // Each value is prefixed with its length, so no two lists of values make the same key.
            key.append(text.length()).append(':').append(text);
        }
        return key.toString();
    }
}
