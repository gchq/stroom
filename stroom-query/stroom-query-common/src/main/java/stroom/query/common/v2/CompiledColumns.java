/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.query.language.functions.Expression;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.ExpressionParser;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Generator;
import stroom.query.language.functions.Null;
import stroom.query.language.functions.ParamFactory;
import stroom.query.language.functions.ref.ValueReferenceIndex;
import stroom.util.shared.NullSafe;

import java.text.ParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompiledColumns {

    private final List<Column> columns;
    private final CompiledColumn[] compiledColumns;
    private final FieldIndex fieldIndex;
    private final ValueReferenceIndex valueReferenceIndex;

    private CompiledColumns(final List<Column> columns,
                            final CompiledColumn[] compiledColumns,
                            final FieldIndex fieldIndex,
                            final ValueReferenceIndex valueReferenceIndex) {
        this.columns = columns;
        this.compiledColumns = compiledColumns;
        this.fieldIndex = fieldIndex;
        this.valueReferenceIndex = valueReferenceIndex;
    }

    public static CompiledColumns create(final ExpressionContext expressionContext,
                                         final List<Column> columns,
                                         final Map<String, String> paramMap) {
        return create(expressionContext, columns, new FieldIndex(), paramMap);
    }

    public static CompiledColumns create(final ExpressionContext expressionContext,
                                         final List<Column> columns,
                                         final FieldIndex fieldIndex,
                                         final Map<String, String> paramMap) {
        final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
        if (columns == null) {
            return new CompiledColumns(Collections.emptyList(), new CompiledColumn[0], fieldIndex, valueReferenceIndex);
        }

        final ExpressionParser expressionParser = new ExpressionParser(new ParamFactory(new HashMap<>()));
        final CompiledColumn[] compiledFields = new CompiledColumn[columns.size()];
        int i = 0;

        for (final Column column : columns) {
            // Create compiled field.
            int groupDepth = -1;
            if (column.getGroup() != null) {
                groupDepth = column.getGroup();
            }
            Generator generator = Null.GEN;
            boolean hasAggregate = false;
            boolean requiresChildData = false;
            if (!NullSafe.isBlankString(column.getExpression())) {
                try {
                    final Expression expression = expressionParser.parse(
                            expressionContext,
                            fieldIndex,
                            column.getExpression());
                    expression.setStaticMappedValues(paramMap);
                    expression.addValueReferences(valueReferenceIndex);
                    generator = expression.createGenerator();
                    hasAggregate = expression.hasAggregate();
                    requiresChildData = expression.requiresChildData();
                } catch (final ParseException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }

            final CompiledColumn compiledField =
                    new CompiledColumn(
                            column,
                            groupDepth,
                            generator,
                            hasAggregate,
                            requiresChildData);

            // Only include this field if it is used for display, grouping,
            // sorting.
            compiledFields[i++] = compiledField;
        }

        return new CompiledColumns(columns, compiledFields, fieldIndex, valueReferenceIndex);
    }

    public List<Column> getColumns() {
        return columns;
    }

    public CompiledColumn[] getCompiledColumns() {
        return compiledColumns;
    }

    public FieldIndex getFieldIndex() {
        return fieldIndex;
    }

    public ValueReferenceIndex getValueReferenceIndex() {
        return valueReferenceIndex;
    }
}
