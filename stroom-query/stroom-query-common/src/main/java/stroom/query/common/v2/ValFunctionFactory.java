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
import stroom.query.api.Format;
import stroom.query.api.datasource.FieldType;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactory;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.util.date.DateUtil;

import java.util.function.Function;

public class ValFunctionFactory implements ValueFunctionFactory<Val> {

    private final Column column;

    public ValFunctionFactory(final Column column) {
        this.column = column;
    }

    @Override
    public Function<Val, Boolean> createNullCheck() {
        return values -> Type.NULL.equals(values.type());
    }

    @Override
    public Function<Val, String> createStringExtractor() {
        return Val::toString;
    }

    @Override
    public Function<Val, Long> createDateExtractor() {
        return values -> {
            if (Type.LONG.equals(values.type()) || Type.DATE.equals(values.type())) {
                return values.toLong();
            } else {
                final String string = values.toString();
                if (string != null) {
                    try {
                        return DateUtil.parseNormalDateTimeString(string);
                    } catch (final NumberFormatException e) {
                        return null;
                    }
                }
            }
            return null;
        };
    }

    @Override
    public Function<Val, Double> createNumberExtractor() {
        return values -> {
            try {
                return values.toDouble();
            } catch (final RuntimeException e) {
                return null;
            }
        };
    }

    @Override
    public FieldType getFieldType() {
        FieldType fieldType = FieldType.TEXT;
        if (column.getFormat() != null) {
            if (Format.Type.NUMBER.equals(column.getFormat().getType())) {
                fieldType = FieldType.LONG;
            } else if (Format.Type.DATE_TIME.equals(column.getFormat().getType())) {
                fieldType = FieldType.DATE;
            }
        }
        return fieldType;
    }
}
