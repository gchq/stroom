package stroom.util.logging;

import stroom.util.logging.AsciiTable.Field.Alignment;

import com.google.common.base.Strings;
import io.vavr.Tuple;
import io.vavr.Tuple2;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AsciiTable {




    public static <T> Builder<T> from(final Collection<T> data) {
        return new Builder<T>(data);
    }


    public static List<String> convertToFixedWidth(final FlatResult flatResult,
                                                   @Nullable Map<String, Class<?>> fieldTypes,
                                                   @Nullable Integer maxRows) {

        if (flatResult == null) {
            return Collections.emptyList();
        }

        Map<String, Integer> fieldIndices = getFieldIndices(flatResult.getStructure());

        long rowLimit = maxRows != null ? maxRows : Long.MAX_VALUE;
        boolean wasTruncated = flatResult.getValues() != null && rowLimit < flatResult.getValues().size();

        List<Map<String, String>> rowData = flatResult.getValues().stream()
                .limit(rowLimit)
                .map(values -> convertValuesToStrings(values, fieldIndices))
                .collect(Collectors.toList());

        //assume all rows have same fields so just use first one
        if (rowData == null || rowData.isEmpty()) {
            return Collections.emptyList();
        } else {
            //Get the field names in index order
            List<String> fieldNames = fieldIndices.entrySet().stream()
                    .sorted(Comparator.comparingInt(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            //get the widths of the field headings
            Map<String, Integer> maxFieldWidths = new HashMap<>();

            List<Map<String, String>> formattedRowData;

            //if we have been given typed for any fields then do then convert those values
            if (fieldTypes == null || fieldTypes.isEmpty()) {
                formattedRowData = rowData;
            } else {
                formattedRowData = rowData.stream()
                        .map(rowMap -> {
                            Map<String, String> newRowMap = new HashMap<>();
                            fieldNames.forEach(fieldName -> {
                                Class<?> type = fieldTypes.get(fieldName);
                                if (type != null) {
                                    String newValue = conversionMap.get(type).apply(rowMap.get(fieldName)).toString();
                                    newRowMap.put(fieldName, newValue);
                                } else {
                                    //no explicit type so take the value as is
                                    newRowMap.put(fieldName, rowMap.get(fieldName));
                                }
                            });
                            return newRowMap;
                        })
                        .collect(Collectors.toList());
            }

            fieldNames.forEach(key -> maxFieldWidths.put(key, key.length()));

            //now find the max width for each value (and its field heading)
            formattedRowData.stream()
                    .flatMap(rowMap -> rowMap.entrySet().stream())
                    .forEach(entry ->
                            maxFieldWidths.merge(entry.getKey(), entry.getValue().length(), Math::max));

            //now construct the row strings
            List<String> valueStrings = formattedRowData.stream()
                    .map(rowMap -> fieldNames.stream()
                            .map(fieldName -> formatCell(rowMap.get(fieldName), maxFieldWidths.get(fieldName)))
                            .collect(Collectors.joining(String.valueOf(TABLE_COLUMN_DELIMITER))))
                    .collect(Collectors.toList());

            String headerString = fieldNames.stream()
                    .map(fieldName -> formatCell(fieldName, maxFieldWidths.get(fieldName)))
                    .collect(Collectors.joining(String.valueOf(TABLE_COLUMN_DELIMITER)));

            List<String> headerAndValueStrings = new ArrayList<>();
            headerAndValueStrings.add(headerString);
            headerAndValueStrings.add(createHorizontalLine(headerString.length(), TABLE_HEADER_DELIMITER));
            headerAndValueStrings.addAll(valueStrings);

            if (wasTruncated) {
                headerAndValueStrings.add(String.format("...TRUNCATED TO %s ROWS...", rowLimit));
            }

            return headerAndValueStrings;
        }
    }

    private static String formatCell(final String value, final int maxWidth) {
        return Strings.padStart(value, maxWidth + 1, ' ') + " ";
    }


    private static String createHorizontalLine(int length, char lineChar) {
        return Strings.repeat(String.valueOf(lineChar), length);
    }

    public static String convertValueToStr(final Object obj) {
        if (obj == null) {
            return "";
        } else if (obj instanceof String) {
            return (String) obj;
        } else {
            return obj.toString();
        }
    }

    public static Map<String, String> convertValuesToStrings(final List<Object> values,
                                                             final Map<String, Integer> fieldIndices) {

        return fieldIndices.entrySet().stream()
                .map(entry -> Tuple.of(entry.getKey(), values.get(entry.getValue())))
                .map(tuple2 -> tuple2.map2(AsciiTable::convertValueToStr))
                .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2));
    }

    public static class Builder<T_ROW> {

//        private static final Map<Class<?>, Function<String, Object>> CONVERSION_MAP = new HashMap<>();
//
//        static {
//            CONVERSION_MAP.put(String.class, str -> str);
//            CONVERSION_MAP.put(Long.class, Long::valueOf);
//            CONVERSION_MAP.put(Double.class, Double::valueOf);
//            CONVERSION_MAP.put(Instant.class, str -> Instant.ofEpochMilli(Long.parseLong(str)));
//            CONVERSION_MAP.put(ZonedDateTime.class, str ->
//                    ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(str)), ZoneOffset.UTC));
//        }

        private static final char TABLE_COLUMN_DELIMITER = '|';
        private static final char TABLE_HEADER_DELIMITER = '-';

        private final Collection<T_ROW> sourceData;
        private final List<Field<T_ROW, ?>> fields = new ArrayList<>();
        private int rowLimit = 0;

        private Builder(final Collection<T_ROW> sourceData) {
            this.sourceData = sourceData;
        }

        public Builder<T_ROW> withField(final Field<T_ROW, ?> field) {
            final boolean isNameAlreadyUsed = fields.stream()
                    .map(Field::getName)
                    .anyMatch(fieldName -> fieldName.equals(field.getName()));
            if (isNameAlreadyUsed) {
                throw new RuntimeException("Field name " + field.getName() + " has already been used.");
            }
            fields.add(field);

            return this;
        }

        public Builder<T_ROW> withRowLimt(final int rowLimit) {
            this.rowLimit = rowLimit;
            return this;
        }



//        public Builder<T_ROW> withField(final String name,
//                                    final Function<T, String> fieldMapper,
//                                    final Alignment alignment) {
//            // TODO check for unique name in fields list
//            final boolean isNameAlreadyUsed = fields.stream()
//                    .map(Field::getName)
//                    .anyMatch(fieldName -> fieldName.equals(name));
//            if (isNameAlreadyUsed) {
//                throw new RuntimeException("Field name " + name + " has already been used.");
//            }
//            fields.add(new Field<>(name, fieldMapper, alignment));
//
//            return this;
//        }
//
//        public Builder<T> withField(final String name, final Function<T, String> fieldMapper) {
//            // TODO check for unique name in fields list
//            final boolean isNameAlreadyUsed = fields.stream()
//                    .map(Field::getName)
//                    .anyMatch(fieldName -> fieldName.equals(name));
//            if (isNameAlreadyUsed) {
//                throw new RuntimeException("Field name " + name + " has already been used.");
//            }
//            fields.add(new Field<>(name, fieldMapper, Alignment.LEFT));
//
//            return this;
//        }

        private List<String> extractValuesFromRow(final T_ROW row) {
            return fields.stream()
                    .map(field -> field.extractValue(row))
                    .collect(Collectors.toList());
        }

        private String formatCell(final Field<T_ROW, ?> field, final String value, final int maxWidth) {
            if (field.getAlignment().equals(Alignment.RIGHT)) {
                return Strings.padStart(value, maxWidth + 1, ' ') + " ";
            } else if (field.getAlignment().equals(Alignment.LEFT)) {
                return Strings.padEnd(value, maxWidth + 1, ' ') + " ";
            } else if (field.getAlignment().equals(Alignment.CENTER)) {
                throw new RuntimeException("TODO");
            } else {
                throw new RuntimeException("Unknown alignment");
            }
        }

        public String build() {

            // outer list is rows, inner is cols
            List<List<String>> rawCells = sourceData.stream()
                    .map(this::extractValuesFromRow)
                    .collect(Collectors.toList());

            //get the widths of the field headings
            final Map<String, Integer> maxFieldWidths = new HashMap<>();

            // Get lengths of field names
            fields.stream()
                    .map(Field::getName)
                    .forEach(key -> maxFieldWidths.put(key, key.length()));

            // Build up map of max field value/name lengths
            rawCells.forEach(row -> {
                for (int i = 0; i < fields.size(); i++) {
                    final String cellValue = row.get(i);
                    maxFieldWidths.merge(
                            fields.get(i).getName(),
                            cellValue != null ? cellValue.length() : 0,
                            Math::max);
                }
            });


            //now construct the row strings
            List<String> valueStrings = rawCells.stream()
                    .map(rowMap -> fields.stream()
                            .map(Field::getName)
                            .map(fieldName -> formatCell(rowMap.get(fieldName), maxFieldWidths.get(fieldName)))
                            .collect(Collectors.joining(String.valueOf(TABLE_COLUMN_DELIMITER))))
                    .collect(Collectors.toList());

            String headerString = fieldNames.stream()
                    .map(fieldName -> formatCell(fieldName, maxFieldWidths.get(fieldName)))
                    .collect(Collectors.joining(String.valueOf(TABLE_COLUMN_DELIMITER)));

            List<String> headerAndValueStrings = new ArrayList<>();
            headerAndValueStrings.add(headerString);
            headerAndValueStrings.add(createHorizontalLine(headerString.length(), TABLE_HEADER_DELIMITER));
            headerAndValueStrings.addAll(valueStrings);

            if (wasTruncated) {
                headerAndValueStrings.add(String.format("...TRUNCATED TO %s ROWS...", rowLimit));
            }

        }
    }

    private static class Field<T_ROW, T_FIELD> {

        private final String name;
        private final Class<T_FIELD> type;
        private final Function<T_ROW, T_FIELD> fieldExtractor;
        private final Function<T_FIELD, String> fieldFormatter;
        private final Alignment alignment;

        private Field(final String name,
                     final Class<T_FIELD> type,
                     final Function<T_ROW, T_FIELD> fieldExtractor,
                     final Function<T_FIELD, String> fieldFormatter,
                     final Alignment alignment) {
            this.name = name;
            this.type = type;
            this.fieldExtractor = fieldExtractor;
            this.fieldFormatter = fieldFormatter;
            this.alignment = alignment;
        }

        public static <T_ROW, T_FIELD> Builder of(final String name,
                                                  final Class<T_FIELD> type,
                                                  final Function<T_ROW, T_FIELD> fieldExtractor) {
            return new Builder(name, type, fieldExtractor);
        }

        public String getName() {
            return name;
        }

        public Alignment getAlignment() {
            return alignment;
        }

        public String extractValue(final T_ROW row) {
            T_FIELD obj = fieldExtractor.apply(row);
            return fieldFormatter.apply(obj);
        }

        public static enum Alignment {
            LEFT,
            RIGHT,
            CENTER
        }

        private static class Builder<T_ROW, T_FIELD> {
            private final String name;
            private final Class<T_FIELD> type;
            private final Function<T_ROW, T_FIELD> fieldExtractor;
            private Function<T_FIELD, String> fieldFormatter = null;
            private Alignment alignment = Alignment.LEFT;

            public Builder(final String name,
                           final Class<T_FIELD> type,
                           final Function<T_ROW, T_FIELD> fieldExtractor) {
                this.name = name;
                this.type = type;
                this.fieldExtractor = fieldExtractor;
            }

            public Builder withFormat(final Function<T_FIELD, String> fieldFormatter) {
                this.fieldFormatter = fieldFormatter;
                return this;
            }

            public Builder leftAligned() {
                this.alignment = Alignment.LEFT;
                return this;
            }

            public Builder rightAligned() {
                this.alignment = Alignment.RIGHT;
                return this;
            }

            public Builder centerAligned() {
                this.alignment = Alignment.CENTER;
                return this;
            }

            public Field<T_ROW, T_FIELD> build() {
                return new Field<>(
                        name,
                        fieldExtractor,
                        fieldFormatter == null ? Objects::toString : fieldFormatter,
                        alignment);
            }
        }

    }

}
