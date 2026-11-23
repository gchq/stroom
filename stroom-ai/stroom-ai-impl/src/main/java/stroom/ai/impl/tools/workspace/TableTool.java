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

package stroom.ai.impl.tools.workspace;

import stroom.ai.impl.tools.AbstractWorkspaceTool;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolDescription;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolTables;
import stroom.ai.impl.tools.query.CurrentResultsTool;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.time.DateFunctionZones;
import stroom.ai.impl.tools.time.ToolTimeZone;
import stroom.ai.impl.tools.time.UserTimeZones;
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
import stroom.ai.impl.workspace.TableJoin;
import stroom.ai.impl.workspace.TableJoin.Joined;
import stroom.ai.impl.workspace.TableJoin.Keep;
import stroom.ai.impl.workspace.TableJoin.Spec;
import stroom.ai.impl.workspace.TableJoin.Within;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.concurrent.LazyValue;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * {@code table} - filters, groups, aggregates and sorts a table in the workspace (an attached table, or a CSV or
 * TSV file), in code, so figures are exact. The query is typed JSON, never code or a command.
 */
public class TableTool extends AbstractWorkspaceTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TableTool.class);

    public static final String NAME = "table";
    static final String HELP_ARG = "help";
    static final String JOIN_ARG = "join";
    /// Examples of expressions, in the tool's description (each is checked to work).
    static final String EXAMPLES = "eval {\"as\": \"rate\", \"expression\": \"round(${bytes} / ${seconds}, 1)\"}, "
                                   + "{\"as\": \"site\", \"expression\": \"upperCase(substring(${host}, 0, 3))\"}, "
                                   + "{\"as\": \"hour\", \"expression\": \"formatDate(floorHour(${time}), "
                                   + "'yyyy-MM-dd HH:00')\"}; aggregates {\"as\": \"error %\", \"expression\": "
                                   + "\"round(sum(if(${status} = 'ERROR', 1, 0)) / count() * 100, 1)\"}";
    private static final LazyValue<String> CATALOGUE = LazyValue.initialisedBy(ExpressionFunctions::catalogue);
    // Kinds of file that are never tables.
    private static final Set<String> NOT_TABLES = Set.of("json", "jsonl", "ndjson", "xml", "xsl", "xslt", "txt",
            "log", "yaml", "yml");
    static final int PREVIEW_ROWS = 10;
    // What list arguments hold, for errors.
    private static final String OBJECTS = "objects";
    private static final String COLUMN_NAMES = "column names";
    // The most of a wrongly shaped argument quoted back in its error.
    private static final int MAX_QUOTED = 80;

    /**
     * After this many failed queries of the same table in a run, its column is summarised instead. Only queries that
     * could be read count: arguments in a shape that can't be read are refused without counting.
     */
    static final int FAILURES_BEFORE_FALLBACK = 2;

    /// The rows of an Elastic Index read for a table grouped or totalled here that make it worth saying esql_query
    /// would have done it in Elasticsearch.
    static final int ESQL_NOTE_ROWS = 1_000;

    private final ColumnSummariser columnSummariser;
    private DataSourceAccess dataSourceAccess;

    /// @param workspace The chats' workspaces, which the tool reads and writes.
    /// @param columnSummariser Summarises a column's values.
    @Inject
    public TableTool(final ChatWorkspace workspace, final ColumnSummariser columnSummariser) {
        super(workspace);
        this.columnSummariser = columnSummariser;
    }

    /// @param dataSourceAccess Finds the data source a table came from, to say when grouping its rows here would
    ///                         have been done by Elasticsearch with esql_query.
    @Inject
    public void setDataSourceAccess(final DataSourceAccess dataSourceAccess) {
        this.dataSourceAccess = dataSourceAccess;
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
                        "starts_with", "ends_with", "matches", "is_empty", "not_empty", "in", "not_in"),
                        "The comparison. contains ignores case; matches takes a regular expression; in and "
                        + "not_in take several values.")
                .addStringProperty("value", "The value to compare with; for in and not_in, the values "
                                            + "separated by commas.")
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
        final ObjectSchema within = ObjectSchema.builder()
                .addStringProperty("column", "This table's time column (ISO 8601, UTC if no zone, or epoch ms).")
                .addStringProperty("rightColumn", "The other table's, if named differently.")
                .addIntegerProperty("seconds", "The most seconds apart.")
                .addBooleanProperty("after", "True if the other table's time must be at or after this one's "
                                             + "(default either side).")
                .required("column", "seconds")
                .build();
        final ObjectSchema join = ObjectSchema.builder()
                .description("Joins another table to this one first; the rest of the query is then of the joined "
                             + "rows. A column of the other table this one has too is named right.<name>.")
                .addStringProperty(PATH_ARG, "The other table.")
                .addProperty("on", stringArraySchema("Columns whose values must be the same (exact text) in both; "
                                                     + "an empty cell matches nothing."))
                .addProperty("rightOn", stringArraySchema("The other table's columns for on, in order, if named "
                                                          + "differently."))
                .addEnumProperty("keep", List.of("both", "unmatched"), "both (default): each row with each row "
                                                                       + "of the other it matches; unmatched: "
                                                                       + "this table's rows nothing matches.")
                .addProperty("within", within)
                .required(PATH_ARG, "on")
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
                        .addStringProperty(UserTimeZones.ARG, UserTimeZones.DESCRIPTION)
                        .addProperty(JOIN_ARG, join)
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("A file in /outputs/ to write the result to: .csv, .tsv or .md.")
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
                .chatOnly(" in the workspace (an attached table in /attachments/, a tab's results that current_results "
                          + "saved in /tabs/results/, or a .csv or .tsv file)")
                .general(" exactly, in code: eval adds columns worked out for each row, where filters rows, then "
                         + "groupBy and aggregates (or distinct, for each distinct value with its count) summarise "
                         + "them, orderBy sorts and limit cuts the result. Use mask with distinct or groupBy to count "
                         + "free text (e.g. messages) as patterns, with numbers, IPs, dates and IDs masked. Use this "
                         + "for any figures; never count rows or work figures out by reading them. Give join to match "
                         + "its rows with another table's first, e.g. on a user and an id, within some seconds.")
                .general(" To join two Stroom data sources, a StroomQL join")
                .chatOnly(" (run_stroomql)")
                .general(" is better.")
                .chatOnly(" Give out to write the result to a file.")
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
        final Map<?, ?> args = argsMap(request);
        final List<String> help = strings(args.get(HELP_ARG), HELP_ARG);
        if (!help.isEmpty()) {
            return ToolResult.text(ExpressionFunctions.help(help));
        }
        final WorkspacePath path = pathArg(request, PATH_ARG);
        refuseUnlessATable(path);
        // Its expressions' dates (now(), day(), floorDay, formatDate, parseDate...) are in the call's zone.
        final ToolTimeZone zone = timeZone(request, context);
        // Arguments in a shape that can't be read are refused here, before the query is run, so they don't count
        // as failures of the query towards the fallback (seen: aggregates given as a JSON string in two parallel
        // calls, the second refusal coming with an unasked-for summary of a column).
        final Query query = query(args, () -> columns(sessionWorkspace(context), path))
                .withDateTimeSettings(zone.dateTimeSettings(System.currentTimeMillis()))
                .withExpressions(expression -> DateFunctionZones.withZone(expression, zone));
        final WorkspacePath out = outArg(request);
        final Map<?, ?> join = joinArg(args.get(JOIN_ARG));
        try {
            return ToolResult.text(join == null
                    ? run(context, path, query, out, zone)
                    : runJoined(context, path, join, query, out, zone));
        } catch (final ExpressionException e) {
            // The model fixes its expression from the message; a column's summary wouldn't help.
            throw e;
        } catch (final WorkspaceException e) {
            return ToolResult.text(fallBack(request, context, path, e));
        }
    }

    /// A file of another kind isn't read as CSV, which it isn't (seen: a survey's JSON, its first line refused for
    /// a stray quote).
    ///
    /// @param path The file the table is read from.
    /// @throws WorkspaceException if it is a kind of file that is never a table.
    static void refuseUnlessATable(final WorkspacePath path) {
        final String name = path.path().toLowerCase(Locale.ROOT);
        final int dot = name.lastIndexOf('.');
        final String extension = dot < 0 || dot < name.lastIndexOf('/')
                ? ""
                : name.substring(dot + 1);
        if (NOT_TABLES.contains(extension)) {
            throw new WorkspaceException(path + " is " + extension.toUpperCase(Locale.ROOT) + ", not a table: "
                                         + ("json".equals(extension) || "jsonl".equals(extension)
                                            || "ndjson".equals(extension)
                    ? "query it with jq."
                    : "read it with read_file or grep.")
                                         + " table reads .csv, .tsv and .md tables.");
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
        final List<String> columns = columns(workspace, path);
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

    // The table's columns, read from its header.
    private static List<String> columns(final SessionWorkspace workspace, final WorkspacePath path) {
        return TableEngine.run(workspace.localFile(path), Format.forPath(path),
                new Query(List.of(), List.of(), List.of(), List.of(), false, false, List.of(), 1)).columns();
    }

    /// Reads the query from a call's arguments, taking each as the model meant it where that can be told (see
    /// [#loose]).
    ///
    /// @param args         The call's arguments.
    /// @param tableColumns The table's columns, read only if an orderBy column starting with '-' needs them.
    /// @return The query.
    /// @throws WorkspaceException if an argument is in a shape that can't be read, saying what was given.
    static Query query(final Map<?, ?> args, final Supplier<List<String>> tableColumns) {
        final List<Eval> evals = evals(args.get("eval"));
        final List<Aggregate> aggregates = aggregates(args.get("aggregates"));
        final LazyValue<Set<String>> resultColumns = LazyValue.initialisedBy(() ->
                resultColumns(tableColumns, evals, aggregates));
        return new Query(
                evals,
                strings(args.get("select"), "select"),
                conditions(args.get("where")),
                strings(args.get("groupBy"), "groupBy"),
                aggregates,
                Boolean.TRUE.equals(args.get("distinct")) || "true".equals(String.valueOf(args.get("distinct"))),
                Boolean.TRUE.equals(args.get("mask")) || "true".equals(String.valueOf(args.get("mask"))),
                orders(args.get("orderBy"), resultColumns::getValueWithLocks),
                limit(args.get("limit")));
    }

    // The columns a result may have: the table's, and those eval and aggregates name. The table's are left out if
    // it can't be read, which running the query then reports.
    private static Set<String> resultColumns(final Supplier<List<String>> tableColumns,
                                             final List<Eval> evals,
                                             final List<Aggregate> aggregates) {
        final Set<String> columns = new HashSet<>();
        try {
            columns.addAll(tableColumns.get());
        } catch (final WorkspaceException e) {
            LOGGER.debug("Unable to read the table's columns: {}", e.getMessage());
        }
        evals.stream()
                .map(Eval::as)
                .filter(Objects::nonNull)
                .forEach(columns::add);
        aggregates.stream()
                .map(Aggregate::as)
                .filter(Objects::nonNull)
                .forEach(columns::add);
        return columns;
    }

    private String run(final ToolCallContext context,
                       final WorkspacePath path,
                       final Query query,
                       final WorkspacePath out,
                       final ToolTimeZone zone) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final Result result = TableEngine.run(workspace.localFile(path), Format.forPath(path), query);
        // A snapshot of a table's results may hold only some of the rows its search found, which the answer must say.
        final String summary = result.rowsMatched() + " of " + result.rowsRead() + " rows matched; the result has "
                               + result.rows().size() + " row(s)."
                               + CurrentResultsTool.coverage(workspace, path, result.rowsRead())
                                       .map(coverage -> " " + coverage)
                                       .orElse("")
                               + esqlNote(workspace.sourceDocUuid(path), query, result.rowsRead())
                               + zoneNote(query, zone);
        if (out != null) {
            writeTable(workspace, out, result, workspace.sourceDocUuid(path));
            return "Wrote the result to " + out + ". " + summary + " The first rows:\n\n"
                   + markdown(context, result.columns(), result.rows(), PREVIEW_ROWS);
        }
        return summary + "\n\n" + markdown(context, result.columns(), result.rows(),
                limits(context).getMaxLinesPerRead());
    }

    /// @param query The query.
    /// @param zone  The call's zone, which its expressions' dates are in.
    /// @return What the result says of the zone: nothing for a query without expressions, whose cells are as the
    /// table holds them; else e.g. " Its expressions' dates are in Australia/Canberra (+11:00)." with the zone's
    /// note, if any.
    static String zoneNote(final Query query, final ToolTimeZone zone) {
        if (!query.hasExpressions()) {
            return "";
        }
        return " Its expressions' dates are in " + zone.label() + "."
               + (zone.note() == null
                ? ""
                : " " + zone.note());
    }

    // Joins the other table to this one, then runs the query on the joined rows.
    private String runJoined(final ToolCallContext context,
                             final WorkspacePath path,
                             final Map<?, ?> join,
                             final Query query,
                             final WorkspacePath out,
                             final ToolTimeZone zone) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final String rightName = string(join.get(PATH_ARG));
        if (rightName == null) {
            throw new WorkspaceException("Give join.path: the table to join to this one.");
        }
        final WorkspacePath right = WorkspacePath.parse(rightName);
        refuseUnlessATable(right);
        final Spec spec = spec(join);
        final Joined joined = TableJoin.join(workspace.localFile(path), Format.forPath(path),
                workspace.localFile(right), Format.forPath(right), spec);
        final Path temp = workspace.tempFile();
        try {
            ToolTables.writeCsv(temp, joined.columns(), joined.rows());
            final Result result = TableEngine.run(temp, Format.CSV, query);
            // The file is checked again against the document a table came from when used: the left table's, or
            // the right's if only it came from one.
            final String leftSource = workspace.sourceDocUuid(path);
            final String source = leftSource != null
                    ? leftSource
                    : workspace.sourceDocUuid(right);
            final String summary = joinSummary(path, right, spec, joined) + " " + result.rowsMatched() + " of "
                                   + result.rowsRead() + " joined rows matched; the result has "
                                   + result.rows().size() + " row(s)." + zoneNote(query, zone);
            if (out != null) {
                writeTable(workspace, out, result, source);
                return "Wrote the result to " + out + ". " + summary + " The first rows:\n\n"
                       + markdown(context, result.columns(), result.rows(), PREVIEW_ROWS);
            }
            return summary + "\n\n" + markdown(context, result.columns(), result.rows(),
                    limits(context).getMaxLinesPerRead());
        } finally {
            ChatWorkspace.deleteQuietly(temp);
        }
    }

    // What was joined, and how many rows matched.
    private static String joinSummary(final WorkspacePath path,
                                      final WorkspacePath right,
                                      final Spec spec,
                                      final Joined joined) {
        final StringBuilder sb = new StringBuilder("Joined ").append(joined.leftRows()).append(" rows of ")
                .append(path).append(" with ").append(joined.rightRows()).append(" of ").append(right)
                .append(" on ").append(String.join(", ", spec.on()));
        if (!spec.rightOn().isEmpty()) {
            sb.append(" (").append(String.join(", ", spec.rightOn())).append(" there)");
        }
        if (spec.within() != null) {
            sb.append(", within ").append(spec.within().seconds()).append(" s")
                    .append(spec.within().after()
                            ? " after"
                            : "");
        }
        sb.append(": ").append(joined.leftMatched()).append(" of the ").append(joined.leftRows())
                .append(" rows matched")
                .append(spec.keep() == Keep.UNMATCHED
                        ? ", so " + joined.rows().size() + " are unmatched."
                        : ", making " + joined.rows().size() + " joined rows.");
        if (joined.untimed() > 0) {
            sb.append(" ").append(joined.untimed()).append(" row(s) had a matching key but a time that couldn't "
                                                           + "be read, so matched nothing.");
        }
        return sb.toString();
    }

    /// Reads join's how-to from its argument.
    ///
    /// @param join The join argument.
    /// @return How to join.
    /// @throws WorkspaceException if a part of it is missing or in a shape that can't be read.
    static Spec spec(final Map<?, ?> join) {
        final String keep = string(join.get("keep"));
        final Keep keepWhat;
        if (keep == null || "both".equalsIgnoreCase(keep.strip())) {
            keepWhat = Keep.BOTH;
        } else if ("unmatched".equalsIgnoreCase(keep.strip())) {
            keepWhat = Keep.UNMATCHED;
        } else {
            throw new WorkspaceException("join.keep must be both or unmatched, but was " + quote(keep) + ".");
        }
        final Object withinArg = loose(join.get("within"), "join.within", OBJECTS);
        Within within = null;
        if (withinArg instanceof final Map<?, ?> map) {
            final String column = string(map.get("column"));
            if (column == null || map.get("seconds") == null) {
                throw new WorkspaceException("join.within needs column (this table's time) and seconds.");
            }
            final long seconds;
            try {
                seconds = (long) Double.parseDouble(String.valueOf(map.get("seconds")));
            } catch (final NumberFormatException e) {
                throw new WorkspaceException("join.within.seconds must be a number of seconds.");
            }
            if (seconds < 0) {
                throw new WorkspaceException("join.within.seconds can't be negative.");
            }
            within = new Within(column, string(map.get("rightColumn")), seconds,
                    Boolean.TRUE.equals(map.get("after")) || "true".equals(string(map.get("after"))));
        } else if (withinArg != null) {
            throw new WorkspaceException("join.within must be an object, e.g. {\"column\": \"time\", "
                                         + "\"seconds\": 300}, but was " + quote(withinArg) + ".");
        }
        return new Spec(strings(join.get("on"), "join.on"), strings(join.get("rightOn"), "join.rightOn"),
                keepWhat, within);
    }

    // The join argument as an object, or null if there is none.
    private static Map<?, ?> joinArg(final Object value) {
        final Object loose = loose(value, JOIN_ARG, OBJECTS);
        if (loose == null) {
            return null;
        }
        if (loose instanceof final Map<?, ?> map) {
            return map;
        }
        throw new WorkspaceException("join must be an object, e.g. {\"path\": \"/outputs/b.csv\", \"on\": "
                                     + "[\"user\"]}, but was " + quote(loose) + ".");
    }

    /// The note for many rows of an Elastic Index grouped or totalled here, which esql_query would have done in
    /// Elasticsearch without reading the rows.
    ///
    /// @param sourceDocUuid The document the table came from, or null.
    /// @param query         The table query.
    /// @param rowsRead      How many rows it read.
    /// @return The note, starting with a space, or empty.
    public String esqlNote(final String sourceDocUuid, final Query query, final long rowsRead) {
        if (dataSourceAccess == null || sourceDocUuid == null || rowsRead < ESQL_NOTE_ROWS
            || (query.groupBy().isEmpty() && query.aggregates().isEmpty())) {
            return "";
        }
        return dataSourceAccess.find(sourceDocUuid).stream()
                .filter(docRef -> ElasticIndexDoc.TYPE.equals(docRef.getType()))
                .findFirst()
                .map(docRef -> " These " + rowsRead + " rows were read from " + DataSourceAccess.describe(docRef)
                               + " to be grouped or totalled here: esql_query would have done that in "
                               + "Elasticsearch, without reading the rows.")
                .orElse("");
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

    private void writeTable(final SessionWorkspace workspace, final WorkspacePath out, final Result result,
            final String source) {
        ToolTables.write(workspace, out, result.columns(), result.rows(), source);
    }

    private static List<String> strings(final Object value, final String name) {
        final Object loose = loose(value, name, COLUMN_NAMES);
        if (loose == null) {
            return List.of();
        }
        if (loose instanceof final List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (loose instanceof final String string) {
            return List.of(string);
        }
        throw new WorkspaceException(name + " must be a list of " + COLUMN_NAMES + ", but was " + quote(loose)
                                     + ".");
    }

    private static List<Condition> conditions(final Object value) {
        final List<Condition> conditions = new ArrayList<>();
        for (final Map<?, ?> map : maps(value, "where")) {
            final Object conditionValue = map.get("value");
            // in and not_in also take their values as a list, which keeps a value with a comma in it whole.
            conditions.add(conditionValue instanceof final List<?> list
                    ? new Condition(string(map.get("column")), string(map.get("op")), null,
                            list.stream().map(item -> Objects.requireNonNullElse(string(item), "")).toList())
                    : new Condition(string(map.get("column")), string(map.get("op")), string(conditionValue)));
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

    // Each item is an object, {"column": ..., "descending": ...}, or a column's name, "-" before it for largest
    // first.
    private static List<Order> orders(final Object value, final Supplier<Set<String>> resultColumns) {
        final List<Order> orders = new ArrayList<>();
        for (final Object item : items(value, "orderBy", "objects or column names")) {
            if (item instanceof final Map<?, ?> map) {
                orders.add(new Order(string(map.get("column")), Boolean.TRUE.equals(map.get("descending"))
                                                                || "true".equals(string(map.get("descending")))));
            } else if (item instanceof final String name) {
                orders.add(order(name.strip(), resultColumns));
            } else {
                throw new WorkspaceException("Each item of orderBy must be an object or a column's name, but one "
                                             + "was " + quote(item) + ".");
            }
        }
        return orders;
    }

    /// @param name          A column's name given as an item of orderBy.
    /// @param resultColumns The columns the result may have.
    /// @return Sorting by the column, smallest first, or largest first if the name starts with '-' (unless a
    /// column has that name, '-' and all).
    static Order order(final String name, final Supplier<Set<String>> resultColumns) {
        if (name.length() > 1 && name.startsWith("-") && !resultColumns.get().contains(name)) {
            return new Order(name.substring(1).strip(), true);
        }
        return new Order(name, false);
    }

    private static List<Map<?, ?>> maps(final Object value, final String name) {
        final List<Map<?, ?>> maps = new ArrayList<>();
        for (final Object item : items(value, name, OBJECTS)) {
            if (item instanceof final Map<?, ?> map) {
                maps.add(map);
            } else if (item == value && value instanceof final String text) {
                // Text that isn't JSON at all.
                throw new WorkspaceException(name + " was text that isn't a JSON list of " + OBJECTS + ": "
                                             + quote(text) + ".");
            } else {
                throw new WorkspaceException(name + " must be a list of " + OBJECTS + ", but one item was "
                                             + quote(item) + ".");
            }
        }
        return maps;
    }

    // The items of a list argument, as the model meant it (see loose): one item given instead of a list is a list
    // of that item.
    private static List<?> items(final Object value, final String name, final String what) {
        final Object loose = loose(value, name, what);
        if (loose == null) {
            return List.of();
        }
        if (loose instanceof final List<?> list) {
            return list;
        }
        return List.of(loose);
    }

    /// An argument as the model meant it. A JSON list or object given as text (seen: aggregates given as
    /// "[{\"as\": \"n\", \"function\": \"count\"}],", with a stray trailing comma) is read as that list or object;
    /// surrounding whitespace and one trailing comma are ignored. Any other value is returned as it is.
    ///
    /// @param value The argument's value.
    /// @param name  The argument's name.
    /// @param what  What the argument is a list of, for the error.
    /// @return The value meant.
    /// @throws WorkspaceException if the value is text that starts as JSON but isn't, quoting it.
    static Object loose(final Object value, final String name, final String what) {
        if (!(value instanceof final String text)) {
            return value;
        }
        final String json = withoutTrailingComma(text.strip());
        if (!json.startsWith("[") && !json.startsWith("{")) {
            return value;
        }
        try {
            return JsonUtil.readValue(json, Object.class);
        } catch (final RuntimeException e) {
            throw new WorkspaceException(name + " was text that isn't a JSON list of " + what + ": " + quote(text)
                                         + ".");
        }
    }

    private static String withoutTrailingComma(final String text) {
        return text.endsWith(",")
                ? text.substring(0, text.length() - 1).strip()
                : text;
    }

    // A value given, quoted (shortened if long) in an error, so the model can see what it sent.
    private static String quote(final Object value) {
        final String text = value instanceof String
                ? (String) value
                : JsonUtil.writeValueAsString(value, false);
        return "'" + (text.length() > MAX_QUOTED
                ? text.substring(0, MAX_QUOTED) + "…"
                : text) + "'";
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
