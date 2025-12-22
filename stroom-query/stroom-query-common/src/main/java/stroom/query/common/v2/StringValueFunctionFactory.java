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

import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactory;
import stroom.util.date.DateUtil;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Function;

public class StringValueFunctionFactory implements ValueFunctionFactory<String> {

    private final QueryField field;

    public StringValueFunctionFactory(final QueryField field) {
        this.field = field;
    }

    public static ValueFunctionFactories<String> create(final QueryField field) {
        final StringValueFunctionFactory stringValueFunctionFactory = new StringValueFunctionFactory(field);
        return fieldName -> stringValueFunctionFactory;
    }

    @Override
    public Function<String, Boolean> createNullCheck() {
        return Objects::isNull;
    }

    @Override
    public Function<String, String> createStringExtractor() {
        return string -> string;
    }

    @Override
    public Function<String, Long> createDateExtractor() {
        return DateUtil::parseNormalDateTimeString;
    }

    @Override
    public Function<String, Double> createNumberExtractor() {
        return string -> {
            try {
                return new BigDecimal(string).doubleValue();
            } catch (final RuntimeException e) {
                return null;
            }
        };
    }

    @Override
    public FieldType getFieldType() {
        return field.getFldType();
    }
}
