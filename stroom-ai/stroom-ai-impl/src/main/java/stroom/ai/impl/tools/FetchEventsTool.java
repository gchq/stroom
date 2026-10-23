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
import stroom.ai.impl.tools.ToolSchema.IntegerSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.EventsFile;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.TableEngine;
import stroom.ai.impl.workspace.TableEngine.Format;
import stroom.ai.impl.workspace.TableEngine.Query;
import stroom.ai.impl.workspace.TableEngine.Result;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.meta.shared.Meta;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Fetches the source events behind the rows of a table, e.g. an attached dashboard table, into one XML file in the
/// workspace (see [EventsFile]), for search_events to search by XPath or text; or reads records from one stream
/// (raw input, Events XML, or a child part such as the receipt headers), as the Stroom MCP server's read_stream
/// did, whole records within a limit of characters, with where to read on.
///
/// Events are read as the user: only streams of feeds they may see are read, and they need permission to view data.
/// What is read is data from anywhere, so the run counts as having read untrusted content.
class FetchEventsTool extends AbstractWorkspaceTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FetchEventsTool.class);

    static final String NAME = "fetch_events";
    static final String LIMIT_ARG = "limit";
    static final String STREAM_ID_COLUMN = "StreamId";
    static final String EVENT_ID_COLUMN = "EventId";
    static final String DEFAULT_OUT = "/outputs/events.xml";
    static final String STREAM_ID_ARG = "streamId";
    static final String FIRST_RECORD_ARG = "firstRecord";
    static final String RECORD_COUNT_ARG = "recordCount";
    static final String CHILD_TYPE_ARG = "childType";
    static final String MAX_CHARS_ARG = "maxChars";
    static final int DEFAULT_RECORD_COUNT = 5;
    static final int MAX_RECORD_COUNT = 100;
    /// Characters of record data returned by default.
    static final int DEFAULT_MAX_CHARS = 60_000;
    static final int MIN_MAX_CHARS = 1_000;
    static final int MAX_MAX_CHARS = 200_000;

    private final Provider<Store> storeProvider;
    private final Provider<SecurityContext> securityContextProvider;
    private final AiAuditLog auditLog;
    private final StreamReader streamReader;

    @Inject
    FetchEventsTool(final ChatWorkspace workspace,
                    final Provider<Store> storeProvider,
                    final Provider<SecurityContext> securityContextProvider,
                    final AiAuditLog auditLog,
                    final StreamReader streamReader) {
        super(workspace);
        this.storeProvider = storeProvider;
        this.securityContextProvider = securityContextProvider;
        this.auditLog = auditLog;
        this.streamReader = streamReader;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.VIEW_DATA_PERMISSION)
                .build();
    }

    @Override
    public boolean usesRunSlot() {
        // It reads many streams.
        return true;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.SEARCH;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Fetches the source events behind the rows of a table with StreamId and EventId "
                                 + "columns")
                        .chatOnly(" (an attached dashboard or StroomQL table that isn't grouped, or a CSV made "
                                  + "from one with the table tool's out, to pick the rows)")
                        .general(" into one XML file: each event in a <stroom-event streamId=\"..\" "
                                 + "eventId=\"..\"> element.")
                        .chatOnly(" Then use search_events to get values from each event by XPath, or find text "
                                  + "in them, as a table.")
                        .general(" Only events the user may see are fetched, up to a limit an administrator sets; "
                                 + "the result says if any were left out. Or give streamId instead to read records "
                                 + "from a stream: raw input, Events XML, or a child part such as the receipt "
                                 + "headers (childType 'Meta Data'). Whole records within a limit of characters, "
                                 + "not of records: when fewer fit than asked, next_first_record says where to read "
                                 + "on.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PATH_ARG, ToolDescription.builder()
                                .general("The table")
                                .chatOnly(", e.g. /attachments/12.md")
                                .general(".")
                                .build())
                        .addIntegerProperty(LIMIT_ARG, "The most events to fetch, from the first rows.")
                        .addProperty(STREAM_ID_ARG, new IntegerSchema(ToolDescription.builder()
                                .general("Stream (meta) id to read records from")
                                .chatOnly(", e.g. from stream_meta")
                                .general(", in place of a table.")
                                .build()))
                        .addIntegerProperty(FIRST_RECORD_ARG, "With streamId: zero-based index of the first "
                                                              + "record (default 0).")
                        .addIntegerProperty(RECORD_COUNT_ARG, "With streamId: how many records (default "
                                                              + DEFAULT_RECORD_COUNT + ", at most "
                                                              + MAX_RECORD_COUNT + ").")
                        .addStringProperty(CHILD_TYPE_ARG, "With streamId: read a child part instead, e.g. 'Meta "
                                                           + "Data' (receipt headers) or 'Context'.")
                        .addIntegerProperty(MAX_CHARS_ARG, "With streamId: characters of record data to return: "
                                                           + "leave the default (60,000) unless the user asks for "
                                                           + "more, for long records; up to 200,000.")
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("The file in /outputs/ to write the events to (default " + DEFAULT_OUT
                                          + "). With streamId, the records are written there, one after "
                                          + "another, instead of being returned (none is written without it).")
                                .build())
                        .build())
                .build();
    }

    @Override
    protected Duration timeLimit(final ToolCallContext context) {
        // Reading many streams can take longer than a workspace step.
        return limits(context).getQueryTimeLimit().getDuration();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        if (stringArg(request, STREAM_ID_ARG) != null) {
            return readStream(request, context);
        }
        if (stringArg(request, PATH_ARG) == null) {
            throw new WorkspaceException("Give path (a table with StreamId and EventId columns), or streamId to "
                                         + "read records from one stream.");
        }
        final SessionWorkspace workspace = sessionWorkspace(context);
        final WorkspacePath path = pathArg(request, PATH_ARG);
        final WorkspacePath requestedOut = outArg(request);
        final WorkspacePath out = requestedOut != null
                ? requestedOut
                : WorkspacePath.parse(DEFAULT_OUT);
        workspace.checkWritable(out);
        if (!canViewData()) {
            throw new WorkspaceException("You don't have permission to view data, so events can't be fetched.");
        }
        final int maxEvents = limits(context).getMaxEventsPerFetch();
        final Integer limitArg = intArg(request, LIMIT_ARG);
        final int limit = limitArg == null
                ? maxEvents
                : Math.max(1, Math.min(limitArg, maxEvents));
        final Ids ids = readIds(workspace.localFile(path), Format.forPath(path), limit);

        final Counts counts = new Counts();
        final Path temp = workspace.tempFile();
        try {
            try (final Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                writer.write(EventsFile.start());
                for (final Map.Entry<Long, List<Long>> stream : ids.byStream().entrySet()) {
                    writeStream(writer, stream.getKey(), stream.getValue(), counts, context);
                }
                writer.write(EventsFile.end());
            }
            if (counts.written > 0) {
                workspace.write(out, temp, workspace.sourceDocUuid(path));
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            ChatWorkspace.deleteQuietly(temp);
            logView(context, counts);
        }
        final String coverage = CurrentResultsTool.coverage(workspace, path, ids.count() + ids.skipped())
                .orElse(null);
        return ToolResult.text(summary(out, ids, counts, coverage));
    }

    // --------------------------------------------------------------------------------
    // Reading one stream.
    // --------------------------------------------------------------------------------

    /// Reads records from one stream, as the Stroom MCP server's read_stream did.
    private ToolResult readStream(final ToolCall request, final ToolCallContext context) {
        final long streamId = longArg(request, STREAM_ID_ARG);
        if (!canViewData()) {
            throw new WorkspaceException("You don't have permission to view data, so stream " + streamId
                                         + " can't be read.");
        }
        final int firstRecord = Math.max(0, Objects.requireNonNullElse(intArg(request, FIRST_RECORD_ARG), 0));
        final int recordCount = Math.max(1, Math.min(MAX_RECORD_COUNT,
                Objects.requireNonNullElse(intArg(request, RECORD_COUNT_ARG), DEFAULT_RECORD_COUNT)));
        final String childType = stringArg(request, CHILD_TYPE_ARG);
        // Records written to a file take nothing from the conversation, so as many are read as may be.
        final WorkspacePath out = outArg(request);
        final SessionWorkspace workspace = out != null
                ? sessionWorkspace(context)
                : null;
        if (workspace != null) {
            workspace.checkWritable(out);
        }
        final Integer maxChars = intArg(request, MAX_CHARS_ARG);
        final int limit = maxChars == null
                ? (out != null
                        ? MAX_MAX_CHARS
                        : DEFAULT_MAX_CHARS)
                : Math.max(MIN_MAX_CHARS, Math.min(MAX_MAX_CHARS, maxChars));
        final Map<String, Object> result;
        try {
            result = readRecords(streamId, firstRecord, recordCount, childType, limit);
        } catch (final StreamReader.StreamReadException e) {
            throw new WorkspaceException(e.getMessage());
        } finally {
            auditLog.streamsViewed(context.session(), List.of(streamId), "Read records of stream " + streamId
                                                                         + " with the " + NAME + " tool");
        }
        if (workspace != null) {
            writeRecords(workspace, out, result);
        }
        return ToolResult.text(JsonUtil.writeValueAsString(result, false)).withStructuredContent(result);
    }

    // Writes the records read to the file, one after another, in place of them in the result, which says where
    // they are.
    private static void writeRecords(final SessionWorkspace workspace,
                                     final WorkspacePath out,
                                     final Map<String, Object> result) {
        final List<String> records = result.get("records") instanceof final List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        workspace.write(out, String.join("\n", records) + (records.isEmpty()
                ? ""
                : "\n"), null);
        result.remove("records");
        result.put("records_read", records.size());
        result.put("out", out.toString());
        result.put("note", "The records are in " + out + ", one after another: use grep, read_file, xpath or "
                           + "search_events on it.");
    }

    /// @return The records, with where to read on, as read_stream gave them.
    Map<String, Object> readRecords(final long streamId,
                                    final int firstRecord,
                                    final int recordCount,
                                    final String childType,
                                    final int limit) {
        List<String> records;
        StreamReader.Fetched first;
        try {
            final StreamReader.Records read = streamReader.readRecords(streamId, firstRecord, recordCount,
                    childType, limit);
            records = read.records();
            first = read.first();
        } catch (final StreamReader.StreamReadException e) {
            if (childType != null || !NullSafe.string(e.getMessage()).contains(StreamReader.UNDECODABLE)) {
                throw e;
            }
            // Bytes its feed's character set can't decode: Stroom won't show the text. Read as a pipeline reads
            // it, through the bytes, each such byte replaced (StreamCharsets); the records are split from that
            // text below.
            final String text = streamReader.cachedRawText(streamId, StreamReader.MAX_SAMPLE_CHARS).text();
            final Meta meta = streamReader.meta(streamId);
            records = List.of(text.substring(0, Math.min(text.length(), limit)));
            first = new StreamReader.Fetched(meta.getFeedName(), meta.getTypeName(), null, false, 1L, null,
                    List.of());
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("stream_id", streamId);
        result.put("feed", first.feedName());
        result.put("type", first.streamTypeName());
        result.put("first_record", firstRecord);
        result.put("total_records", first.totalItems());
        result.put("available_child_types", first.availableChildTypes());
        result.put("records", records);
        final Map<String, Object> report = childType == null
                ? streamReader.charsetReport(streamId)
                : null;
        if (report != null) {
            result.put("undecodable_bytes", report);
        }
        if (childType == null && !first.segmented() && Objects.requireNonNullElse(first.totalItems(), 1L) <= 1) {
            final Map<String, Object> split = streamReader.splitRaw(streamId, firstRecord, recordCount, limit);
            if (split != null) {
                result.putAll(split);
                records = castList(split.get("records"));
                if (records.isEmpty()) {
                    return result;
                }
            }
        }
        final long total = result.get("total_records") instanceof final Number number
                ? number.longValue()
                : 0;
        if (records.stream().mapToInt(String::length).sum() >= limit) {
            result.put("truncated", true);
            result.put("hint", "Record data was trimmed at " + StreamReader.thousands(limit) + " characters: read "
                               + "fewer records at a time, or, if the user asks for more, maxChars (up to "
                               + "200,000).");
        } else if (records.size() < recordCount && firstRecord + records.size() < total) {
            // The size limit was reached before the next whole record: the rest from where this stops.
            final int next = firstRecord + records.size();
            result.put("next_first_record", next);
            result.put("hint", records.size() + " whole records fit in " + StreamReader.thousands(limit)
                               + " characters (a limit of characters, not of records); read on with firstRecord="
                               + next + ". survey_feed reads a stream's records without this limit.");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(final Object value) {
        return (List<String>) value;
    }

    private long longArg(final ToolCall request, final String name) {
        final String value = stringArg(request, name);
        try {
            return Long.parseLong(value.replace(",", ""));
        } catch (final NumberFormatException e) {
            throw new WorkspaceException("'" + value + "' isn't a stream id: give a whole number, e.g. from "
                                         + "stream_meta.");
        }
    }

    /// Records in the audit log the streams events were read from.
    private void logView(final ToolCallContext context, final Counts counts) {
        if (counts.streams.isEmpty()) {
            return;
        }
        auditLog.streamsViewed(context.session(), counts.streams, "Read " + counts.written + " event(s) from "
                                                     + counts.streams.size() + " stream(s) with the " + NAME
                                                     + " tool");
    }

    /// Writes the events of one stream, reading its header and footer once.
    private void writeStream(final Writer writer,
                             final long streamId,
                             final List<Long> eventIds,
                             final Counts counts,
                             final ToolCallContext context) throws IOException {
        final int maxBytes = limits(context).getMaxEventBytes();
        try (final Source source = storeProvider.get().openSource(streamId);
                final InputStreamProvider provider = source.get(0)) {
            final long segments = segmentCount(provider);
            // Segment 0 is the header and the last the footer, so events are 1 to segments - 2.
            if (segments < 3) {
                counts.notEvents += eventIds.size();
                return;
            }
            final String header = readSegment(provider, 0, maxBytes);
            final String footer = readSegment(provider, segments - 1, maxBytes);
            for (final long eventId : eventIds) {
                ToolDeadline.check();
                if (eventId < 1 || eventId > segments - 2) {
                    counts.missing++;
                    continue;
                }
                final String event = readSegment(provider, eventId, maxBytes);
                if (event == null || header == null || footer == null) {
                    counts.tooBig++;
                    continue;
                }
                writer.write(EventsFile.element(streamId, eventId, header + event + footer));
                counts.written++;
                counts.streams.add(streamId);
            }
        } catch (final ToolTimeoutException e) {
            throw e;
        } catch (final RuntimeException e) {
            // E.g. a stream of a feed the user may not see, or one that has been deleted.
            LOGGER.debug(() -> "Unable to read stream " + streamId + ": " + e.getMessage(), e);
            counts.unreadable += eventIds.size();
        }
    }

    private static long segmentCount(final InputStreamProvider provider) throws IOException {
        try (final SegmentInputStream in = provider.get()) {
            return in.count();
        }
    }

    // True if the user may view data, as the Data screen needs; false if they may not, or if checking fails (failing
    // closed, as the other tools' checks do).
    private boolean canViewData() {
        try {
            return securityContextProvider.get().hasAppPermission(AppPermission.VIEW_DATA_PERMISSION);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to check the View Data permission", e);
            return false;
        }
    }

    /// @return The segment's text, or null if it is larger than the limit.
    private static String readSegment(final InputStreamProvider provider,
                                      final long segment,
                                      final int maxBytes) throws IOException {
        try (final SegmentInputStream in = provider.get()) {
            in.include(segment);
            final byte[] bytes = in.readNBytes(maxBytes + 1);
            return bytes.length > maxBytes
                    ? null
                    : new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /// @return The stream and event ids of the table's rows, in order, without repeats, up to the limit.
    static Ids readIds(final Path table, final Format format, final int limit) {
        final Result result = TableEngine.run(table, format,
                new Query(null, null, null, null, false, false, null, null));
        final int streamColumn = indexOf(result.columns(), STREAM_ID_COLUMN);
        final int eventColumn = indexOf(result.columns(), EVENT_ID_COLUMN);
        if (streamColumn < 0 || eventColumn < 0) {
            throw new WorkspaceException("The table has no " + STREAM_ID_COLUMN + " and " + EVENT_ID_COLUMN
                                         + " columns, so its rows can't be traced to events. A grouped table "
                                         + "has none; ask for the table without grouping.");
        }
        final Set<List<Long>> seen = new LinkedHashSet<>();
        int skipped = 0;
        int more = 0;
        for (final List<String> row : result.rows()) {
            final Long streamId = parseId(row.get(streamColumn));
            final Long eventId = parseId(row.get(eventColumn));
            if (streamId == null || eventId == null) {
                skipped++;
            } else if (!seen.contains(List.of(streamId, eventId))) {
                if (seen.size() < limit) {
                    seen.add(List.of(streamId, eventId));
                } else {
                    more++;
                }
            }
        }
        final Map<Long, List<Long>> byStream = new LinkedHashMap<>();
        for (final List<Long> id : seen) {
            byStream.computeIfAbsent(id.get(0), k -> new ArrayList<>()).add(id.get(1));
        }
        // In the order they are stored, so reading moves forward through each stream: stored data is compressed
        // in blocks, and reading out of order would decompress the same block again.
        byStream.values().forEach(Collections::sort);
        return new Ids(byStream, seen.size(), skipped, more);
    }

    private static int indexOf(final List<String> columns, final String name) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).strip().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static Long parseId(final String value) {
        try {
            final long id = Long.parseLong(value.strip().replace(",", ""));
            return id > 0
                    ? id
                    : null;
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static String summary(final WorkspacePath out,
                                  final Ids ids,
                                  final Counts counts,
                                  final String coverage) {
        final StringBuilder sb = new StringBuilder();
        if (counts.written > 0) {
            sb.append(String.format(Locale.ROOT, "Wrote %d event(s) from %d stream(s) to %s. Use search_events "
                                                 + "on it to get values by XPath or find text, per event.",
                    counts.written, counts.streams.size(), out));
        } else {
            sb.append("No events could be fetched.");
        }
        // On the first line, so a pipeline's log of this step keeps it.
        if (coverage != null) {
            sb.append(' ').append(coverage);
        }
        note(sb, ids.more(), "more row(s) were left out, over the limit; fetch them with a filtered table");
        note(sb, ids.skipped(), "row(s) had no stream or event id");
        note(sb, counts.unreadable, "event(s) couldn't be read: the streams may be of feeds you can't see, "
                                    + "or deleted");
        note(sb, counts.missing, "event(s) weren't in their streams");
        note(sb, counts.notEvents, "row(s) were of streams that don't hold separate events");
        note(sb, counts.tooBig, "event(s) were too large to fetch");
        return sb.toString();
    }

    private static void note(final StringBuilder sb, final long count, final String text) {
        if (count > 0) {
            sb.append(' ').append(count).append(' ').append(text).append('.');
        }
    }

    /// @param byStream The event ids of each stream, in order, with the streams in the order they were first seen.
    /// @param count    How many events there are.
    /// @param skipped  How many rows had no usable ids.
    /// @param more     How many more events there were, over the limit.
    record Ids(Map<Long, List<Long>> byStream, int count, int skipped, int more) {

    }

    private static class Counts {

        private int written;
        private int unreadable;
        private int missing;
        private int notEvents;
        private int tooBig;
        private final Set<Long> streams = new LinkedHashSet<>();
    }
}
