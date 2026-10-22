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

import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.ExpressionException;
import stroom.ai.impl.workspace.ExpressionFunctions;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.TableEngine;
import stroom.ai.impl.workspace.TableEngine.Aggregate;
import stroom.ai.impl.workspace.TableEngine.Condition;
import stroom.ai.impl.workspace.TableEngine.Eval;
import stroom.ai.impl.workspace.TableEngine.Format;
import stroom.ai.impl.workspace.TableEngine.Order;
import stroom.ai.impl.workspace.TableEngine.Query;
import stroom.ai.impl.workspace.TableEngine.Result;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.util.concurrent.LazyValue;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code table} - filters, groups, aggregates and sorts a table in the workspace (an attached table, or a CSV or
 * TSV file), in code, so figures are exact. The query is typed JSON, never code or a command.
 */
class TableTool extends AbstractWorkspaceTool {

    static final String NAME = "table";
    static final String HELP_ARG = "help";
    /// Examples of expressions, in the tool's description (each is checked to work).
    static final String EXAMPLES = "eval {\"as\": \"rate\", \"expression\": \"round(${bytes} / ${seconds}, 1)\"}, "
                                   + "{\"as\": \"site\", \"expression\": \"upperCase(substring(${host}, 0, 3))\"}, "
                                   + "{\"as\": \"hour\", \"expression\": \"formatDate(floorHour(${time}), "
                                   + "'yyyy-MM-dd HH:00')\"}; aggregates {\"as\": \"error %\", \"expression\": "
                                   + "\"round(sum(if(${status} = 'ERROR', 1, 0)) / count() * 100, 1)\"}";
    private static final LazyValue<String> CATALOGUE = LazyValue.initialisedBy(ExpressionFunctions::catalogue);
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
    public AiToolCategory category() {
        return AiToolCategory.CORE;
    }

    @Override
    public ToolDefinition definition() {
        final ObjectSchema condition = ObjectSchema.builder()
                .addStringProperty("column", "The column.")
                .addEnumProperty("op", List.of("=", "!=", "<", "<=", ">", ">=", "contains", "not_contains",
                        "starts_with", "ends_with", "matches", "is_empty", "not_empty"), "The comparison. "
                        + "contains ignores case; matches takes a regular expression.")
                .addStringProperty("value", "The value to compare with.")
                .required("column", "op")
                .build();
        final ObjectSchema aggregate = ObjectSchema.builder()
                .addEnumProperty("function", List.of("count", "count_distinct", "sum", "min", "max", "avg"),
                        "What to work out for each group, or give expression instead.")
                .addStringProperty("column", "The column (not needed for count).")
                .addStringProperty("expression", "An expression with aggregates, worked out for each group, "
                                                 + "instead of function, e.g. round(sum(${bytes}) / count(), 2).")
                .addStringProperty("as", "A name for the result column.")
                .build();
        final ObjectSchema eval = ObjectSchema.builder()
                .addStringProperty("as", "The new column's name.")
                .addStringProperty("expression", "What it is for each row, e.g. ${bytes} / ${duration}.")
                .required("as", "expression")
                .build();
        final ObjectSchema order = ObjectSchema.builder()
                .addStringProperty("column", "A column of the result.")
                .addBooleanProperty("descending", "True for largest first.")
                .required("column")
                .build();
        return ToolDefinition.builder()
                .name(NAME)
                .description(description())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PATH_ARG, ToolDescription.builder()
                                .general("The table")
                                .chatOnly(", e.g. /attachments/12.md")
                                .general(".")
                                .build())
                        .addProperty("eval", ArraySchema.builder().items(eval)
                                .description("Columns worked out for each row, first, which the rest can use as "
                                             + "any other.").build())
                        .addProperty("select", stringArraySchema("The columns to keep (default all)."))
                        .addProperty("where", ArraySchema.builder().items(condition)
                                .description("Conditions every row must meet.").build())
                        .addProperty("groupBy", stringArraySchema("The columns to group by."))
                        .addProperty("aggregates", ArraySchema.builder().items(aggregate)
                                .description("What to work out for each group (default count).").build())
                        .addBooleanProperty("distinct", "True for the distinct values of the selected columns, "
                                                        + "with counts.")
                        .addBooleanProperty("mask", "True to mask numbers, IPs, dates and IDs in grouped values.")
                        .addProperty("orderBy", ArraySchema.builder().items(order)
                                .description("How to sort the result.").build())
                        .addIntegerProperty("limit", "The most rows to return.")
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("A .csv file in /outputs/ to write the result to.")
                                .build())
                        .addProperty(HELP_ARG, stringArraySchema("Expression functions to describe (what each "
                                                                 + "takes and does), instead of querying a "
                                                                 + "table."))
                        .build())
                .build();
    }

    // What the tool does, how to write expressions, and the functions there are, by category. The functions are
    // Stroom's, so are listed when first asked for.
    private static ToolDescription description() {
        return ToolDescription.builder()
                .general("Queries a table")
                .chatOnly(" in the workspace (an attached table in /attachments/, or a .csv or .tsv file)")
                .general(" exactly, in code: eval adds columns worked out for each row, where filters rows, then "
                         + "groupBy and aggregates (or distinct, for each distinct value with its count) summarise "
                         + "them, orderBy sorts and limit cuts the result. Use mask with distinct or groupBy to count "
                         + "free text (e.g. messages) as patterns, with numbers, IPs, dates and IDs masked. Use this "
                         + "for any figures; never count rows or work figures out by reading them.")
                .chatOnly(" Give out to write the result to a CSV file.")
                .general("\n\n" + expressionsHelp())
                .build();
    }

    // How to write expressions, and the functions there are, by category.
    private static String expressionsHelp() {
        return "Expressions (eval, and aggregates given as expression) are in Stroom's expression language, as in "
               + "dashboards: ${column} is a column's value, 'text' or \"text\" a string, operators are "
               + "+ - * / % ^ and = != < <= > >=, and functions are called as name(arguments). The names are "
               + "Stroom's, not SQL's: the mean is average(), not avg(). A cell that is "
               + "a number is a number (except one with a leading zero, e.g. 007, which is text); others are "
               + "text, which date functions read as dates. An empty cell is no value, and a failure (e.g. "
               + "arithmetic on text) gives ERR: in its cell. In aggregates, min and max take an empty cell as "
               + "the least value, so filter with not_empty first. For example: "
               + EXAMPLES + ". An expression in aggregates is worked out over each group, so may use aggregate "
               + "functions; one in eval is worked out for each row, so may not. Give help with function names for "
               + "what each takes, before using one you haven't used.\n\nFunctions, by category (one shown as "
               + "name() takes no arguments: e.g. year() is the start of this year, so a date's year is "
               + "formatDate(${time}, 'yyyy')):\n" + CATALOGUE.getValueWithLocks();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final List<String> help = strings(argsMap(request).get(HELP_ARG), HELP_ARG);
        if (!help.isEmpty()) {
            return ToolResult.text(ExpressionFunctions.help(help));
        }
        final WorkspacePath path = pathArg(request, PATH_ARG);
        try {
            return ToolResult.text(query(request, context, path));
        } catch (final ExpressionException e) {
            // The model fixes its expression from the message; a column's summary wouldn't help.
            throw e;
        } catch (final WorkspaceException e) {
            return ToolResult.text(fallBack(request, context, path, e));
        }
    }

    /**
     * After a table has failed to be queried twice in a run, summarises the column the query was about instead
     * (the automatic fallback), so the model is not left trying query after query.
     */
    private String fallBack(final ToolCall request,
                            final ToolCallContext context,
                            final WorkspacePath path,
                            final WorkspaceException e) {
        final int failures = context.runContext()
                .map(runContext -> runContext.recordFailure(NAME + ":" + path))
                .orElse(0);
        if (failures < FAILURES_BEFORE_FALLBACK) {
            throw e;
        }
        final SessionWorkspace workspace = sessionWorkspace(context);
        final String column = referencedColumn(request, workspace, path);
        if (column == null) {
            throw new WorkspaceException(e.getMessage() + " Queries of this table have failed " + failures
                                         + " times; consider summarise_column to summarise a column instead.");
        }
        return "Error: " + e.getMessage() + "\n\nQueries of this table have failed " + failures + " times, so "
               + "here is a summary of column '" + column + "' instead:\n\n"
               + columnSummariser.summarise(workspace, context, path, column, null);
    }

    private String referencedColumn(final ToolCall request,
                                    final SessionWorkspace workspace,
                                    final WorkspacePath path) {
        final List<String> columns = TableEngine.run(workspace.localFile(path), Format.forPath(path),
                new Query(List.of(), List.of(), List.of(), List.of(), false, false, List.of(), 1)).columns();
        final Map<?, ?> args = argsMap(request);
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

    private String query(final ToolCall request, final ToolCallContext context, final WorkspacePath path) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final WorkspacePath out = outArg(request);
        final Map<?, ?> args = argsMap(request);
        final Query query = new Query(
                evals(args.get("eval")),
                strings(args.get("select"), "select"),
                conditions(args.get("where")),
                strings(args.get("groupBy"), "groupBy"),
                aggregates(args.get("aggregates")),
                Boolean.TRUE.equals(args.get("distinct")) || "true".equals(String.valueOf(args.get("distinct"))),
                Boolean.TRUE.equals(args.get("mask")) || "true".equals(String.valueOf(args.get("mask"))),
                orders(args.get("orderBy")),
                limit(args.get("limit")));

        final Result result = TableEngine.run(workspace.localFile(path), Format.forPath(path), query);
        final String summary = result.rowsMatched() + " of " + result.rowsRead() + " rows matched; the result has "
                               + result.rows().size() + " row(s).";
        if (out != null) {
            writeCsv(workspace, out, result, workspace.sourceDocUuid(path));
            return "Wrote the result to " + out + ". " + summary + " The first rows:\n\n"
                   + markdown(context, result.columns(), result.rows(), PREVIEW_ROWS);
        }
        return summary + "\n\n" + markdown(context, result.columns(), result.rows(),
                limits(context).getMaxLinesPerRead());
    }

    private static String markdown(final ToolCallContext context,
                                   final List<String> columns,
                                   final List<List<String>> rows,
                                   final int maxRows) {
        final StringBuilder sb = new StringBuilder(row(columns));
        sb.append("|").append(" --- |".repeat(columns.size())).append('\n');
        int shown = 0;
        for (final List<String> row : rows) {
            final String line = row(row);
            if (shown >= maxRows || sb.length() + line.length() > limits(context).getMaxCharsPerRead()) {
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

    private void writeCsv(final SessionWorkspace workspace, final WorkspacePath out, final Result result,
            final String source) {
        ToolTables.writeCsv(workspace, out, result.columns(), result.rows(), source);
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
            final String expression = string(map.get("expression"));
            if (expression == null && string(map.get("function")) == null) {
                throw new WorkspaceException("Give each aggregate a function, or an expression.");
            }
            aggregates.add(new Aggregate(string(map.get("function")), string(map.get("column")),
                    string(map.get("as")), expression));
        }
        return aggregates;
    }

    private static List<Eval> evals(final Object value) {
        final List<Eval> evals = new ArrayList<>();
        for (final Map<?, ?> map : maps(value, "eval")) {
            evals.add(new Eval(string(map.get("as")), string(map.get("expression"))));
        }
        return evals;
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
