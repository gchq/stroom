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

package stroom.util.logging;

import stroom.util.logging.AsciiTable.Column.Alignment;
import stroom.util.logging.AsciiTable.Column.ColumnBuilder;

import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Used for turning a {@link Collection} of some class into a
 * markdown style ascii table, e.g.
 * <pre>
 *
 *  Title | First Name | Surname | Date of Birth | Height
 * -------------------------------------------------------
 *  Mr    | Joe        | Bloggs  |  1971-03-23   |    180
 *  Mrs   | Joanna     | Bloggs  |  1972-04-01   |    170
 *
 * </pre>
 * <p>
 * Supports left/right/center alignment.
 */
public class AsciiTable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsciiTable.class);

    private static final String BLOCK_8_8THS_WIDE = "█";
    private static final String BLOCK_7_8THS_WIDE = "▉";
    private static final String BLOCK_6_8THS_WIDE = "▊";
    private static final String BLOCK_5_8THS_WIDE = "▋";
    private static final String BLOCK_4_8THS_WIDE = "▌";
    private static final String BLOCK_3_8THS_WIDE = "▍";
    private static final String BLOCK_2_8THS_WIDE = "▎";
    private static final String BLOCK_1_8THS_WIDE = "▏";

    private static final String[] BLOCKS_ARR = {
            BLOCK_1_8THS_WIDE,
            BLOCK_2_8THS_WIDE,
            BLOCK_3_8THS_WIDE,
            BLOCK_4_8THS_WIDE,
            BLOCK_5_8THS_WIDE,
            BLOCK_6_8THS_WIDE,
            BLOCK_7_8THS_WIDE,
            BLOCK_8_8THS_WIDE};

    private static final Set<String> IGNORED_RECORD_METHOD_NAMES = Set.of(
            "equals",
            "toString",
            "hashCode");

    private AsciiTable() {
    }

    /**
     * @return A builder for defining the structure of an ascii table
     * with customized column extraction, formatting and alignment.
     */
    public static <T> TableBuilder<T> builder(final Collection<T> data) {
        return new TableBuilder<T>(data);
    }

    /**
     * Attempts to determine the structure of the table from the public getters
     * of the collection items. The column names are derived from the method name,
     * e.g. getFirstNameLength() becomes "First Name Length".
     * Columns MAY be in declared order, but order cannot be guaranteed.
     * Sub-classes of {@link Number} are right aligned.
     *
     * @return A {@link String} containing the markdown style table.
     */
    public static <T> String fromCollection(final Collection<T> data) {
        return fromCollection(data, false);
    }

    /**
     * Builds a two column table from a map
     * Sub-classes of {@link Number} are right aligned.
     *
     * @return A {@link String} containing the markdown style table.
     */
    public static <K, V> String fromMap(final Map<K, V> map) {
        Objects.requireNonNull(map);
        final Set<Entry<K, V>> data = map.entrySet();
        return fromEntries(map.entrySet());
    }

    /**
     * Builds a two column table from a collection of {@link Entry}
     * Sub-classes of {@link Number} are right aligned.
     *
     * @return A {@link String} containing the markdown style table.
     */
    public static <K, V> String fromEntries(final Collection<Entry<K, V>> entries) {
        Objects.requireNonNull(entries);

        return builder(entries)
                .withColumn(Column.builder("Key", (Entry<K, V> e) -> e.getKey())
                        .autoAligned(entries)
                        .build())
                .withColumn(Column.builder("Value", (Entry<K, V> e) -> e.getValue())
                        .autoAligned(entries)
                        .build())
                .build();
    }

    /**
     * Attempts to determine the structure of the table from the public getters
     * of the collection items. The column names are derived from the method name,
     * e.g. getFirstNameLength() becomes "First Name Length".
     * Sub-classes of {@link Number} are right aligned.
     *
     * @param sortColumns If true columns will be in order of column name, else they will
     *                    be in no particular order.
     * @return A {@link String} containing the markdown style table.
     */
    public static <T> String fromCollection(final Collection<T> data, final boolean sortColumns) {
        if (data == null || data.isEmpty()) {
            return "NO DATA";
        }

        final T item = data.iterator().next();
        final TableBuilder<T> tableBuilder = builder(data);

        if (item instanceof String
                || Number.class.isAssignableFrom(item.getClass())
                || Temporal.class.isAssignableFrom(item.getClass())) {
            // Simple single column table so don't try and get getters.
            tableBuilder.withColumn(Column.of(item.getClass().getSimpleName(), Object::toString));
        } else {
            Stream<Method> methodNameStream = Arrays.stream(item.getClass().getDeclaredMethods());

            if (item.getClass().isRecord()) {
                methodNameStream = methodNameStream.filter(method -> {
                    final String methodName = method.getName();
                    return !IGNORED_RECORD_METHOD_NAMES.contains(methodName);
                });
            } else {
                methodNameStream = methodNameStream.filter(method -> {
                    final String methodName = method.getName();
                    return methodName.startsWith("get") || methodName.startsWith("is");
                });
            }

            if (sortColumns) {
                methodNameStream = methodNameStream.sorted(Comparator.comparing(Method::getName));
            }
            methodNameStream.forEach(method -> {
                final Column<T, ?> column = convertToColumn(method);
                tableBuilder.withColumn(column);
            });
        }

        return tableBuilder.build();
    }

    private static <T> Column<T, ?> convertToColumn(final Method method) {
        final String columnName = convertToColumnName(method.getName());

        final ColumnBuilder<T, ?> columnBuilder = Column.builder(columnName, row -> {
            try {
                return method.invoke(row);
            } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        });
        if (Number.class.isAssignableFrom(method.getReturnType())) {
            columnBuilder.rightAligned();
        }
        return columnBuilder.build();
    }

    private static String convertToColumnName(final String methodName) {
        String result = methodName.replaceAll("^(get|is)", "");
        result = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, result);
        return result.replaceAll("(?<!^)([A-Z])", " $1");
    }

    public static String asciiBar(final long value,
                                  final long minValueInc,
                                  final long maxValueInc,
                                  final int maxChars) {

        final long range = maxValueInc - minValueInc;
        final int barRange = maxChars * 8;
        final double scaleFactor = barRange / (double) range;

        final long scaledValue = (long) ((value - minValueInc) * scaleFactor);

        final String bar;
        if (scaledValue > barRange) {
            bar = Strings.repeat(BLOCK_8_8THS_WIDE, maxChars);
        } else {
            final int fullBlockCount = (int) (scaledValue / 8);
            final int remainder = (int) (scaledValue % 8);

            LOGGER.trace("value {}, scaleFactor {}, scaledValue {}, fullBlockCount {}, remainder {}",
                    value, scaleFactor, scaledValue, fullBlockCount, remainder);

            if (remainder == 0) {
                bar = Strings.repeat(BLOCK_8_8THS_WIDE, fullBlockCount);
            } else {
                bar = Strings.repeat(BLOCK_8_8THS_WIDE, fullBlockCount) + BLOCKS_ARR[remainder - 1];
            }
        }
        return bar;
    }


    // -----------------------------------------------------------------------------------


    public static class TableBuilder<T_ROW> {

        private static final char TABLE_COLUMN_DELIMITER = '|';
        private static final char TABLE_HEADER_DELIMITER = '-';
        private static final char PAD_CHAR = ' ';
        private static final int COLUMN_PADDING = 1;
        private static final String PAD_STRING = String.valueOf(PAD_CHAR);

        private final Collection<T_ROW> sourceData;
        private final List<Column<T_ROW, ?>> columns = new ArrayList<>();
        private int rowLimit = Integer.MAX_VALUE;
        private boolean useHeader = true;

        private TableBuilder(final Collection<T_ROW> sourceData) {
            this.sourceData = sourceData;
        }

        /**
         * Add column in order from left to right
         */
        public TableBuilder<T_ROW> withColumn(final Column<T_ROW, ?> column) {
            final boolean isNameAlreadyUsed = columns.stream()
                    .map(Column::getName)
                    .anyMatch(columnName -> columnName.equals(column.getName()));
            if (isNameAlreadyUsed) {
                throw new RuntimeException("Column name " + column.getName() +
                        " has already been used.");
            }
            columns.add(column);

            return this;
        }

        /**
         * Only output rowLimit rows of the source data
         */
        public TableBuilder<T_ROW> withRowLimit(final int rowLimit) {
            this.rowLimit = rowLimit;
            return this;
        }

        /**
         * Don't include the header rows
         */
        public TableBuilder<T_ROW> withoutHeader() {
            this.useHeader = false;
            return this;
        }

        private boolean isTruncated() {
            return sourceData.size() > rowLimit;
        }

        private Map<Column<T_ROW, ?>, String> extractValuesFromRow(final T_ROW row) {
            return columns.stream()
                    .collect(Collectors.toMap(
                            Function.identity(),
                            column -> column.extractValue(row)));
        }

        private static String createHorizontalLine(final int length, final char lineChar) {
            return Strings.repeat(String.valueOf(lineChar), length);
        }

        private String formatCell(final Column<T_ROW, ?> column,
                                  final String value,
                                  final int maxWidth) {
            if (column.getAlignment().equals(Alignment.RIGHT)) {
                return Strings.padStart(
                        value,
                        maxWidth + COLUMN_PADDING,
                        PAD_CHAR) + PAD_CHAR;
            } else if (column.getAlignment().equals(Alignment.LEFT)) {
                return PAD_CHAR + Strings.padEnd(
                        value,
                        maxWidth + COLUMN_PADDING,
                        PAD_CHAR);
            } else if (column.getAlignment().equals(Alignment.CENTER)) {
                final int valWidth = value.length();
                final int totalSpace = maxWidth + (COLUMN_PADDING * 2) - valWidth;
                final int space = totalSpace / 2;
                final int remainder = totalSpace % 2;

                return Strings.repeat(PAD_STRING, space) +
                        value +
                        Strings.repeat(PAD_STRING, space + remainder);
            } else {
                throw new RuntimeException("Unknown alignment");
            }
        }

        public String build() {

            // outer list is rows, inner maps is columnName => cellValue
            final List<Map<Column<T_ROW, ?>, String>> rawRows = sourceData.stream()
                    .map(this::extractValuesFromRow)
                    .collect(Collectors.toList());

            //get the widths of the column headings
            final Map<Column<T_ROW, ?>, Integer> maxColumnWidths = new HashMap<>();

            // Get lengths of column names
            if (useHeader) {
                columns.forEach(column -> maxColumnWidths.put(column, column.getName().length()));
            }

            // Build up map of max column value/name lengths
            rawRows.stream()
                    .flatMap(rowMap -> rowMap.entrySet().stream())
                    .forEach(entry -> maxColumnWidths.merge(
                            entry.getKey(),
                            entry.getValue().length(),
                            Math::max));

            final List<String> headerAndValueStrings = new ArrayList<>();

            if (useHeader) {
                headerAndValueStrings.add(createHeaderRowString(maxColumnWidths));
                headerAndValueStrings.add(createHeaderLineString(maxColumnWidths));
            }

            final List<String> valueRowStrings = createValueRowStrings(rawRows, maxColumnWidths);
            headerAndValueStrings.addAll(valueRowStrings);

            if (isTruncated()) {
                headerAndValueStrings.add(String.format("...TRUNCATED TO %s ROWS...", rowLimit));
            }

            return String.join("\n", headerAndValueStrings);
        }

        private List<String> createValueRowStrings(
                final List<Map<Column<T_ROW, ?>, String>> rawRows,
                final Map<Column<T_ROW, ?>, Integer> maxColumnWidths) {

            return rawRows.stream()
                    .limit(rowLimit)
                    .map(rowMap -> columns.stream()
                            .map(column ->
                                    formatCell(
                                            column,
                                            rowMap.get(column),
                                            maxColumnWidths.get(column)))
                            .collect(Collectors.joining(
                                    String.valueOf(TABLE_COLUMN_DELIMITER))))
                    .map(rowStr -> String.join("", "|", rowStr, "|"))
                    .collect(Collectors.toList());
        }

        private String createHeaderLineString(final Map<Column<T_ROW, ?>, Integer> maxColumnWidths) {
            // TODO could add markdown alignment indicators e.g. |------:|
            return "|" + columns.stream()
                    .map(column ->
                            Strings.repeat(
                                    String.valueOf(TABLE_HEADER_DELIMITER),
                                    maxColumnWidths.get(column) + (COLUMN_PADDING * 2)))
                    .collect(Collectors.joining(String.valueOf(TABLE_COLUMN_DELIMITER)))
                    + "|";
        }

        private String createHeaderRowString(final Map<Column<T_ROW, ?>, Integer> maxColumnWidths) {
            return "|" + columns.stream()
                    .map(column ->
                            formatCell(column, column.getName(), maxColumnWidths.get(column)))
                    .collect(Collectors.joining(String.valueOf(TABLE_COLUMN_DELIMITER)))
                    + "|";
        }
    }


    // -----------------------------------------------------------------------------------


    public static class Column<T_ROW, T_COL> {

        private final String name;
        private final Function<T_ROW, T_COL> columnExtractor;
        private final Function<T_COL, String> columnFormatter;
        private final Supplier<String> nullValueSupplier;
        private final Alignment alignment;

        private Column(final String name,
                       final Function<T_ROW, T_COL> columnExtractor,
                       final Function<T_COL, String> columnFormatter,
                       final Supplier<String> nullValueSupplier,
                       final Alignment alignment) {
            this.name = name;
            this.columnExtractor = columnExtractor;
            this.columnFormatter = columnFormatter;
            this.nullValueSupplier = nullValueSupplier;
            this.alignment = alignment;
        }

        /**
         * A simple left-aligned column with no additional formatting
         */
        public static <T_ROW, T_COL> Column<T_ROW, T_COL> of(
                final String name,
                final Function<T_ROW, T_COL> columnExtractor) {

            return new ColumnBuilder<>(
                    Objects.requireNonNull(name),
                    Objects.requireNonNull(columnExtractor))
                    .build();
        }

        /**
         * A right-aligned column for decimal numeric values, formatted with thousands separator and
         * the specified fixed number of decimal places.
         */
        public static <T_ROW, T_COL extends Number> Column<T_ROW, T_COL> decimal(
                final String name,
                final Function<T_ROW, T_COL> columnExtractor,
                final int decimalPlaces) {

            final NumberFormat numberFormat = NumberFormat.getNumberInstance();
            numberFormat.setGroupingUsed(true);
            numberFormat.setMinimumFractionDigits(decimalPlaces);
            numberFormat.setMaximumFractionDigits(decimalPlaces);

            return new ColumnBuilder<>(
                    Objects.requireNonNull(name),
                    Objects.requireNonNull(columnExtractor))
                    .rightAligned()
                    .withFormat(numberFormat::format)
                    .build();
        }

        /**
         * A right-aligned column for {@link java.time.Duration} values represented as milliseconds
         * with thousand separators.
         */
        public static <T_ROW, T_COL extends Duration> Column<T_ROW, Long> durationMillis(
                final String name,
                final Function<T_ROW, T_COL> columnExtractor) {

            final NumberFormat numberFormat = NumberFormat.getNumberInstance();
            numberFormat.setGroupingUsed(true);
            numberFormat.setMinimumFractionDigits(0);
            numberFormat.setMaximumFractionDigits(0);
            Objects.requireNonNull(columnExtractor);
            final Function<T_ROW, Long> newExtractor = row -> columnExtractor.apply(row)
                    .toMillis();

            return new ColumnBuilder<>(
                    Objects.requireNonNull(name),
                    newExtractor)
                    .rightAligned()
                    .withFormat(numberFormat::format)
                    .build();
        }

        /**
         * A right-aligned column for {@link java.time.Duration} values represented as nanos
         * with thousand separators.
         */
        public static <T_ROW, T_COL extends Duration> Column<T_ROW, Long> durationNanos(
                final String name,
                final Function<T_ROW, T_COL> columnExtractor) {

            final NumberFormat numberFormat = NumberFormat.getNumberInstance();
            numberFormat.setGroupingUsed(true);
            numberFormat.setMinimumFractionDigits(0);
            numberFormat.setMaximumFractionDigits(0);
            Objects.requireNonNull(columnExtractor);
            final Function<T_ROW, Long> newExtractor = row -> columnExtractor.apply(row)
                    .toNanos();

            return new ColumnBuilder<>(
                    Objects.requireNonNull(name),
                    newExtractor)
                    .rightAligned()
                    .withFormat(numberFormat::format)
                    .build();
        }

        /**
         * A right-aligned column for integer numeric values, formatted with thousands separator
         */
        public static <T_ROW, T_COL extends Number> Column<T_ROW, T_COL> integer(
                final String name,
                final Function<T_ROW, T_COL> columnExtractor) {

            final NumberFormat numberFormat = NumberFormat.getNumberInstance();
            numberFormat.setGroupingUsed(true);
            numberFormat.setMinimumFractionDigits(0);
            numberFormat.setMaximumFractionDigits(0);

            return new ColumnBuilder<>(
                    Objects.requireNonNull(name),
                    Objects.requireNonNull(columnExtractor))
                    .rightAligned()
                    .withFormat(numberFormat::format)
                    .build();
        }

        /**
         * A column builder for when you need full control over the column style
         */
        public static <T_ROW, T_COL> ColumnBuilder<T_ROW, T_COL> builder(
                final String name,
                final Function<T_ROW, T_COL> columnExtractor) {

            return new ColumnBuilder<>(
                    Objects.requireNonNull(name),
                    Objects.requireNonNull(columnExtractor));
        }

        private String getName() {
            return name;
        }

        private Alignment getAlignment() {
            return alignment;
        }

        private String extractValue(final T_ROW row) {
            final T_COL obj = columnExtractor.apply(row);
            if (obj == null) {
                return nullValueSupplier.get();
            } else {
                return columnFormatter.apply(obj);
            }
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Column<?, ?> column = (Column<?, ?>) o;
            return name.equals(column.name) &&
                    columnExtractor.equals(column.columnExtractor) &&
                    columnFormatter.equals(column.columnFormatter) &&
                    alignment == column.alignment;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, columnExtractor, columnFormatter, alignment);
        }

        @Override
        public String toString() {
            return "Column{" +
                    "name='" + name + '\'' +
                    ", alignment=" + alignment +
                    '}';
        }

        public enum Alignment {
            LEFT,
            RIGHT,
            CENTER
        }


        // -----------------------------------------------------------------------------------


        public static class ColumnBuilder<T_ROW, T_COL> {

            private final String name;
            private final Function<T_ROW, T_COL> columnExtractor;
            private Function<T_COL, String> columnFormatter = null;
            private Supplier<String> nullValueSupplier = null;
            private Alignment alignment = Alignment.LEFT;

            private ColumnBuilder(final String name,
                                  final Function<T_ROW, T_COL> columnExtractor) {
                this.name = name;
                this.columnExtractor = columnExtractor;
            }

            public ColumnBuilder<T_ROW, T_COL> withFormat(
                    final Function<T_COL, String> columnFormatter) {

                this.columnFormatter = columnFormatter;
                return this;
            }

            public ColumnBuilder<T_ROW, T_COL> withNullValueSupplier(
                    final Supplier<String> nullValueSupplier) {

                this.nullValueSupplier = nullValueSupplier;
                return this;
            }

            public ColumnBuilder<T_ROW, T_COL> withAlignment(final Alignment alignment) {
                this.alignment = alignment;
                return this;
            }

            public ColumnBuilder<T_ROW, T_COL> leftAligned() {
                this.alignment = Alignment.LEFT;
                return this;
            }

            public ColumnBuilder<T_ROW, T_COL> rightAligned() {
                this.alignment = Alignment.RIGHT;
                return this;
            }

            public ColumnBuilder<T_ROW, T_COL> centerAligned() {
                this.alignment = Alignment.CENTER;
                return this;
            }

            public ColumnBuilder<T_ROW, T_COL> autoAligned(final Collection<T_ROW> data) {

                final boolean isNumeric = data.stream()
                        .allMatch(k -> k != null
                                && Number.class.isAssignableFrom(k.getClass()));

                this.alignment = isNumeric
                        ? Alignment.RIGHT
                        : Alignment.LEFT;
                return this;
            }


            public Column<T_ROW, T_COL> build() {
                // IJ was having issues inferring arguments, hence not inlined
                final Column<T_ROW, T_COL> column = new Column<T_ROW, T_COL>(
                        name,
                        columnExtractor,
                        columnFormatter == null
                                ? Objects::toString
                                : columnFormatter,
                        nullValueSupplier == null
                                ? () -> ""
                                : nullValueSupplier,
                        alignment);
                return column;
            }
        }
    }
}
