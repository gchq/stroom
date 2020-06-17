package stroom.util.filter;

import stroom.util.shared.filter.FilterFieldDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
                T_FIELD fieldVal = function1.apply(row);
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
                T_FIELD fieldVal = function1.apply(row);
                if (fieldVal != null) {
                    T_SUB_FIELD subFieldVal = function2.apply(fieldVal);
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

    public Function<T_ROW, String> getNullSafeStringValueExtractor() {
        if (LOGGER.isTraceEnabled()) {
            return row -> {
                String val = valueExtractor.apply(row);
                LOGGER.trace("Extracted [{}] from field {} in [{}]", val, fieldDefinition, row);
                return val;
            };
        } else {
            return valueExtractor;
        }
    }

//        public Function<T_ROW, String> getNullSafeStringValueExtractor() {
//            return row -> {
//                final String result;
//                if (row != null) {
//                    T_FIELD fieldVal = valueExtractor.apply(row);
//                    if (fieldVal != null) {
//                        result = stringValueExtractor.apply(fieldVal);
//                    } else {
//                        result = null;
//                    }
//                } else {
//                    result = null;
//                }
//                return result;
//            };
//        }

    @Override
    public String toString() {
        return fieldDefinition.toString();
    }

    @SafeVarargs
    public static <T> Map<String, FilterFieldMapper<T>> mappedByQualifier(
            final FilterFieldMapper<T>... fieldMappers) {
        return mappedByQualifier(Arrays.asList(fieldMappers));
    }

    public static <T> Map<String, FilterFieldMapper<T>> mappedByQualifier(
            final Collection<FilterFieldMapper<T>> fieldMappers) {

        return Optional.ofNullable(fieldMappers)
                .map(fieldMappers2 -> fieldMappers2.stream()
                        .collect(Collectors.toMap(
                                fieldMapper -> fieldMapper.getFieldDefinition().getFilterQualifier(),
                                Function.identity())))
                .orElseGet(Collections::emptyMap);
    }
}
