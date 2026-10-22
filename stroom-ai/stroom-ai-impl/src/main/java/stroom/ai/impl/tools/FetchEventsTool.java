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
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

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
import java.util.Set;

/// Fetches the source events behind the rows of a table, e.g. an attached dashboard table, into one XML file in the
/// workspace (see [EventsFile]), for search_events to search by XPath or text.
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

    private final Provider<Store> storeProvider;
    private final Provider<SecurityContext> securityContextProvider;
    private final AiAuditLog auditLog;

    @Inject
    FetchEventsTool(final ChatWorkspace workspace,
                    final Provider<Store> storeProvider,
                    final Provider<SecurityContext> securityContextProvider,
                    final AiAuditLog auditLog) {
        super(workspace);
        this.storeProvider = storeProvider;
        this.securityContextProvider = securityContextProvider;
        this.auditLog = auditLog;
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
                                 + "the result says if any were left out.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PATH_ARG, ToolDescription.builder()
                                .general("The table")
                                .chatOnly(", e.g. /attachments/12.md")
                                .general(".")
                                .build())
                        .addIntegerProperty(LIMIT_ARG, "The most events to fetch, from the first rows.")
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("The file in /outputs/ to write the events to (default " + DEFAULT_OUT
                                          + ").")
                                .build())
                        .required(PATH_ARG)
                        .build())
                .build();
    }

    @Override
    protected Duration timeLimit(final ToolCallContext context) {
        // Reading many streams can take longer than a workspace step.
        return limits(context).getQueryTimeLimit().getDuration();
    }

    @Override
    ToolResult run(final ToolCall request, final ToolCallContext context) {
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
