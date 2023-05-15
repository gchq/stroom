/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.ExpressionParser;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.Null;
import stroom.dashboard.expression.v1.ParamFactory;
import stroom.dashboard.expression.v1.StaticValueGen;
import stroom.dashboard.expression.v1.ref.ValueReferenceIndex;
import stroom.query.api.v2.Field;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

public class CompiledFields {

    private final CompiledField[] compiledFields;
    private final FieldIndex fieldIndex;
    private final ValueReferenceIndex valueReferenceIndex;

    private CompiledFields(final CompiledField[] compiledFields,
                           final FieldIndex fieldIndex,
                           final ValueReferenceIndex valueReferenceIndex) {
        this.compiledFields = compiledFields;
        this.fieldIndex = fieldIndex;
        this.valueReferenceIndex = valueReferenceIndex;
    }

    public static CompiledFields create(final List<Field> fields,
                                        final Map<String, String> paramMap) {
        return create(fields, new FieldIndex(), paramMap);
    }

    public static CompiledFields create(final List<Field> fields,
                                        final FieldIndex fieldIndex,
                                        final Map<String, String> paramMap) {
        final ValueReferenceIndex valueReferenceIndex = new ValueReferenceIndex();
        if (fields == null) {
            return new CompiledFields(new CompiledField[0], fieldIndex, valueReferenceIndex);
        }

        final ExpressionParser expressionParser = new ExpressionParser(new ParamFactory());
        final CompiledField[] compiledFields = new CompiledField[fields.size()];
        int i = 0;

        for (final Field field : fields) {
            // Create compiled field.
            int groupDepth = -1;
            if (field.getGroup() != null) {
                groupDepth = field.getGroup();
            }
            Generator generator = Null.GEN;
            boolean hasAggregate = false;
            boolean requiresChildData = false;
            if (field.getExpression() != null && field.getExpression().trim().length() > 0) {
                try {
                    final Expression expression = expressionParser.parse(fieldIndex, field.getExpression());
                    expression.setStaticMappedValues(paramMap);
                    expression.addValueReferences(valueReferenceIndex);
                    generator = expression.createGenerator();
                    hasAggregate = expression.hasAggregate();
                    requiresChildData = expression.requiresChildData();
                } catch (final ParseException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }

            CompiledFilter filter = null;
            if (field.getFilter() != null) {
                filter = new CompiledFilter(field.getFilter(), paramMap);
            }

            final CompiledField compiledField =
                    new CompiledField(field, groupDepth, generator, hasAggregate, requiresChildData, filter);

            // Only include this field if it is used for display, grouping,
            // sorting.
            compiledFields[i++] = compiledField;
        }

        return new CompiledFields(compiledFields, fieldIndex, valueReferenceIndex);
    }

    public CompiledField[] getCompiledFields() {
        return compiledFields;
    }

    public FieldIndex getFieldIndex() {
        return fieldIndex;
    }

    public ValueReferenceIndex getValueReferenceIndex() {
        return valueReferenceIndex;
    }
}
