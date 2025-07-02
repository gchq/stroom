package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class SimpleMapper implements ItemMapper {

    private final int[] columnIndexMapping;

    private SimpleMapper(final int[] columnIndexMapping) {
        this.columnIndexMapping = columnIndexMapping;
    }

    public static ItemMapper create(final List<Column> originalColumns,
                                    final List<Column> newColumns) {
        final int[] columnIndexMapping = createColumnIndexMapping(originalColumns, newColumns);

        // If no column positions are missing and the columns are in the original order then just return a direct
        // mapping.
        final boolean direct = isDirect(columnIndexMapping);
        if (direct) {
            return new DirectMapper(columnIndexMapping);
        }

        return new SimpleMapper(columnIndexMapping);
    }

    private static boolean isDirect(final int[] columnIndexMapping) {
        for (int i = 0; i < columnIndexMapping.length; i++) {
            if (columnIndexMapping[i] != i) {
                return false;
            }
        }
        return true;
    }

    private static int[] createColumnIndexMapping(final List<Column> originalColumns,
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
                arr[i] = Objects.requireNonNullElse(index, -1);
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
                    final Integer index = originalColumnIndex.get(column.getName());
                    arr[i] = Objects.requireNonNullElse(index, -1);
                } else {
                    arr[i] = -1;
                }
            }
            return arr;
        }
    }

    @Override
    public Stream<Item> create(final Item item) {
        // Fully resolve all values.
        final Val[] arr = new Val[columnIndexMapping.length];
        for (int i = 0; i < arr.length; i++) {
            final int index = columnIndexMapping[i];
            final Val val;
            if (index != -1) {
                val = item.getValue(index);
            } else {
                val = ValNull.INSTANCE;
            }
            arr[i] = val;
        }
        return Stream.of(new SimpleItem(item.getKey(), arr));
    }

    private static class DirectMapper implements ItemMapper {

        private final int[] columnIndexMapping;

        public DirectMapper(final int[] columnIndexMapping) {
            this.columnIndexMapping = columnIndexMapping;
        }

        @Override
        public Stream<Item> create(final Item item) {
            // Fully resolve all values.
            final Val[] arr = new Val[columnIndexMapping.length];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = item.getValue(i);
            }
            return Stream.of(new SimpleItem(item.getKey(), arr));
        }
    }
}
