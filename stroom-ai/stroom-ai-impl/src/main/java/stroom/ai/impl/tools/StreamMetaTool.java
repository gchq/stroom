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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.data.store.api.AttributeMapFactory;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.shared.AppPermission;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// `stream_meta` - finds streams by feed, the pipeline that produced them, type, create time and status, and
/// writes a table of their attributes to the workspace, one row per stream (or per part, with the headers each
/// part was received with), or grouped by day, hour, feed, type or pipeline with counts, sums, minimums and
/// maximums.
///
/// Streams are found through [MetaService], which adds the user's feed permissions to the search itself (only
/// streams of feeds the user may view are found), so what the database holds needs nothing more. Attribute values
/// come from the database while it keeps them, and otherwise from the stream store, which also holds values the
/// database never has. Reading the stream store needs permission to view data, as the Data screen does, and the
/// store checks the user may view each stream's feed again when it opens it; without it, those values are left
/// out and the summary says why. Reading the stream store is capped per call.
class StreamMetaTool extends AbstractWorkspaceTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StreamMetaTool.class);

    static final String NAME = "stream_meta";
    static final String TYPE_ID = "AskStroomAi." + NAME;
    static final String FEEDS_ARG = "feeds";
    static final String PIPELINE_ARG = "pipeline";
    static final String FOLDER_ARG = "folder";
    static final String TYPE_ARG = "type";
    static final String FROM_ARG = "from";
    static final String TO_ARG = "to";
    static final String STATUS_ARG = "status";
    static final String ATTRIBUTES_ARG = "attributes";
    static final String PARTS_ARG = "parts";
    static final String GROUP_BY_ARG = "groupBy";
    static final String AGGREGATES_ARG = "aggregates";
    static final String LIMIT_ARG = "limit";
    static final String DEFAULT_OUT = "/outputs/stream_meta.csv";
    static final int DEFAULT_LIMIT = 1_000;
    static final int MAX_LIMIT = 10_000;
    /// The most parts of one stream read for their receipt meta.
    static final int MAX_PARTS_PER_STREAM = 100;
    /// The most attribute columns shown when none are asked for.
    static final int MAX_DEFAULT_ATTRIBUTES = 30;
    /// The most columns in a table of streams or parts.
    static final int MAX_COLUMNS = 60;
    static final int PREVIEW_ROWS = 10;

    static final String ID_COLUMN = "Id";
    static final String FEED_COLUMN = "Feed";
    static final String TYPE_COLUMN = "Type";
    static final String PIPELINE_COLUMN = "Pipeline";
    static final String STATUS_COLUMN = "Status";
    static final String CREATE_TIME_COLUMN = "Create Time";
    static final String PARENT_ID_COLUMN = "Parent Id";
    static final String PART_COLUMN = "Part";
    static final String COUNT_COLUMN = "count";
    static final List<String> CORE_COLUMNS = List.of(ID_COLUMN, FEED_COLUMN, TYPE_COLUMN, PIPELINE_COLUMN,
            STATUS_COLUMN, CREATE_TIME_COLUMN, PARENT_ID_COLUMN);
    static final List<String> GROUPS = List.of("day", "hour", "feed", "type", "pipeline");

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern AGGREGATE_PATTERN = Pattern.compile(
            "^\\s*(count|sum|min|max)\\s*(?:[(:]\\s*(.*?)\\s*\\)?)?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH':00Z'")
            .withZone(ZoneOffset.UTC);

    private final Provider<MetaService> metaServiceProvider;
    private final Provider<AttributeMapFactory> attributeMapFactoryProvider;
    private final Provider<Store> storeProvider;
    private final DocumentAccess documentAccess;
    private final AiAuditLog auditLog;

    @Inject
    StreamMetaTool(final ChatWorkspace workspace,
                   final Provider<MetaService> metaServiceProvider,
                   final Provider<AttributeMapFactory> attributeMapFactoryProvider,
                   final Provider<Store> storeProvider,
                   final DocumentAccess documentAccess,
                   final AiAuditLog auditLog) {
        super(workspace);
        this.metaServiceProvider = metaServiceProvider;
        this.attributeMapFactoryProvider = attributeMapFactoryProvider;
        this.storeProvider = storeProvider;
        this.documentAccess = documentAccess;
        this.auditLog = auditLog;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .unlockedBy(AppPermission.VIEW_DATA_PERMISSION)
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Finds streams (the data Stroom has received or produced) and writes a table of "
                                 + "their meta")
                        .chatOnly(" to the workspace, for the table tool")
                        .general(". Give feeds and/or a pipeline (the streams it produced), or a folder (the streams "
                                 + "of the feeds in it and produced by its pipelines), and optionally type, from/to "
                                 + "(create time) and status. Each row is a stream, with the attributes asked for "
                                 + "(e.g. 'Raw Size', 'Read Count', or a header the data arrived with such as "
                                 + "'RemoteHost'); give parts for a row per part with the headers each part was "
                                 + "received with. Give groupBy (day, hour, feed, type, pipeline) with aggregates "
                                 + "(e.g. ['count', 'sum(Raw Size)', 'max(Raw Size)']) for totals, e.g. how much a "
                                 + "feed received each day. Values come from the database while it keeps them, else "
                                 + "from the stream store (only for a limited number of streams per call); the "
                                 + "result says how many came from each and how many weren't read.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addProperty(FEEDS_ARG, stringArraySchema("Feed names or UUIDs."))
                        .addStringProperty(PIPELINE_ARG, "A pipeline's UUID: the streams it produced.")
                        .addStringProperty(FOLDER_ARG, "A folder's UUID or Explorer path, e.g. "
                                                       + "'/Security/Firewalls': the streams of the feeds in it "
                                                       + "(at any depth) and produced by its pipelines.")
                        .addStringProperty(TYPE_ARG, "A stream type, e.g. 'Raw Events' or 'Events'.")
                        .addStringProperty(FROM_ARG, "The earliest create time: an ISO 8601 date or date and "
                                                     + "time (UTC), or a period back from now, e.g. 'P7D'.")
                        .addStringProperty(TO_ARG, "The create time to stop before, in the same forms.")
                        .addEnumProperty(STATUS_ARG, List.of(Status.UNLOCKED.getDisplayValue(),
                                        Status.LOCKED.getDisplayValue(), Status.DELETED.getDisplayValue()),
                                "The streams' status (default Unlocked).")
                        .addProperty(ATTRIBUTES_ARG, stringArraySchema("The attributes to return, e.g. "
                                                                          + "['Raw Size', 'RemoteHost']. By "
                                                                          + "default, those the database "
                                                                          + "holds."))
                        .addBooleanProperty(PARTS_ARG, "True for a row per part, with the headers (receipt "
                                                       + "meta) each part was received with.")
                        .addProperty(GROUP_BY_ARG, stringArraySchema("Group the rows by any of: "
                                                                        + String.join(", ", GROUPS) + "."))
                        .addProperty(AGGREGATES_ARG, stringArraySchema("For each group (or all the rows): "
                                                                          + "count, or sum, min or max of an "
                                                                          + "attribute, e.g. 'sum(Raw Size)'."))
                        .addIntegerProperty(LIMIT_ARG, "The most streams to find (default " + DEFAULT_LIMIT
                                                       + ", at most " + MAX_LIMIT + ").")
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("A .csv file in /outputs/ to write the table to (default " + DEFAULT_OUT
                                          + ").")
                                .build())
                        .build())
                .build();
    }

    @Override
    protected Duration timeLimit(final ToolCallContext context) {
        // Reading the stream store can take longer than a workspace step.
        return limits(context).getQueryTimeLimit().getDuration();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final long startNanos = System.nanoTime();
        final WorkspacePath requestedOut = outArg(request);
        final WorkspacePath out = requestedOut != null
                ? requestedOut
                : WorkspacePath.parse(DEFAULT_OUT);
        workspace.checkWritable(out);

        final Criteria criteria = criteria(request);
        final List<String> groupBy = groupBy(request);
        final List<Aggregate> aggregates = aggregates(request, groupBy);
        final boolean parts = booleanArg(request, PARTS_ARG, false);
        final Integer limitArg = intArg(request, LIMIT_ARG);
        final int limit = limitArg == null
                ? DEFAULT_LIMIT
                : Math.max(1, Math.min(limitArg, MAX_LIMIT));

        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(criteria.expression());
        // One more than the limit, to tell if there are more.
        findMetaCriteria.setPageRequest(new PageRequest(0, limit + 1));
        final ResultPage<MetaRow> page = metaServiceProvider.get().findRows(findMetaCriteria);
        final List<MetaRow> found = NullSafe.list(NullSafe.get(page, ResultPage::getValues));
        final boolean more = found.size() > limit;
        final List<MetaRow> metaRows = found.subList(0, Math.min(limit, found.size()));

        final List<String> attributes = attributes(request, metaRows, aggregates);
        // Only with View Data, checked once, failing closed.
        final Reader reader = new Reader(documentAccess.canViewData(), limits(context).getMaxStreamStoreReads(),
                startNanos, timeLimit(context).toNanos() / 2);
        final List<Map<String, String>> records = new ArrayList<>();
        for (final MetaRow metaRow : metaRows) {
            ToolDeadline.check();
            records.addAll(records(metaRow, attributes, parts, reader));
        }

        final List<String> columns;
        final List<List<String>> rows;
        if (!groupBy.isEmpty() || !aggregates.isEmpty()) {
            columns = new ArrayList<>(groupBy);
            aggregates.forEach(aggregate -> columns.add(aggregate.column()));
            rows = group(records, groupBy, aggregates);
        } else {
            final Set<String> allColumns = new LinkedHashSet<>(CORE_COLUMNS);
            if (parts) {
                allColumns.add(PART_COLUMN);
            }
            allColumns.addAll(attributes);
            // With parts and no attributes asked for, each part's own headers.
            records.forEach(record -> record.keySet().stream()
                    .filter(key -> allColumns.size() < MAX_COLUMNS)
                    .forEach(allColumns::add));
            columns = List.copyOf(allColumns);
            rows = records.stream()
                    .map(record -> columns.stream().map(column -> record.getOrDefault(column, "")).toList())
                    .toList();
        }
        ToolTables.writeCsv(workspace, out, columns, rows, criteria.sourceDocUuid());
        logSearch(context, criteria, metaRows, reader);
        return ToolResult.text(summary(out, metaRows.size(), more, rows, columns, reader, parts));
    }

    /// @return The search, from the arguments.
    private Criteria criteria(final ToolCall request) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.AND);
        final List<String> description = new ArrayList<>();
        String sourceDocUuid = null;

        final List<String> feeds = stringListArg(request, FEEDS_ARG);
        final String pipeline = stringArg(request, PIPELINE_ARG);
        final String folder = stringArg(request, FOLDER_ARG);
        if (feeds.isEmpty() && pipeline == null && folder == null) {
            throw new WorkspaceException("Give feeds, a pipeline or a folder.");
        }
        if (folder != null) {
            final DocRef folderRef = documentAccess.findFolder(folder)
                    .orElseThrow(() -> new WorkspaceException("No folder was found with that UUID or path, or you "
                                                              + "don't have permission to view it."));
            if (!ExplorerConstants.SYSTEM_DOC_REF.equals(folderRef)) {
                builder.addOperator(ExpressionOperator.builder().op(Op.OR)
                        .addDocRefTerm(MetaFields.FEED, Condition.IN_FOLDER, folderRef)
                        .addDocRefTerm(MetaFields.PIPELINE, Condition.IN_FOLDER, folderRef)
                        .build());
                description.add("feed or pipeline in folder '" + folderRef.getName() + "'");
                sourceDocUuid = folderRef.getUuid();
            }
        }
        if (!feeds.isEmpty()) {
            final ExpressionOperator.Builder feedTerms = ExpressionOperator.builder().op(Op.OR);
            final List<String> names = new ArrayList<>();
            for (final String feed : feeds) {
                final String name = feedName(feed);
                feedTerms.addTextTerm(MetaFields.FEED, Condition.EQUALS, name);
                names.add(name);
            }
            builder.addOperator(feedTerms.build());
            description.add("feed is " + String.join(" or ", names));
            if (feeds.size() == 1 && UUID_PATTERN.matcher(feeds.getFirst()).matches()) {
                sourceDocUuid = feeds.getFirst();
            }
        }
        if (pipeline != null) {
            final DocRef pipelineRef = documentAccess.findDocRef(pipeline)
                    .filter(docRef -> PipelineDoc.TYPE.equals(docRef.getType()))
                    .orElseThrow(() -> new WorkspaceException("No pipeline was found with that UUID, or you don't "
                                                              + "have permission to view it."));
            builder.addDocRefTerm(MetaFields.PIPELINE, Condition.IS_DOC_REF, pipelineRef);
            description.add("produced by pipeline '" + pipelineRef.getName() + "'");
            if (sourceDocUuid == null) {
                sourceDocUuid = pipelineRef.getUuid();
            }
        }
        final String type = stringArg(request, TYPE_ARG);
        if (type != null) {
            builder.addTextTerm(MetaFields.TYPE, Condition.EQUALS, type);
            description.add("type is " + type);
        }
        final String from = stringArg(request, FROM_ARG);
        if (from != null) {
            final String date = DateUtil.createNormalDateTimeString(time(from, FROM_ARG));
            builder.addDateTerm(MetaFields.CREATE_TIME, Condition.GREATER_THAN_OR_EQUAL_TO, date);
            description.add("created from " + date);
        }
        final String to = stringArg(request, TO_ARG);
        if (to != null) {
            final String date = DateUtil.createNormalDateTimeString(time(to, TO_ARG));
            builder.addDateTerm(MetaFields.CREATE_TIME, Condition.LESS_THAN, date);
            description.add("created before " + date);
        }
        final Status status = status(stringArg(request, STATUS_ARG));
        builder.addTextTerm(MetaFields.STATUS, Condition.EQUALS, status.getDisplayValue());
        description.add("status is " + status.getDisplayValue());
        return new Criteria(builder.build(), String.join(", ", description), sourceDocUuid);
    }

    /// @return The name of a feed given by name or UUID.
    /// @throws WorkspaceException for a UUID of a feed the user can't view, or that doesn't exist.
    private String feedName(final String feed) {
        if (!UUID_PATTERN.matcher(feed).matches()) {
            // By name, MetaService only finds streams of feeds the user may view.
            return feed;
        }
        return documentAccess.findDocRef(feed)
                .filter(docRef -> FeedDoc.TYPE.equals(docRef.getType()))
                .map(DocRef::getName)
                .orElseThrow(() -> new WorkspaceException("No feed was found with the UUID " + feed + ", or you "
                                                          + "don't have permission to view it."));
    }

    /// @param value An ISO 8601 date, date and time, or period back from now.
    /// @return The time, in milliseconds since the epoch.
    static long time(final String value, final String name) {
        try {
            if (value.startsWith("P") || value.startsWith("p")) {
                final String upper = value.toUpperCase(Locale.ROOT);
                if (upper.contains("T")) {
                    return Instant.now().minus(Duration.parse(upper)).toEpochMilli();
                }
                return ZonedDateTime.now(ZoneOffset.UTC).minus(Period.parse(upper)).toInstant().toEpochMilli();
            }
            if (value.length() == 10) {
                return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            }
            return DateUtil.parseUnknownString(value);
        } catch (final DateTimeParseException | IllegalArgumentException e) {
            throw new WorkspaceException("'" + value + "' isn't a time " + name + " can be: give an ISO 8601 date "
                                         + "(e.g. 2026-10-01), date and time (e.g. 2026-10-01T12:00:00Z) or "
                                         + "period back from now (e.g. P7D or PT6H).");
        }
    }

    private static Status status(final String value) {
        if (value == null) {
            return Status.UNLOCKED;
        }
        for (final Status status : Status.values()) {
            if (status.getDisplayValue().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new WorkspaceException("The status must be Unlocked, Locked or Deleted.");
    }

    private static List<String> groupBy(final ToolCall request) {
        final List<String> groupBy = new ArrayList<>();
        for (final String group : stringListArg(request, GROUP_BY_ARG)) {
            final String lower = group.toLowerCase(Locale.ROOT);
            if (!GROUPS.contains(lower)) {
                throw new WorkspaceException("Can't group by '" + group + "'. Group by any of: "
                                             + String.join(", ", GROUPS) + ".");
            }
            groupBy.add(lower);
        }
        return groupBy;
    }

    /// @return The aggregates asked for, or just a count if the rows are grouped and none were.
    static List<Aggregate> aggregates(final ToolCall request, final List<String> groupBy) {
        final List<Aggregate> aggregates = new ArrayList<>();
        for (final String item : stringListArg(request, AGGREGATES_ARG)) {
            final Matcher matcher = AGGREGATE_PATTERN.matcher(item);
            if (!matcher.matches()) {
                throw new WorkspaceException("'" + item + "' isn't an aggregate. Give count, or sum, min or max "
                                             + "of an attribute, e.g. 'sum(Raw Size)'.");
            }
            final String function = matcher.group(1).toLowerCase(Locale.ROOT);
            final String attribute = NullSafe.isBlankString(matcher.group(2))
                    ? null
                    : matcher.group(2).strip();
            if (!COUNT_COLUMN.equals(function) && attribute == null) {
                throw new WorkspaceException("Give the attribute to " + function + ", e.g. '" + function
                                             + "(Raw Size)'.");
            }
            aggregates.add(new Aggregate(function, COUNT_COLUMN.equals(function)
                    ? null
                    : attribute));
        }
        if (aggregates.isEmpty() && !groupBy.isEmpty()) {
            aggregates.add(new Aggregate(COUNT_COLUMN, null));
        }
        return aggregates;
    }

    /// @return The attributes to read: those asked for, else those the database holds for the streams found,
    /// with any the aggregates need.
    private static List<String> attributes(final ToolCall request,
                                           final List<MetaRow> metaRows,
                                           final List<Aggregate> aggregates) {
        final Set<String> attributes = new LinkedHashSet<>(stringListArg(request, ATTRIBUTES_ARG));
        if (attributes.isEmpty() && aggregates.isEmpty()) {
            final Set<String> held = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            metaRows.forEach(metaRow -> held.addAll(NullSafe.map(metaRow.getAttributes()).keySet()));
            held.stream().limit(MAX_DEFAULT_ATTRIBUTES).forEach(attributes::add);
        }
        aggregates.stream()
                .map(Aggregate::attribute)
                .filter(attribute -> attribute != null && !isCore(attribute))
                .forEach(attributes::add);
        attributes.removeIf(StreamMetaTool::isCore);
        return List.copyOf(attributes);
    }

    private static boolean isCore(final String attribute) {
        return CORE_COLUMNS.stream().anyMatch(core -> core.equalsIgnoreCase(attribute))
               || PART_COLUMN.equalsIgnoreCase(attribute);
    }

    /// @return The records for a stream: one, or one per part, each with its core values and attributes.
    private List<Map<String, String>> records(final MetaRow metaRow,
                                              final List<String> attributes,
                                              final boolean parts,
                                              final Reader reader) {
        final Meta meta = metaRow.getMeta();
        final Map<String, String> core = new LinkedHashMap<>();
        core.put(ID_COLUMN, String.valueOf(meta.getId()));
        core.put(FEED_COLUMN, NullSafe.string(meta.getFeedName()));
        core.put(TYPE_COLUMN, NullSafe.string(meta.getTypeName()));
        core.put(PIPELINE_COLUMN, NullSafe.getOrElse(metaRow.getPipeline(), DocRef::getName,
                NullSafe.string(meta.getPipelineUuid())));
        core.put(STATUS_COLUMN, NullSafe.getOrElse(meta.getStatus(), Status::getDisplayValue, ""));
        core.put(CREATE_TIME_COLUMN, DateUtil.createNormalDateTimeString(meta.getCreateMs()));
        core.put(PARENT_ID_COLUMN, NullSafe.getOrElse(meta.getParentMetaId(), String::valueOf, ""));
        final Map<String, String> database = caseInsensitive(metaRow.getAttributes());

        final StreamValues stream = new StreamValues(meta.getId(), database, reader);
        if (!parts) {
            final Map<String, String> record = new LinkedHashMap<>(core);
            for (final String attribute : attributes) {
                record.put(attribute, stream.value(attribute));
            }
            return List.of(record);
        }
        final List<Map<String, String>> partRecords = reader.partAttributes(meta.getId());
        if (partRecords == null) {
            // Not read, so one row for the stream, from what the database has.
            final Map<String, String> record = new LinkedHashMap<>(core);
            record.put(PART_COLUMN, "");
            for (final String attribute : attributes) {
                record.put(attribute, stream.value(attribute));
            }
            return List.of(record);
        }
        final List<Map<String, String>> records = new ArrayList<>();
        for (int i = 0; i < partRecords.size(); i++) {
            final Map<String, String> partAttributes = partRecords.get(i);
            final Map<String, String> record = new LinkedHashMap<>(core);
            record.put(PART_COLUMN, String.valueOf(i + 1));
            final List<String> columns = attributes.isEmpty()
                    ? List.copyOf(partAttributes.keySet())
                    : attributes;
            for (final String attribute : columns) {
                final String value = partAttributes.get(attribute);
                if (value != null) {
                    reader.fromStore++;
                    record.put(attribute, value);
                } else {
                    record.put(attribute, stream.value(attribute));
                }
            }
            records.add(record);
        }
        return records;
    }

    private static Map<String, String> caseInsensitive(final Map<String, String> map) {
        final Map<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        NullSafe.map(map).forEach((key, value) -> {
            if (key != null && value != null) {
                result.put(key, value);
            }
        });
        return result;
    }

    /// @return The records grouped, with each group's aggregates, in order of the group values.
    static List<List<String>> group(final List<Map<String, String>> records,
                                    final List<String> groupBy,
                                    final List<Aggregate> aggregates) {
        final Map<List<String>, List<Map<String, String>>> groups = new TreeMap<>(StreamMetaTool::compareKeys);
        for (final Map<String, String> record : records) {
            final List<String> key = groupBy.stream().map(group -> groupValue(record, group)).toList();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
        }
        final List<List<String>> rows = new ArrayList<>();
        groups.forEach((key, members) -> {
            final List<String> row = new ArrayList<>(key);
            for (final Aggregate aggregate : aggregates) {
                row.add(aggregate.apply(members));
            }
            rows.add(row);
        });
        return rows;
    }

    private static int compareKeys(final List<String> a, final List<String> b) {
        for (int i = 0; i < a.size(); i++) {
            final int compared = a.get(i).compareTo(b.get(i));
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private static String groupValue(final Map<String, String> record, final String group) {
        return switch (group) {
            case "day" -> truncateTime(record.get(CREATE_TIME_COLUMN), DAY);
            case "hour" -> truncateTime(record.get(CREATE_TIME_COLUMN), HOUR);
            case "feed" -> record.getOrDefault(FEED_COLUMN, "");
            case "type" -> record.getOrDefault(TYPE_COLUMN, "");
            case "pipeline" -> record.getOrDefault(PIPELINE_COLUMN, "");
            default -> "";
        };
    }

    private static String truncateTime(final String time, final DateTimeFormatter formatter) {
        try {
            return formatter.format(Instant.ofEpochMilli(DateUtil.parseNormalDateTimeString(time)));
        } catch (final RuntimeException e) {
            return "";
        }
    }

    private void logSearch(final ToolCallContext context,
                           final Criteria criteria,
                           final List<MetaRow> metaRows,
                           final Reader reader) {
        final List<Long> streams = metaRows.stream()
                .map(metaRow -> metaRow.getMeta().getId())
                .toList();
        auditLog.streamsSearched(context.session(),
                criteria.description(),
                streams,
                Map.of("StreamStoreReads", String.valueOf(reader.reads)),
                "Stream meta found by the " + NAME + " tool");
    }

    private String summary(final WorkspacePath out,
                           final int streams,
                           final boolean more,
                           final List<List<String>> rows,
                           final List<String> columns,
                           final Reader reader,
                           final boolean parts) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(streams).append(" stream(s)");
        if (more) {
            sb.append(" (there are more; narrow the search, e.g. by time, or give a larger limit)");
        }
        sb.append(". Wrote ").append(rows.size()).append(" row(s) to ").append(out)
                .append("; use the table tool on it to filter, group or sort.");
        sb.append(" Attribute values: ").append(reader.fromDatabase).append(" from the database, ")
                .append(reader.fromStore).append(" from the stream store (").append(reader.reads)
                .append(" stream(s) read).");
        if (reader.skipped > 0) {
            sb.append(' ').append(reader.skipped).append(" value(s) weren't read from the stream store because ");
            if (!reader.canViewData) {
                sb.append("you don't have permission to view data (only values the database holds are given)");
            } else {
                sb.append(reader.outOfTime
                                ? "the call ran out of time"
                                : "of the limit of " + reader.maxReads + " stream store reads per call")
                        .append("; narrow the search to read them");
            }
            sb.append('.');
        }
        if (parts && !reader.canViewData) {
            sb.append(" The headers each part was received with are only in the stream store, which you don't have "
                      + "permission to view data from, so there is a row per stream instead.");
        }
        if (reader.notFound > 0) {
            sb.append(' ').append(reader.notFound).append(" value(s) weren't held for their stream.");
        }
        if (reader.unreadable > 0) {
            sb.append(' ').append(reader.unreadable).append(" stream(s) couldn't be read from the stream store.");
        }
        if (parts && reader.partsCut > 0) {
            sb.append(" Only the first ").append(MAX_PARTS_PER_STREAM).append(" parts of ").append(reader.partsCut)
                    .append(" stream(s) were read.");
        }
        if (!rows.isEmpty()) {
            sb.append(" The first rows:\n\n").append(ToolTables.preview(columns, rows, PREVIEW_ROWS));
        }
        return sb.toString();
    }

    @Override
    public boolean readsUntrustedContent() {
        // Headers and attributes are given by whoever sent the data.
        return true;
    }

    /// @param expression    The search.
    /// @param description   The search, in words, for the audit log.
    /// @param sourceDocUuid The feed or pipeline the table comes from, if just one, or null.
    private record Criteria(ExpressionOperator expression, String description, String sourceDocUuid) {

    }

    /// One of the aggregates asked for.
    ///
    /// @param function  count, sum, min or max.
    /// @param attribute The attribute it works on, or null for count.
    record Aggregate(String function, String attribute) {

        /// @return The heading of the aggregate's column, e.g. `sum(Raw Size)`.
        String column() {
            return attribute == null
                    ? function
                    : function + "(" + attribute + ")";
        }

        /// @param records The records of one group.
        /// @return The aggregate of the group: the count, or the sum, minimum or maximum of the attribute's
        /// numeric values, or nothing if it has none.
        String apply(final List<Map<String, String>> records) {
            if (COUNT_COLUMN.equals(function)) {
                return String.valueOf(records.size());
            }
            BigDecimal result = null;
            for (final Map<String, String> record : records) {
                final BigDecimal value = number(valueOf(record));
                if (value != null) {
                    if (result == null) {
                        result = value;
                    } else {
                        result = switch (function) {
                            case "sum" -> result.add(value);
                            case "min" -> result.min(value);
                            case "max" -> result.max(value);
                            default -> result;
                        };
                    }
                }
            }
            return result == null
                    ? ""
                    : result.stripTrailingZeros().toPlainString();
        }

        private String valueOf(final Map<String, String> record) {
            for (final Map.Entry<String, String> entry : record.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(attribute)) {
                    return entry.getValue();
                }
            }
            return null;
        }

        private static BigDecimal number(final String value) {
            if (NullSafe.isBlankString(value)) {
                return null;
            }
            try {
                return new BigDecimal(value.strip().replace(",", ""));
            } catch (final NumberFormatException e) {
                return null;
            }
        }
    }

    /// The values of one stream: from the database if it holds them, else read from the stream store once.
    private final class StreamValues {

        private final long id;
        private final Map<String, String> database;
        private final Reader reader;
        private Map<String, String> store;
        private boolean storeTried;

        private StreamValues(final long id, final Map<String, String> database, final Reader reader) {
            this.id = id;
            this.database = database;
            this.reader = reader;
        }

        private String value(final String attribute) {
            final String fromDatabase = database.get(attribute);
            if (fromDatabase != null) {
                reader.fromDatabase++;
                return fromDatabase;
            }
            if (!storeTried) {
                storeTried = true;
                store = reader.streamAttributes(id);
            }
            if (store == null) {
                reader.skipped++;
                return "";
            }
            final String fromStore = store.get(attribute);
            if (fromStore == null) {
                reader.notFound++;
                return "";
            }
            reader.fromStore++;
            return fromStore;
        }
    }

    /// Reads from the stream store, only with View Data, within the cap on reads and half the call's time, and
    /// counts where values came from.
    private final class Reader {

        private final boolean canViewData;
        private final int maxReads;
        private final long startNanos;
        private final long budgetNanos;
        private final Map<Long, Map<String, String>> streamAttributes = new LinkedHashMap<>();
        private int reads;
        private int fromDatabase;
        private int fromStore;
        private int skipped;
        private int notFound;
        private int unreadable;
        private int partsCut;
        private boolean outOfTime;

        private Reader(final boolean canViewData, final int maxReads, final long startNanos, final long budgetNanos) {
            this.canViewData = canViewData;
            this.maxReads = maxReads;
            this.startNanos = startNanos;
            this.budgetNanos = budgetNanos;
        }

        private boolean mayRead() {
            if (!canViewData) {
                return false;
            }
            if (System.nanoTime() - startNanos > budgetNanos) {
                outOfTime = true;
                return false;
            }
            return reads < maxReads;
        }

        /// @return The stream's own attributes from the stream store, or null if they weren't read.
        private Map<String, String> streamAttributes(final long id) {
            if (streamAttributes.containsKey(id)) {
                return streamAttributes.get(id);
            }
            if (!mayRead()) {
                return null;
            }
            reads++;
            Map<String, String> attributes;
            try {
                attributes = caseInsensitive(attributeMapFactoryProvider.get().getAttributes(id));
            } catch (final ToolTimeoutException e) {
                throw e;
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to read the attributes of stream " + id + ": " + e.getMessage(), e);
                unreadable++;
                attributes = null;
            }
            streamAttributes.put(id, attributes);
            return attributes;
        }

        /// @return The receipt meta of each of the stream's parts, or null if it wasn't read.
        private List<Map<String, String>> partAttributes(final long id) {
            if (!mayRead()) {
                return null;
            }
            reads++;
            try {
                final long count;
                try (final Source source = storeProvider.get().openSource(id)) {
                    count = source.count();
                }
                if (count > MAX_PARTS_PER_STREAM) {
                    partsCut++;
                }
                final List<Map<String, String>> parts = new ArrayList<>();
                for (long part = 0; part < Math.min(count, MAX_PARTS_PER_STREAM); part++) {
                    ToolDeadline.check();
                    parts.add(caseInsensitive(attributeMapFactoryProvider.get().getAttributeMapForPart(id, part)));
                }
                return parts;
            } catch (final ToolTimeoutException e) {
                throw e;
            } catch (final IOException | RuntimeException e) {
                LOGGER.debug(() -> "Unable to read the parts of stream " + id + ": " + e.getMessage(), e);
                unreadable++;
                return null;
            }
        }
    }
}
