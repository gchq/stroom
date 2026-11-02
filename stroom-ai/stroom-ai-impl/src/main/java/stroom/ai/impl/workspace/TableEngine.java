/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.workspace;

import org.apache.commons.csv.CSVException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/// Selects, filters, groups, aggregates and sorts the rows of a table in the workspace (CSV, TSV or a markdown
/// table, such as an attached table), in code, so figures are exact. Nothing in a query is ever run as code: a
/// query is a typed description of the steps, and the only patterns are [SafePattern]s.
public final class TableEngine {

    static final int MAX_GROUPS = 100_000;
    static final int MAX_ROWS_HELD = 1_000_000;
    static final int MAX_DISTINCT_VALUES = 100_000;

    private TableEngine() {
        // Static only.
    }

    /// Runs a query on a table.
    ///
    /// @param file   The table.
    /// @param format Its format.
    /// @param query  The query.
    /// @return The result.
    /// @throws WorkspaceException if the query does not fit the table, or goes over a limit.
    public static Result run(final Path file, final Format format, final Query query) {
        try {
            return runOn(file, format, query);
        } catch (final UncheckedIOException e) {
            // A file that isn't the table it is named as (seen: a CSV line with a stray quote, from JSON read as
            // CSV): said to the model, which can read the line, rather than a tool failure.
            if (e.getCause() instanceof final CSVException csv) {
                throw new WorkspaceException("The file isn't " + format + " the table tool can read: "
                                             + csv.getMessage() + ". A field holding the delimiter, a quote or a "
                                             + "line break must be in double quotes, its own quotes doubled; "
                                             + "read_file shows the line.");
            }
            throw e;
        }
    }

    private static Result runOn(final Path file, final Format format, final Query query) {
        try (final RowSource source = withEvals(open(file, format), query.eval())) {
            final List<String> header = source.header();
            final List<CompiledCondition> conditions = new ArrayList<>();
            for (final Condition condition : query.where()) {
                conditions.add(compile(condition, header));
            }
            final boolean grouping = !query.groupBy().isEmpty() || !query.aggregates().isEmpty() || query.distinct();
            return grouping
                    ? group(source, header, conditions, query)
                    : select(source, header, conditions, query);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Result select(final RowSource source,
                                 final List<String> header,
                                 final List<CompiledCondition> conditions,
                                 final Query query) {
        final List<Integer> indexes = query.select().isEmpty()
                ? allIndexes(header)
                : indexes(query.select(), header);
        final List<String> columns = indexes.stream().map(header::get).toList();
        final List<List<String>> rows = new ArrayList<>();
        long read = 0;
        long matched = 0;
        // Without sorting, only the rows to be returned need to be held.
        final int keep = query.orderBy().isEmpty() && query.limit() != null
                ? query.limit()
                : MAX_ROWS_HELD;
        for (final Iterator<List<String>> it = source.rows(); it.hasNext(); ) {
            final List<String> row = it.next();
            read++;
            checkTime(read);
            if (matches(row, conditions)) {
                matched++;
                if (rows.size() < keep) {
                    rows.add(project(row, indexes));
                } else if (keep == MAX_ROWS_HELD) {
                    throw new WorkspaceException("More than " + MAX_ROWS_HELD + " rows match. Filter the rows "
                                                 + "first, or give a limit.");
                }
            }
        }
        return new Result(columns, sortAndLimit(rows, columns, query), read, matched);
    }

    private static Result group(final RowSource source,
                                final List<String> header,
                                final List<CompiledCondition> conditions,
                                final Query query) {
        final List<String> keyNames = !query.groupBy().isEmpty()
                ? query.groupBy()
                : query.distinct()
                        ? (query.select().isEmpty()
                                ? header
                                : query.select())
                        : List.of();
        final List<Integer> keyIndexes = indexes(keyNames, header);
        final List<Aggregate> aggregates = query.aggregates().isEmpty()
                ? List.of(new Aggregate("count", null, null))
                : query.aggregates();
        // Each aggregate, ready to make what works it out for a group.
        final List<Supplier<GroupValue>> makers = new ArrayList<>();
        for (final Aggregate aggregate : aggregates) {
            if (aggregate.expression() != null) {
                final TableExpression expression = TableExpression.compile(aggregate.expression(), header);
                makers.add(() -> new ExpressionValue(expression.group()));
            } else {
                checkFunction(aggregate);
                final int index = aggregate.column() == null
                        ? -1
                        : index(aggregate.column(), header);
                makers.add(() -> new Accumulator(aggregate.function(), index));
            }
        }

        final Map<List<String>, GroupValue[]> groups = new LinkedHashMap<>();
        long read = 0;
        long matched = 0;
        for (final Iterator<List<String>> it = source.rows(); it.hasNext(); ) {
            final List<String> row = it.next();
            read++;
            checkTime(read);
            if (!matches(row, conditions)) {
                continue;
            }
            matched++;
            final List<String> key = new ArrayList<>(keyIndexes.size());
            for (final int index : keyIndexes) {
                final String value = cell(row, index);
                key.add(query.mask()
                        ? ValueMasker.mask(value)
                        : value);
            }
            GroupValue[] accumulators = groups.get(key);
            if (accumulators == null) {
                if (groups.size() >= MAX_GROUPS) {
                    throw new WorkspaceException("There are more than " + MAX_GROUPS + " groups. Group by fewer "
                                                 + "columns, filter the rows first, or use mask to reduce free "
                                                 + "text to patterns.");
                }
                accumulators = new GroupValue[aggregates.size()];
                for (int i = 0; i < accumulators.length; i++) {
                    accumulators[i] = makers.get(i).get();
                }
                groups.put(key, accumulators);
            }
            for (final GroupValue accumulator : accumulators) {
                accumulator.add(row);
            }
        }

        final List<String> columns = new ArrayList<>();
        keyIndexes.forEach(index -> columns.add(header.get(index)));
        aggregates.forEach(aggregate -> columns.add(aggregate.name()));
        final List<List<String>> rows = new ArrayList<>(groups.size());
        groups.forEach((key, accumulators) -> {
            final List<String> row = new ArrayList<>(key);
            for (final GroupValue accumulator : accumulators) {
                row.add(accumulator.result());
            }
            rows.add(row);
        });
        return new Result(columns, sortAndLimit(rows, columns, query), read, matched);
    }

    private static List<List<String>> sortAndLimit(final List<List<String>> rows,
                                                   final List<String> columns,
                                                   final Query query) {
        if (!query.orderBy().isEmpty()) {
            Comparator<List<String>> comparator = null;
            for (final Order order : query.orderBy()) {
                final int index = index(order.column(), columns);
                Comparator<List<String>> next = (a, b) -> compareValues(cell(a, index), cell(b, index));
                if (order.descending()) {
                    next = next.reversed();
                }
                comparator = comparator == null
                        ? next
                        : comparator.thenComparing(next);
            }
            rows.sort(comparator);
        }
        return query.limit() != null && rows.size() > query.limit()
                ? new ArrayList<>(rows.subList(0, query.limit()))
                : rows;
    }

    static int compareValues(final String a, final String b) {
        final Double x = number(a);
        final Double y = number(b);
        if (x != null && y != null) {
            return Double.compare(x, y);
        }
        if (x != null) {
            return -1;
        }
        if (y != null) {
            return 1;
        }
        return Objects.requireNonNullElse(a, "").compareTo(Objects.requireNonNullElse(b, ""));
    }

    static Double number(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String trimmed = value.strip();
        final char first = trimmed.charAt(0);
        if (!(Character.isDigit(first) || first == '-' || first == '+' || first == '.')) {
            return null;
        }
        try {
            final double number = Double.parseDouble(trimmed);
            return Double.isFinite(number)
                    ? number
                    : null;
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static void checkTime(final long rowsRead) {
        if (rowsRead % 1_000 == 0) {
            ToolDeadline.check();
        }
    }

    private static boolean matches(final List<String> row, final List<CompiledCondition> conditions) {
        for (final CompiledCondition condition : conditions) {
            if (!condition.test(cell(row, condition.index()))) {
                return false;
            }
        }
        return true;
    }

    private static CompiledCondition compile(final Condition condition, final List<String> header) {
        final int index = index(condition.column(), header);
        final String op = Objects.requireNonNullElse(condition.op(), "=").toLowerCase(Locale.ROOT);
        final String value = condition.value();
        final SafePattern pattern = switch (op) {
            case "matches" -> SafePattern.of(value, true, false);
            case "contains", "not_contains" -> SafePattern.of(value, false, true);
            default -> null;
        };
        final Double number = number(value);
        return switch (op) {
            case "=", "!=", "<", "<=", ">", ">=", "contains", "not_contains", "starts_with", "ends_with",
                 "matches", "is_empty", "not_empty" -> {
                if (value == null && !op.equals("is_empty") && !op.equals("not_empty")) {
                    throw new WorkspaceException("Give a value for the condition on " + condition.column() + ".");
                }
                yield new CompiledCondition(index, op, value, number, pattern);
            }
            default -> throw new WorkspaceException("'" + condition.op() + "' is not a condition. Use one of =, "
                                                    + "!=, <, <=, >, >=, contains, not_contains, starts_with, "
                                                    + "ends_with, matches, is_empty or not_empty.");
        };
    }

    private static void checkFunction(final Aggregate aggregate) {
        switch (Objects.requireNonNullElse(aggregate.function(), "").toLowerCase(Locale.ROOT)) {
            case "count" -> {
                // Needs no column.
            }
            case "count_distinct", "sum", "min", "max", "avg" -> {
                if (aggregate.column() == null) {
                    throw new WorkspaceException(aggregate.function() + " needs a column.");
                }
            }
            default -> throw new WorkspaceException("'" + aggregate.function() + "' is not an aggregate. Use one "
                                                    + "of count, count_distinct, sum, min, max or avg.");
        }
    }

    private static List<Integer> allIndexes(final List<String> header) {
        final List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            indexes.add(i);
        }
        return indexes;
    }

    private static List<Integer> indexes(final List<String> names, final List<String> header) {
        return names.stream().map(name -> index(name, header)).toList();
    }

    static int index(final String name, final List<String> columns) {
        if (name != null) {
            final int exact = columns.indexOf(name);
            if (exact >= 0) {
                return exact;
            }
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).equalsIgnoreCase(name.strip())) {
                    return i;
                }
            }
        }
        throw new WorkspaceException("There is no column '" + name + "'. The columns are: "
                                     + String.join(", ", columns) + ".");
    }

    private static String cell(final List<String> row, final int index) {
        return index < row.size()
                ? row.get(index)
                : "";
    }

    private static List<String> project(final List<String> row, final List<Integer> indexes) {
        final List<String> projected = new ArrayList<>(indexes.size());
        for (final int index : indexes) {
            projected.add(cell(row, index));
        }
        return projected;
    }

    private static RowSource open(final Path file, final Format format) throws IOException {
        final Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
        if (format == Format.MARKDOWN) {
            return new MarkdownSource(new BufferedReader(reader));
        }
        final CSVFormat csvFormat = (format == Format.TSV
                ? CSVFormat.TDF
                : CSVFormat.DEFAULT).builder()
                .setIgnoreSurroundingSpaces(true)
                .get();
        return new CsvSource(CSVParser.parse(reader, csvFormat));
    }

    /// The format of a table.
    public enum Format {
        CSV,
        TSV,
        MARKDOWN;

        /// @param path A file's path.
        /// @return Its format, from its name: markdown for .md, TSV for .tsv, otherwise CSV.
        public static Format forPath(final WorkspacePath path) {
            final String name = path.path().toLowerCase(Locale.ROOT);
            if (name.endsWith(".md")) {
                return MARKDOWN;
            } else if (name.endsWith(".tsv")) {
                return TSV;
            }
            return CSV;
        }
    }

    /// A condition on a column's value.
    ///
    /// @param column The column.
    /// @param op     One of =, !=, <, <=, >, >= (compared as numbers when both are numbers), contains,
    ///               not_contains (ignoring case), starts_with, ends_with, matches (a regular expression),
    ///               is_empty, not_empty.
    /// @param value  The value to compare with.
    public record Condition(String column, String op, String value) {

    }

    /// An aggregate over each group: of a column's values, with a function, or an expression in Stroom's expression
    /// language with aggregates in it (see [TableExpression]).
    ///
    /// @param function   One of count, count_distinct, sum, min, max, avg, or null for an expression.
    /// @param column     The column, or null for count or an expression.
    /// @param as         The result column's name, or null for e.g. "sum(bytes)".
    /// @param expression The expression, e.g. round(sum(${bytes}) / count(), 2), or null for a function.
    public record Aggregate(String function, String column, String as, String expression) {

        /// An aggregate of one of the functions.
        public Aggregate(final String function, final String column, final String as) {
            this(function, column, as, null);
        }

        String name() {
            if (as != null && !as.isBlank()) {
                return as;
            }
            if (expression != null) {
                return expression;
            }
            return column == null
                    ? function
                    : function + "(" + column + ")";
        }
    }

    /// A column worked out for each row from its other columns, with an expression in Stroom's expression language
    /// (see [TableExpression]).
    ///
    /// @param as         The column's name.
    /// @param expression The expression, e.g. ${bytes} / ${duration}.
    public record Eval(String as, String expression) {

    }

    /// A column to sort by.
    ///
    /// @param column     The column (of the result).
    /// @param descending True for largest first.
    public record Order(String column, boolean descending) {

    }

    /// What to do to a table, in this order: work out computed columns for each row (eval), filter rows (where),
    /// then either group (groupBy, aggregates, or distinct for the distinct values of the selected columns with their
    /// counts) or select columns, then sort (orderBy), then limit.
    ///
    /// @param eval       Columns worked out for each row, which the rest can use as any other.
    /// @param select     The columns to keep, or empty for all.
    /// @param where      Conditions rows must all meet.
    /// @param groupBy    The columns to group by.
    /// @param aggregates What to work out for each group.
    /// @param distinct   True for the distinct values of the selected columns, with counts.
    /// @param mask       True to mask numbers, IP addresses, dates, UUIDs and hex in grouped values, so free text
    ///                   reduces to patterns.
    /// @param orderBy    How to sort the result.
    /// @param limit      The most rows to return, or null for all.
    public record Query(List<Eval> eval,
                        List<String> select,
                        List<Condition> where,
                        List<String> groupBy,
                        List<Aggregate> aggregates,
                        boolean distinct,
                        boolean mask,
                        List<Order> orderBy,
                        Integer limit) {

        public Query {
            eval = List.copyOf(Objects.requireNonNullElse(eval, List.of()));
            select = List.copyOf(Objects.requireNonNullElse(select, List.of()));
            where = List.copyOf(Objects.requireNonNullElse(where, List.of()));
            groupBy = List.copyOf(Objects.requireNonNullElse(groupBy, List.of()));
            aggregates = List.copyOf(Objects.requireNonNullElse(aggregates, List.of()));
            orderBy = List.copyOf(Objects.requireNonNullElse(orderBy, List.of()));
        }

        /// A query without computed columns.
        public Query(final List<String> select,
                     final List<Condition> where,
                     final List<String> groupBy,
                     final List<Aggregate> aggregates,
                     final boolean distinct,
                     final boolean mask,
                     final List<Order> orderBy,
                     final Integer limit) {
            this(List.of(), select, where, groupBy, aggregates, distinct, mask, orderBy, limit);
        }
    }

    /// The result of a query.
    ///
    /// @param columns     The result's columns.
    /// @param rows        The result's rows.
    /// @param rowsRead    How many rows of the table were read.
    /// @param rowsMatched How many met the conditions.
    public record Result(List<String> columns, List<List<String>> rows, long rowsRead, long rowsMatched) {

    }

    private record CompiledCondition(int index, String op, String value, Double number, SafePattern pattern) {

        /// A number is only compared with numbers, so e.g. an empty cell is neither more nor less than 20.
        private boolean comparable(final String cell) {
            return number == null || TableEngine.number(cell) != null;
        }

        boolean test(final String cell) {
            return switch (op) {
                case "=" -> number != null && TableEngine.number(cell) != null
                        ? number.equals(TableEngine.number(cell))
                        : cell.equals(value);
                case "!=" -> number != null && TableEngine.number(cell) != null
                        ? !number.equals(TableEngine.number(cell))
                        : !cell.equals(value);
                case "<" -> comparable(cell) && compareValues(cell, value) < 0;
                case "<=" -> comparable(cell) && compareValues(cell, value) <= 0;
                case ">" -> comparable(cell) && compareValues(cell, value) > 0;
                case ">=" -> comparable(cell) && compareValues(cell, value) >= 0;
                case "contains", "matches" -> pattern.find(cell);
                case "not_contains" -> !pattern.find(cell);
                case "starts_with" -> cell.startsWith(value);
                case "ends_with" -> cell.endsWith(value);
                case "is_empty" -> cell.isBlank();
                case "not_empty" -> !cell.isBlank();
                default -> false;
            };
        }
    }

    /// What is worked out over a group's rows, for one aggregate.
    private interface GroupValue {

        void add(List<String> row);

        String result();
    }

    /// An aggregate given as an expression, e.g. round(sum(${bytes}) / count(), 2).
    private record ExpressionValue(TableExpression.Group group) implements GroupValue {

        @Override
        public void add(final List<String> row) {
            group.add(row);
        }

        @Override
        public String result() {
            return group.result();
        }
    }

    private static final class Accumulator implements GroupValue {

        private final String function;
        // The column it is of, or -1 for count.
        private final int index;
        private long count;
        private double sum;
        private long numbers;
        private String min;
        private String max;
        private Set<String> distinct;

        Accumulator(final String function, final int index) {
            this.function = function.toLowerCase(Locale.ROOT);
            this.index = index;
        }

        @Override
        public void add(final List<String> row) {
            final String value = index < 0
                    ? null
                    : cell(row, index);
            count++;
            switch (function) {
                case "count_distinct" -> {
                    if (distinct == null) {
                        distinct = new HashSet<>();
                    }
                    if (distinct.size() < MAX_DISTINCT_VALUES) {
                        distinct.add(value);
                    }
                }
                case "sum", "avg" -> {
                    final Double number = number(value);
                    if (number != null) {
                        sum += number;
                        numbers++;
                    }
                }
                case "min" -> {
                    if (value != null && !value.isBlank() && (min == null || compareValues(value, min) < 0)) {
                        min = value;
                    }
                }
                case "max" -> {
                    if (value != null && !value.isBlank() && (max == null || compareValues(value, max) > 0)) {
                        max = value;
                    }
                }
                default -> {
                    // count needs nothing more.
                }
            }
        }

        @Override
        public String result() {
            return switch (function) {
                case "count" -> String.valueOf(count);
                case "count_distinct" -> String.valueOf(distinct == null
                        ? 0
                        : distinct.size());
                case "sum" -> format(sum);
                case "avg" -> numbers == 0
                        ? ""
                        : format(sum / numbers);
                case "min" -> Objects.requireNonNullElse(min, "");
                case "max" -> Objects.requireNonNullElse(max, "");
                default -> "";
            };
        }

        private static String format(final double value) {
            return value == Math.rint(value) && Math.abs(value) < 1e15
                    ? String.valueOf((long) value)
                    : new BigDecimal(value).round(MathContext.DECIMAL64).stripTrailingZeros()
                            .toPlainString();
        }
    }

    /// @return The rows, with the computed columns added to each: each worked out in turn, so one can use those
    /// before it.
    private static RowSource withEvals(final RowSource source, final List<Eval> evals) throws IOException {
        if (evals.isEmpty()) {
            return source;
        }
        final List<String> header = new ArrayList<>(source.header());
        final List<TableExpression> expressions = new ArrayList<>();
        for (final Eval eval : evals) {
            final String name = Objects.requireNonNullElse(eval.as(), "").strip();
            if (name.isEmpty()) {
                throw new WorkspaceException("Give each computed column a name (as).");
            }
            if (header.stream().anyMatch(name::equalsIgnoreCase)) {
                throw new WorkspaceException("There is already a column '" + name + "'. Name the computed column "
                                             + "something else.");
            }
            final TableExpression expression = TableExpression.compile(eval.expression(), header);
            if (expression.isAggregate()) {
                throw new ExpressionException("The computed column '" + name + "' uses an aggregate (e.g. sum or "
                                             + "count), which works over groups of rows: give it in aggregates, "
                                             + "with groupBy.");
            }
            expressions.add(expression);
            header.add(name);
        }
        return new EvalSource(source, header, expressions);
    }

    private interface RowSource extends AutoCloseable {

        List<String> header();

        Iterator<List<String>> rows();

        @Override
        void close() throws IOException;
    }

    /// A table's rows, with computed columns added.
    private static final class EvalSource implements RowSource {

        private final RowSource source;
        private final List<String> header;
        private final List<TableExpression> expressions;

        private EvalSource(final RowSource source,
                           final List<String> header,
                           final List<TableExpression> expressions) {
            this.source = source;
            this.header = List.copyOf(header);
            this.expressions = expressions;
        }

        @Override
        public List<String> header() {
            return header;
        }

        @Override
        public Iterator<List<String>> rows() {
            final Iterator<List<String>> rows = source.rows();
            final int width = header.size() - expressions.size();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return rows.hasNext();
                }

                @Override
                public List<String> next() {
                    final List<String> row = new ArrayList<>(header.size());
                    final List<String> given = rows.next();
                    for (int i = 0; i < width; i++) {
                        row.add(cell(given, i));
                    }
                    for (final TableExpression expression : expressions) {
                        row.add(expression.evaluate(row));
                    }
                    return row;
                }
            };
        }

        @Override
        public void close() throws IOException {
            source.close();
        }
    }

    private static final class CsvSource implements RowSource {

        private final CSVParser parser;
        private final Iterator<CSVRecord> records;
        private final List<String> header;

        CsvSource(final CSVParser parser) {
            this.parser = parser;
            this.records = parser.iterator();
            this.header = records.hasNext()
                    ? records.next().toList()
                    : List.of();
        }

        @Override
        public List<String> header() {
            return header;
        }

        @Override
        public Iterator<List<String>> rows() {
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return records.hasNext();
                }

                @Override
                public List<String> next() {
                    return records.next().toList();
                }
            };
        }

        @Override
        public void close() throws IOException {
            parser.close();
        }
    }

    private static final class MarkdownSource implements RowSource {

        private final BufferedReader reader;
        private final List<String> header;
        private String nextLine;

        MarkdownSource(final BufferedReader reader) throws IOException {
            this.reader = reader;
            String line = reader.readLine();
            while (line != null && line.isBlank()) {
                line = reader.readLine();
            }
            this.header = line == null
                    ? List.of()
                    : MarkdownTables.parseRow(line);
            this.nextLine = advance();
        }

        private String advance() throws IOException {
            String line = reader.readLine();
            while (line != null && (line.isBlank() || MarkdownTables.isSeparatorRow(line))) {
                line = reader.readLine();
            }
            return line;
        }

        @Override
        public List<String> header() {
            return header;
        }

        @Override
        public Iterator<List<String>> rows() {
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return nextLine != null;
                }

                @Override
                public List<String> next() {
                    final List<String> row = MarkdownTables.parseRow(nextLine);
                    try {
                        nextLine = advance();
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return row;
                }
            };
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
