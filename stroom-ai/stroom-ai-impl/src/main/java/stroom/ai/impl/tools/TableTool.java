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

package stroom.ai.impl.tools;

import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.TableEngine;
import stroom.ai.impl.workspace.TableEngine.Aggregate;
import stroom.ai.impl.workspace.TableEngine.Condition;
import stroom.ai.impl.workspace.TableEngine.Format;
import stroom.ai.impl.workspace.TableEngine.Order;
import stroom.ai.impl.workspace.TableEngine.Query;
import stroom.ai.impl.workspace.TableEngine.Result;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.util.json.JsonUtil;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import jakarta.inject.Inject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code table} - filters, groups, aggregates and sorts a table in the workspace (an attached table, or a CSV or
 * TSV file), in code, so figures are exact. The query is typed JSON, never code or a command.
 */
class TableTool extends AbstractWorkspaceTool {

    static final String NAME = "table";
    static final int PREVIEW_ROWS = 10;

    /**
     * After this many failed queries of the same table in a run, its column is summarised instead.
     */
    static final int FAILURES_BEFORE_FALLBACK = 2;

    private final ColumnSummariser columnSummariser;

    @Inject
    TableTool(final ChatWorkspace workspace, final ColumnSummariser columnSummariser) {
        super(workspace);
        this.columnSummariser = columnSummariser;
    }

    @Override
    public ToolSpecification specification() {
        final JsonObjectSchema condition = JsonObjectSchema.builder()
                .addStringProperty("column", "The column.")
                .addEnumProperty("op", List.of("=", "!=", "<", "<=", ">", ">=", "contains", "not_contains",
                        "starts_with", "ends_with", "matches", "is_empty", "not_empty"), "The comparison. "
                        + "contains ignores case; matches takes a regular expression.")
                .addStringProperty("value", "The value to compare with.")
                .required("column", "op")
                .build();
        final JsonObjectSchema aggregate = JsonObjectSchema.builder()
                .addEnumProperty("function", List.of("count", "count_distinct", "sum", "min", "max", "avg"),
                        "What to work out for each group.")
                .addStringProperty("column", "The column (not needed for count).")
                .addStringProperty("as", "A name for the result column.")
                .required("function")
                .build();
        final JsonObjectSchema order = JsonObjectSchema.builder()
                .addStringProperty("column", "A column of the result.")
                .addBooleanProperty("descending", "True for largest first.")
                .required("column")
                .build();
        return ToolSpecification.builder()
                .name(NAME)
                .description("Queries a table in the workspace (an attached table in /attachments/, or a .csv or "
                             + ".tsv file) exactly, in code: where filters rows, then groupBy and aggregates (or "
                             + "distinct, for each distinct value with its count) summarise them, orderBy sorts "
                             + "and limit cuts the result. Use mask with distinct or groupBy to count free text "
                             + "(e.g. messages) as patterns, with numbers, IPs, dates and IDs masked. Use this "
                             + "for any figures; never count rows by reading them. Give out to write the result "
                             + "to a CSV file.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty(PATH_ARG, "The table, e.g. /attachments/12.md.")
                        .addProperty("select", stringArray("The columns to keep (default all)."))
                        .addProperty("where", JsonArraySchema.builder().items(condition)
                                .description("Conditions every row must meet.").build())
                        .addProperty("groupBy", stringArray("The columns to group by."))
                        .addProperty("aggregates", JsonArraySchema.builder().items(aggregate)
                                .description("What to work out for each group (default count).").build())
                        .addBooleanProperty("distinct", "True for the distinct values of the selected columns, "
                                                        + "with counts.")
                        .addBooleanProperty("mask", "True to mask numbers, IPs, dates and IDs in grouped values.")
                        .addProperty("orderBy", JsonArraySchema.builder().items(order)
                                .description("How to sort the result.").build())
                        .addIntegerProperty("limit", "The most rows to return.")
                        .addStringProperty(OUT_ARG, "A .csv file in /outputs/ to write the result to.")
                        .required(PATH_ARG)
                        .build())
                .build();
    }

    private static JsonArraySchema stringArray(final String description) {
        return JsonArraySchema.builder().items(new JsonStringSchema()).description(description).build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        final WorkspacePath path = pathArg(request, PATH_ARG);
        try {
            return query(request, chatId, path);
        } catch (final WorkspaceException e) {
            return fallBack(request, chatId, path, e);
        }
    }

    /**
     * After a table has failed to be queried twice in a run, summarises the column the query was about instead
     * (the automatic fallback), so the model is not left trying query after query.
     */
    private String fallBack(final ToolExecutionRequest request,
                            final int chatId,
                            final WorkspacePath path,
                            final WorkspaceException e) {
        final int failures = ToolRunContext.current()
                .map(context -> context.recordFailure(NAME + ":" + path))
                .orElse(0);
        if (failures < FAILURES_BEFORE_FALLBACK) {
            throw e;
        }
        final String column = referencedColumn(request, chatId, path);
        if (column == null) {
            throw new WorkspaceException(e.getMessage() + " Queries of this table have failed " + failures
                                         + " times; consider summarise_column to summarise a column instead.");
        }
        return "Error: " + e.getMessage() + "\n\nQueries of this table have failed " + failures + " times, so "
               + "here is a summary of column '" + column + "' instead:\n\n"
               + columnSummariser.summarise(chatId, path, column, null);
    }

    private String referencedColumn(final ToolExecutionRequest request, final int chatId, final WorkspacePath path) {
        final List<String> columns = TableEngine.run(workspace.localFile(chatId, path), Format.forPath(path),
                new Query(List.of(), List.of(), List.of(), List.of(), false, false, List.of(), 1)).columns();
        final Map<?, ?> args = args(request);
        final List<String> referenced = new ArrayList<>();
        try {
            conditions(args.get("where")).forEach(condition -> referenced.add(condition.column()));
            referenced.addAll(strings(args.get("groupBy"), "groupBy"));
            referenced.addAll(strings(args.get("select"), "select"));
        } catch (final WorkspaceException ignored) {
            // The query itself was malformed; nothing to go on.
        }
        for (final String name : referenced) {
            for (final String column : columns) {
                if (column.equalsIgnoreCase(String.valueOf(name).strip())) {
                    return column;
                }
            }
        }
        return null;
    }

    private String query(final ToolExecutionRequest request, final int chatId, final WorkspacePath path) {
        final WorkspacePath out = outArg(request);
        final Map<?, ?> args = args(request);
        final Query query = new Query(
                strings(args.get("select"), "select"),
                conditions(args.get("where")),
                strings(args.get("groupBy"), "groupBy"),
                aggregates(args.get("aggregates")),
                Boolean.TRUE.equals(args.get("distinct")) || "true".equals(String.valueOf(args.get("distinct"))),
                Boolean.TRUE.equals(args.get("mask")) || "true".equals(String.valueOf(args.get("mask"))),
                orders(args.get("orderBy")),
                limit(args.get("limit")));

        final Result result = TableEngine.run(workspace.localFile(chatId, path), Format.forPath(path), query);
        final String summary = result.rowsMatched() + " of " + result.rowsRead() + " rows matched; the result has "
                               + result.rows().size() + " row(s).";
        if (out != null) {
            writeCsv(chatId, out, result, workspace.sourceDocUuid(chatId, path));
            return "Wrote the result to " + out + ". " + summary + " The first rows:\n\n"
                   + markdown(result.columns(), result.rows(), PREVIEW_ROWS);
        }
        return summary + "\n\n" + markdown(result.columns(), result.rows(), limits().getMaxLinesPerRead());
    }

    private String markdown(final List<String> columns, final List<List<String>> rows, final int maxRows) {
        final StringBuilder sb = new StringBuilder(row(columns));
        sb.append("|").append(" --- |".repeat(columns.size())).append('\n');
        int shown = 0;
        for (final List<String> row : rows) {
            final String line = row(row);
            if (shown >= maxRows || sb.length() + line.length() > limits().getMaxCharsPerRead()) {
                sb.append("\n(").append(rows.size() - shown).append(" more rows not shown. Give out to write them "
                                                                    + "all to a file.)");
                break;
            }
            sb.append(line);
            shown++;
        }
        return sb.toString().strip();
    }

    private static String row(final List<String> cells) {
        final StringBuilder sb = new StringBuilder("|");
        for (final String cell : cells) {
            sb.append(' ').append(shortenLine(cell).replace("|", "\\|").replace('\n', ' ')).append(" |");
        }
        return sb.append('\n').toString();
    }

    private void writeCsv(final int chatId, final WorkspacePath out, final Result result, final String source) {
        final Path temp = workspace.tempFile();
        try {
            try (final BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8);
                    final CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
                printer.printRecord(result.columns());
                for (final List<String> row : result.rows()) {
                    printer.printRecord(row);
                }
            }
            workspace.write(chatId, out, temp, source);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            ChatWorkspace.deleteQuietly(temp);
        }
    }

    private static Map<?, ?> args(final ToolExecutionRequest request) {
        try {
            final Map<?, ?> args = JsonUtil.readValue(request.arguments(), Map.class);
            return args == null
                    ? Map.of()
                    : args;
        } catch (final RuntimeException e) {
            throw new WorkspaceException("The arguments are not valid JSON.");
        }
    }

    private static List<String> strings(final Object value, final String name) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof final List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof final String string) {
            return List.of(string);
        }
        throw new WorkspaceException(name + " must be a list of column names.");
    }

    private static List<Condition> conditions(final Object value) {
        final List<Condition> conditions = new ArrayList<>();
        for (final Map<?, ?> map : maps(value, "where")) {
            conditions.add(new Condition(string(map.get("column")), string(map.get("op")),
                    string(map.get("value"))));
        }
        return conditions;
    }

    private static List<Aggregate> aggregates(final Object value) {
        final List<Aggregate> aggregates = new ArrayList<>();
        for (final Map<?, ?> map : maps(value, "aggregates")) {
            aggregates.add(new Aggregate(string(map.get("function")), string(map.get("column")),
                    string(map.get("as"))));
        }
        return aggregates;
    }

    private static List<Order> orders(final Object value) {
        final List<Order> orders = new ArrayList<>();
        for (final Map<?, ?> map : maps(value, "orderBy")) {
            orders.add(new Order(string(map.get("column")), Boolean.TRUE.equals(map.get("descending"))
                                                            || "true".equals(string(map.get("descending")))));
        }
        return orders;
    }

    private static List<Map<?, ?>> maps(final Object value, final String name) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof final List<?> list) {
            final List<Map<?, ?>> maps = new ArrayList<>();
            for (final Object item : list) {
                if (item instanceof final Map<?, ?> map) {
                    maps.add(map);
                } else {
                    throw new WorkspaceException(name + " must be a list of objects.");
                }
            }
            return maps;
        }
        throw new WorkspaceException(name + " must be a list of objects.");
    }

    private static Integer limit(final Object value) {
        if (value == null) {
            return null;
        }
        try {
            final int limit = (int) Double.parseDouble(String.valueOf(value));
            if (limit < 1) {
                throw new WorkspaceException("limit must be at least 1.");
            }
            return limit;
        } catch (final NumberFormatException e) {
            throw new WorkspaceException("limit must be a number.");
        }
    }

    private static String string(final Object value) {
        return value == null
                ? null
                : String.valueOf(value);
    }
}
