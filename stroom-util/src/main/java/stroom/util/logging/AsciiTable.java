package stroom.util.logging;

import stroom.util.logging.AsciiTable.Field.Alignment;

import com.google.common.base.Strings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AsciiTable {

    private AsciiTable() {
    }

    public static <T> Builder<T> from(final Collection<T> data) {
        return new Builder<T>(data);
    }

    public static class Builder<T_ROW> {

        private static final char TABLE_COLUMN_DELIMITER = '|';
        private static final char TABLE_HEADER_DELIMITER = '-';

        private final Collection<T_ROW> sourceData;
        private final List<Field<T_ROW, ?>> fields = new ArrayList<>();
        private int rowLimit = Integer.MAX_VALUE;

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

        private boolean isTruncated() {
            return sourceData.size() > rowLimit;
        }

        private Map<Field<T_ROW, ?>, String> extractValuesFromRow(final T_ROW row) {
            return fields.stream()
                    .collect(Collectors.toMap(
                            Function.identity(),
                            field -> field.extractValue(row)));
        }

        private static String createHorizontalLine(int length, char lineChar) {
            return Strings.repeat(String.valueOf(lineChar), length);
        }

        private String formatCell(final Field<T_ROW, ?> field, final String value, final int maxWidth) {
            if (field.getAlignment().equals(Alignment.RIGHT)) {
                return Strings.padStart(value, maxWidth + 1, ' ') + " ";
            } else if (field.getAlignment().equals(Alignment.LEFT)) {
                return " " + Strings.padEnd(value, maxWidth + 1, ' ');
            } else if (field.getAlignment().equals(Alignment.CENTER)) {
                int valWidth = value.length();
                int totalSpace = maxWidth + 2 - valWidth;
                int space = totalSpace / 2;
                int remainder = totalSpace % 2;

                return Strings.repeat(" ", space) +
                        value +
                        Strings.repeat(" ", space + remainder);
            } else {
                throw new RuntimeException("Unknown alignment");
            }
        }

        public String build() {

            // outer list is rows, inner maps is fieldName => cellValue
            final List<Map<Field<T_ROW, ?>, String>> rawRows = sourceData.stream()
                    .map(this::extractValuesFromRow)
                    .collect(Collectors.toList());

            //get the widths of the field headings
            final Map<Field<T_ROW, ?>, Integer> maxFieldWidths = new HashMap<>();

            // Get lengths of field names
            fields.forEach(field -> maxFieldWidths.put(field, field.getName().length()));

            // Build up map of max field value/name lengths
            rawRows.stream()
                    .flatMap(rowMap -> rowMap.entrySet().stream())
                    .forEach(entry -> maxFieldWidths.merge(
                            entry.getKey(),
                            entry.getValue().length(),
                            Math::max));

//            rawRows.forEach(row -> {
//                for (int i = 0; i < fields.size(); i++) {
//                    final String cellValue = row.get(i);
//                    maxFieldWidths.merge(
//                            fields.get(i).getName(),
//                            cellValue != null ? cellValue.length() : 0,
//                            Math::max);
//                }
//            });


            //now construct the row strings
            List<String> valueStrings = rawRows.stream()
                    .limit(rowLimit)
                    .map(rowMap -> fields.stream()
                            .map(field -> formatCell(field, rowMap.get(field), maxFieldWidths.get(field)))
                            .collect(Collectors.joining(String.valueOf(TABLE_COLUMN_DELIMITER))))
                    .collect(Collectors.toList());

            String headerString = fields.stream()
                    .map(field -> formatCell(field, field.getName(), maxFieldWidths.get(field)))
                    .collect(Collectors.joining(String.valueOf(TABLE_COLUMN_DELIMITER)));

            List<String> headerAndValueStrings = new ArrayList<>();
            headerAndValueStrings.add(headerString);
            headerAndValueStrings.add(createHorizontalLine(headerString.length(), TABLE_HEADER_DELIMITER));
            headerAndValueStrings.addAll(valueStrings);

            if (isTruncated()) {
                headerAndValueStrings.add(String.format("...TRUNCATED TO %s ROWS...", rowLimit));
            }

            return String.join("\n", headerAndValueStrings);
        }
    }

    static class Field<T_ROW, T_FIELD> {

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

        public static <T_ROW, T_FIELD> Field<T_ROW, T_FIELD> of(final String name,
                                                                final Class<T_FIELD> type,
                                                                final Function<T_ROW, T_FIELD> fieldExtractor) {
            return new Builder<>(
                    Objects.requireNonNull(name),
                    Objects.requireNonNull(type),
                    Objects.requireNonNull(fieldExtractor))
                    .build();
        }

        public static <T_ROW, T_FIELD> Builder<T_ROW, T_FIELD> builder(final String name,
                                                                       final Class<T_FIELD> type,
                                                                       final Function<T_ROW, T_FIELD> fieldExtractor) {
            return new Builder<>(
                    Objects.requireNonNull(name),
                    Objects.requireNonNull(type),
                    Objects.requireNonNull(fieldExtractor));
        }

        private String getName() {
            return name;
        }

        private Alignment getAlignment() {
            return alignment;
        }

        private String extractValue(final T_ROW row) {
            T_FIELD obj = fieldExtractor.apply(row);
            return fieldFormatter.apply(obj);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Field<?, ?> field = (Field<?, ?>) o;
            return name.equals(field.name) &&
                    type.equals(field.type) &&
                    fieldExtractor.equals(field.fieldExtractor) &&
                    fieldFormatter.equals(field.fieldFormatter) &&
                    alignment == field.alignment;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type, fieldExtractor, fieldFormatter, alignment);
        }

        @Override
        public String toString() {
            return "Field{" +
                    "name='" + name + '\'' +
                    ", type=" + type +
                    ", alignment=" + alignment +
                    '}';
        }

        public static enum Alignment {
            LEFT,
            RIGHT,
            CENTER
        }

        public static class Builder<T_ROW, T_FIELD> {
            private final String name;
            private final Class<T_FIELD> type;
            private final Function<T_ROW, T_FIELD> fieldExtractor;
            private Function<T_FIELD, String> fieldFormatter = null;
            private Alignment alignment = Alignment.LEFT;

            private Builder(final String name,
                           final Class<T_FIELD> type,
                           final Function<T_ROW, T_FIELD> fieldExtractor) {
                this.name = name;
                this.type = type;
                this.fieldExtractor = fieldExtractor;
            }

            public Builder<T_ROW, T_FIELD> withFormat(final Function<T_FIELD, String> fieldFormatter) {
                this.fieldFormatter = fieldFormatter;
                return this;
            }

            public Builder<T_ROW, T_FIELD> leftAligned() {
                this.alignment = Alignment.LEFT;
                return this;
            }

            public Builder<T_ROW, T_FIELD> rightAligned() {
                this.alignment = Alignment.RIGHT;
                return this;
            }

            public Builder<T_ROW, T_FIELD> centerAligned() {
                this.alignment = Alignment.CENTER;
                return this;
            }

            public Field<T_ROW, T_FIELD> build() {
                return new Field<>(
                        name,
                        type,
                        fieldExtractor,
                        fieldFormatter == null ? Objects::toString : fieldFormatter,
                        alignment);
            }
        }
    }
}
