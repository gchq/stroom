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

package stroom.sqldatasource.impl;

import stroom.query.api.datasource.FieldType;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactory;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.util.date.DateUtil;

import java.util.function.Function;

/// Reads one field from a row of values, so the `where` expression can be tested against the row.
///
/// The field type is the type of the column the field comes from, so dates and numbers are compared
/// as dates and numbers rather than as text.
class RowValueFunctionFactory implements ValueFunctionFactory<Val[]> {

    private final int index;
    private final FieldType fieldType;

    /// @param index     The field's position in each row.
    /// @param fieldType The type of the column the field comes from.
    RowValueFunctionFactory(final int index, final FieldType fieldType) {
        this.index = index;
        this.fieldType = fieldType;
    }

    @Override
    public Function<Val[], Boolean> createNullCheck() {
        return row -> Type.NULL.equals(row[index].type());
    }

    @Override
    public Function<Val[], String> createStringExtractor() {
        return row -> row[index].toString();
    }

    @Override
    public Function<Val[], Long> createDateExtractor() {
        return row -> {
            final Val val = row[index];
            if (Type.DATE.equals(val.type()) || Type.LONG.equals(val.type())) {
                return val.toLong();
            }
            final String text = val.toString();
            if (text == null) {
                return null;
            }
            try {
                return DateUtil.parseNormalDateTimeString(text);
            } catch (final RuntimeException e) {
                return null;
            }
        };
    }

    @Override
    public Function<Val[], Double> createNumberExtractor() {
        return row -> {
            try {
                return row[index].toDouble();
            } catch (final RuntimeException e) {
                return null;
            }
        };
    }

    @Override
    public FieldType getFieldType() {
        return fieldType;
    }
}
