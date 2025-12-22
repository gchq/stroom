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

package stroom.util.filter;

import stroom.util.shared.filter.FilterFieldDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

/**
 * Defines the mapping between a FilterFieldDefinition and a property
 * of T_ROW. The valueExtractor should return a value for the field
 * from an instance of T_ROW.
 */
public class FilterFieldMapper<T_ROW> {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilterFieldMapper.class);

    private final FilterFieldDefinition fieldDefinition;
    private final Function<T_ROW, String> valueExtractor;

    private FilterFieldMapper(final FilterFieldDefinition fieldDefinition,
                              final Function<T_ROW, String> valueExtractor) {
        this.fieldDefinition = Objects.requireNonNull(fieldDefinition);
        this.valueExtractor = Objects.requireNonNull(valueExtractor);
    }

    public static <T_ROW> FilterFieldMapper<T_ROW> of(
            final FilterFieldDefinition fieldDefinition,
            final Function<T_ROW, String> valueExtractor) {
        return new FilterFieldMapper<>(fieldDefinition, valueExtractor);
    }

    /**
     * Overloaded factory method that allows you to define a nested value
     * extractor that is null safe. The value of each function is chained
     * in a null safe way.
     */
    public static <T_ROW, T_FIELD> FilterFieldMapper<T_ROW> of(
            final FilterFieldDefinition fieldDefinition,
            final Function<T_ROW, T_FIELD> function1,
            final Function<T_FIELD, String> function2) {

        final Function<T_ROW, String> valueExtractor = row -> {
            final String result;
            if (row != null) {
                final T_FIELD fieldVal = function1.apply(row);
                if (fieldVal != null) {
                    result = function2.apply(fieldVal);
                } else {
                    result = null;
                }
            } else {
                result = null;
            }
            return result;
        };
        return new FilterFieldMapper<>(fieldDefinition, valueExtractor);
    }

    /**
     * Overloaded factory method that allows you to define a nested value
     * extractor that is null safe. The value of each function is chained
     * in a null safe way.
     */
    public static <T_ROW, T_FIELD, T_SUB_FIELD> FilterFieldMapper<T_ROW> of(
            final FilterFieldDefinition fieldDefinition,
            final Function<T_ROW, T_FIELD> function1,
            final Function<T_FIELD, T_SUB_FIELD> function2,
            final Function<T_SUB_FIELD, String> function3) {

        final Function<T_ROW, String> valueExtractor = row -> {
            final String result;
            if (row != null) {
                final T_FIELD fieldVal = function1.apply(row);
                if (fieldVal != null) {
                    final T_SUB_FIELD subFieldVal = function2.apply(fieldVal);
                    if (subFieldVal != null) {
                        return function3.apply(subFieldVal);
                    } else {
                        result = null;
                    }
                } else {
                    result = null;
                }
            } else {
                result = null;
            }
            return result;
        };
        return new FilterFieldMapper<>(fieldDefinition, valueExtractor);
    }

    public FilterFieldDefinition getFieldDefinition() {
        return fieldDefinition;
    }

    public String extractFieldValue(final T_ROW row) {
        // Useful is unit tests, VERY noisy with lots of data
        if (LOGGER.isTraceEnabled()) {
            final String val = valueExtractor.apply(row);
            LOGGER.trace("Extracted [{}] from field {} in [{}]", val, fieldDefinition, row);
            return val;
        } else {
            return valueExtractor.apply(row);
        }
    }

//    public Function<T_ROW, String> getNullSafeStringValueExtractor() {
//        // Useful is unit tests, VERY noisy with lots of data
//        if (LOGGER.isTraceEnabled()) {
//            return row -> {
//                String val = valueExtractor.apply(row);
//                LOGGER.trace("Extracted [{}] from field {} in [{}]", val, fieldDefinition, row);
//                return val;
//            };
//        }
//        return valueExtractor;
//    }

    @Override
    public String toString() {
        return fieldDefinition.toString();
    }
}
