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

package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Function;

public class AttributeMapFunctionFactory implements ValueFunctionFactory<AttributeMap> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AttributeMapFunctionFactory.class);

    private final FieldType fieldType;
    private final String fieldName;

    AttributeMapFunctionFactory(final QueryField queryField) {
        Objects.requireNonNull(queryField);
        this.fieldType = queryField.getFldType();
        this.fieldName = queryField.getFldName();
    }

    @Override
    public Function<AttributeMap, Boolean> createNullCheck() {
        return attributeMap -> Objects.isNull(attributeMap.get(fieldName));
    }

    @Override
    public Function<AttributeMap, String> createStringExtractor() {
        return attributeMap -> attributeMap.get(fieldName);
    }

    @Override
    public Function<AttributeMap, Long> createDateExtractor() {
        return attributeMap -> {
            try {
                return attributeMap.getAsEpochMillis(fieldName);
            } catch (final Exception e) {
                // attributeMap could contain any old rubbish so swallow and return null
                LOGGER.debug(LogUtil.message("Error extracting field {} of type {} as millis: {}",
                        fieldName, fieldType, LogUtil.exceptionMessage(e), e));
                return null;
            }
        };
    }

    @Override
    public Function<AttributeMap, Double> createNumberExtractor() {
        return attributeMap -> {
            try {
                return NullSafe.get(
                        attributeMap.get(fieldName),
                        val -> new BigDecimal(val).doubleValue());
            } catch (final RuntimeException e) {
                // attributeMap could contain any old rubbish so swallow and return null
                LOGGER.debug(LogUtil.message("Error extracting field {} of type {} as double: {}",
                        fieldName, fieldType, LogUtil.exceptionMessage(e), e));
                return null;
            }
        };
    }

    @Override
    public FieldType getFieldType() {
        return fieldType;
    }
}
