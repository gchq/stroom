package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactory;
import stroom.query.common.v2.format.Formatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RowUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RowUtil.class);

    static int[] createColumnIndexMapping(final List<Column> originalColumns,
                                          final List<Column> newColumns) {
        // If the columns are the same then it is just an identity mapping.
        if (Objects.equals(originalColumns, newColumns)) {
            final int[] arr = new int[newColumns.size()];
            for (int i = 0; i < newColumns.size(); i++) {
                arr[i] = i;
            }
            return arr;
        }

        try {
            // Map original columns to their position.
            final Map<String, Integer> originalColumnIndex = new HashMap<>();
            for (int i = 0; i < originalColumns.size(); i++) {
                final Column column = originalColumns.get(i);
                Objects.requireNonNull(column.getId(), "Null column id");
                final Integer existing = originalColumnIndex.put(column.getId(), i);
                if (existing != null) {
                    throw new RuntimeException("Duplicate original column id: " + column.getId());
                }
            }
            final int[] arr = new int[newColumns.size()];
            for (int i = 0; i < newColumns.size(); i++) {
                final Column column = newColumns.get(i);
                Objects.requireNonNull(column.getId(), "Null column id");
                final Integer index = originalColumnIndex.get(column.getId());
                if (index == null) {
                    arr[i] = -1;
                } else {
                    arr[i] = index;
                }
            }
            return arr;

        } catch (final RuntimeException e) {
            // Fallback to name mapping :(
            // Map original columns to their position.
            final Map<String, Integer> originalColumnIndex = new HashMap<>();
            for (int i = 0; i < originalColumns.size(); i++) {
                final Column column = originalColumns.get(i);
                if (column.getName() != null) {
                    originalColumnIndex.put(column.getName(), i);
                }
            }
            final int[] arr = new int[newColumns.size()];
            for (int i = 0; i < newColumns.size(); i++) {
                final Column column = newColumns.get(i);
                if (column.getName() != null) {
                    Integer index = originalColumnIndex.get(column.getName());
                    if (index == null) {
                        arr[i] = -1;
                    } else {
                        arr[i] = index;
                    }
                } else {
                    arr[i] = -1;
                }
            }
            return arr;
        }
    }

    static Formatter[] createFormatters(final List<Column> newColumns,
                                        final FormatterFactory formatterFactory) {
        final Formatter[] formatters = new Formatter[newColumns.size()];
        for (int i = 0; i < newColumns.size(); i++) {
            final Column column = newColumns.get(i);
            formatters[i] = formatterFactory.create(column);
        }
        return formatters;
    }

    public static ValueFunctionFactories<Val[]> createColumnIdValExtractors(final List<Column> newColumns) {
        // Create the field position map for the new columns.
        final Map<String, ValueFunctionFactory<Val[]>> fieldPositionMap = new HashMap<>();
        for (int i = 0; i < newColumns.size(); i++) {
            final Column column = newColumns.get(i);
            fieldPositionMap.put(column.getId(), new ValArrayFunctionFactory(column, i));
        }
        return fieldPositionMap::get;
    }

    static ValueFunctionFactories<Val[]> createColumnNameValExtractor(final List<Column> newColumns) {
        // Create the field position map for the new columns.
        final Map<String, ValueFunctionFactory<Val[]>> fieldPositionMap = new HashMap<>();
        for (int i = 0; i < newColumns.size(); i++) {
            final Column column = newColumns.get(i);
            fieldPositionMap.put(column.getName(), new ValArrayFunctionFactory(column, i));
        }
        return fieldPositionMap::get;
    }

    static Val[] createValuesArray(final Item item,
                                   final int[] columnIndexMapping) {
        final Val[] values = new Val[columnIndexMapping.length];
        for (int i = 0; i < values.length; i++) {
            final int index = columnIndexMapping[i];
            final Val val;
            if (index != -1) {
                val = item.getValue(index);
            } else {
                val = ValNull.INSTANCE;
            }
            values[i] = val;
        }
        return values;
    }

    static List<String> convertValues(final Val[] values,
                                      final Formatter[] columnFormatters) {
        final List<String> stringValues = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            try {
                final Val val = values[i];
                stringValues.add(columnFormatters[i].format(val));
            } catch (final RuntimeException e) {
                LOGGER.error(LogUtil.message("Error getting column value for column index {}", i), e);
                throw e;
            }
        }
        return stringValues;
    }

    static List<String> convertValuesDirectly(final Item item,
                                              final int[] columnIndexMapping,
                                              final Formatter[] columnFormatters) {
        final List<String> stringValues = new ArrayList<>(columnIndexMapping.length);
        for (int i = 0; i < columnIndexMapping.length; i++) {
            try {
                final int index = columnIndexMapping[i];
                final Val val;
                if (index != -1) {
                    val = item.getValue(index);
                } else {
                    val = ValNull.INSTANCE;
                }
                stringValues.add(columnFormatters[i].format(val));
            } catch (final RuntimeException e) {
                LOGGER.error(LogUtil.message("Error getting column value for column index {}", i), e);
                throw e;
            }
        }
        return stringValues;
    }
}
