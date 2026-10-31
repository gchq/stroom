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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.CallProgress;
import stroom.ai.impl.tools.ErrorTriage;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.cef.CefEvents;
import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlParser;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.XmlTree;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What review_coverage reads of Stroom: a pipeline's Error streams in bounded batches by Id, the records they log as
/// missed, a raw record for an example, Unknown events sampled from its newest Events streams, and the streams it
/// wrote. Everything is read as the user: only streams of feeds they may view are found.
class CoverageReads {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CoverageReads.class);

    /// The most streams a find gives at once.
    static final int PAGE = 200;
    /// The most markers read of one Error stream.
    static final int MAX_MARKERS = 5_000;
    private static final Pattern LOGGED = Pattern.compile(
            "(?:No event mapping matched record |Kept as Unknown by rule '(.*)': record )(\\d+)(?: \\((.*)\\))?\\s*$");
    private static final Pattern DATA_NAME = Pattern.compile("Data\\[@Name='([^']+)'\\]");
    private static final List<String> COUNTS = List.of("Warning Count", "Error Count", "Fatal Error Count",
            "Info Count");

    private final StreamReader streamReader;
    private final StreamSummaries streamSummaries;
    private final SampleStreamTexts sampleStreamTexts;

    /// @param streamReader      Finds and reads streams as the user.
    /// @param streamSummaries   Reads an Error stream's markers.
    /// @param sampleStreamTexts Reads a raw stream's text, for an example.
    @Inject
    CoverageReads(final StreamReader streamReader,
                  final StreamSummaries streamSummaries,
                  final SampleStreamTexts sampleStreamTexts) {
        this.streamReader = streamReader;
        this.streamSummaries = streamSummaries;
        this.sampleStreamTexts = sampleStreamTexts;
    }

    /// What a log line of the generated translation says.
    ///
    /// @param message A marker's message.
    /// @return The record, the rule that kept it as Unknown (null for no rule matched) and the values the rules
    /// test; null if it is not such a line.
    static Logged parseLogged(final String message) {
        final Matcher matcher = LOGGED.matcher(NullSafe.string(message));
        if (!matcher.find()) {
            return null;
        }
        final Map<String, String> values = new LinkedHashMap<>();
        for (final String part : NullSafe.string(matcher.group(3)).split(Pattern.quote(" | "), -1)) {
            final int equals = part.indexOf('=');
            if (equals >= 0) {
                values.put(part.substring(0, equals), part.substring(equals + 1));
            }
        }
        return new Logged(0, null, Integer.parseInt(matcher.group(2)), matcher.group(1), values);
    }

    /// Up to `budget` of the pipeline's Error streams with an Id above `after`, oldest first.
    ///
    /// @param pipeline The pipeline.
    /// @param after    The Id to read after.
    /// @param budget   The most Error streams.
    /// @return The Error streams (not deleted), and the Id to continue after, or null when there are no more.
    ErrorBatch errorBatch(final DocRef pipeline, final long after, final int budget) {
        final List<ErrorStream> out = new ArrayList<>();
        long from = after;
        while (out.size() < budget) {
            final int want = Math.min(PAGE, budget - out.size());
            final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                    .addDocRefTerm(MetaFields.PIPELINE, Condition.IS_DOC_REF, pipeline)
                    .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.ERROR)
                    .addIdTerm(MetaFields.ID, Condition.GREATER_THAN, from)
                    .build();
            final List<MetaRow> rows = rows(streamReader.find(expression, want, false));
            if (rows.isEmpty()) {
                return new ErrorBatch(out, null);
            }
            for (final MetaRow row : rows) {
                final Meta meta = row.getMeta();
                if (meta.getStatus() != Status.DELETED) {
                    out.add(new ErrorStream(meta.getId(), meta.getParentMetaId(), meta.getCreateMs(),
                            NullSafe.map(row.getAttributes())));
                }
            }
            from = rows.getLast().getMeta().getId();
            if (rows.size() < want) {
                return new ErrorBatch(out, null);
            }
        }
        return new ErrorBatch(out, from);
    }

    /// Each record no rule matched or a rule kept as Unknown, from these Error streams. A stream Stroom counts as clean
    /// is not read, nor one that is not a raw stream's.
    ///
    /// @param progress Where the call is, said now and then.
    /// @param errors   Error streams.
    /// @return The records logged, and how many of the Error streams were gone through before the call's time
    /// ran out (all of them, if it didn't).
    /// @throws stroom.ai.impl.workspace.ToolTimeoutException if the call is cancelled.
    LoggedRecords logged(final CallProgress progress, final List<ErrorStream> errors) {
        final List<Logged> found = new ArrayList<>();
        int read = 0;
        for (final ErrorStream meta : errors) {
            if (PipelineStepping.timeIsUp()) {
                break;
            }
            read++;
            progress.nowAndThen("Reading Error streams: " + read + " of " + errors.size());
            final Map<String, String> counts = new LinkedHashMap<>();
            meta.attributes().forEach((key, value) -> {
                if (COUNTS.contains(key)) {
                    counts.put(key, value);
                }
            });
            if (!counts.isEmpty() && counts.values().stream().noneMatch(CoverageReads::nonZero)) {
                // Counted, and clean (a find doesn't always give the counts: then it is read).
                continue;
            }
            if (meta.parentId() == null) {
                continue;
            }
            final List<ErrorTriage.Marker> markers;
            try {
                markers = streamSummaries.errorMarkers(meta.id(), MAX_MARKERS);
            } catch (final Exception e) {
                // An Error stream Stroom can't read (its volume gone): the batch's others still counted.
                LOGGER.debug(() -> LogUtil.message("Unable to read Error stream {}", meta.id()), e);
                continue;
            }
            for (final ErrorTriage.Marker marker : markers) {
                final Logged parsed = parseLogged(marker.message());
                if (parsed != null) {
                    found.add(new Logged(meta.parentId(), meta.createMs(), parsed.record(), parsed.rule(),
                            parsed.values()));
                }
            }
        }
        return new LoggedRecords(found, read);
    }

    /// One raw record, as the mapping reads it, for an example.
    ///
    /// @param mapping The pipeline's mapping.
    /// @param raw     A raw stream.
    /// @param record  The record in it, from 0, as step_records takes it.
    /// @return The record shown, or null if it can't be read.
    String example(final TranslationMapping mapping, final long raw, final int record) {
        final String text;
        try {
            text = sampleStreamTexts.read(List.of(raw)).texts().values().stream().findFirst().orElse("");
        } catch (final ToolRefusal e) {
            return null;
        }
        final List<Object> records;
        try {
            final SplitterSpec splitter = "data_splitter".equals(mapping.input())
                    ? SplitterDryRun.inferSpec(text).spec()
                    : null;
            records = LocalCheck.sampleRecords(mapping, text, splitter).records();
        } catch (final RuntimeException e) {
            // Only an example: a record this can't read leaves its kind without one.
            return null;
        }
        return record >= 0 && record < records.size()
                ? shown(records.get(record))
                : null;
    }

    /// A record, briefly.
    ///
    /// @param record A record: a map, an XML element or other text.
    /// @return Its first 12 fields as `name=value`, its XML, or its text; at most 400 characters.
    static String shown(final Object record) {
        if (record instanceof final Map<?, ?> map) {
            final List<String> fields = new ArrayList<>();
            map.entrySet().stream().limit(12).forEach(e -> fields.add(e.getKey() + "="
                                                                      + ToolText.str(e.getValue())));
            return ToolText.head(String.join(", ", fields), 400);
        }
        if (record instanceof final XmlTree.Element element) {
            return ToolText.head(XmlTree.toText(element), 400);
        }
        return ToolText.head(ToolText.str(record), 400);
    }

    /// For an XSLT that logs no Unknown records (saved before they were logged, or by hand): events kept as Unknown in
    /// its newest Events streams, grouped by TypeId and the Data they carry.
    ///
    /// @param pipeline  The pipeline.
    /// @param streams   The most Events streams to read.
    /// @param perStream The most records to read of each.
    /// @return The counts and an example of each kind, and the raw streams they came from.
    SampledUnknown sampledUnknown(final DocRef pipeline, final int streams, final int perStream) {
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addDocRefTerm(MetaFields.PIPELINE, Condition.IS_DOC_REF, pipeline)
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.EVENTS)
                .build();
        final Map<UnknownKind, Integer> kinds = new LinkedHashMap<>();
        final Map<UnknownKind, Map<String, Object>> examples = new LinkedHashMap<>();
        final Set<Long> raws = new TreeSet<>();
        for (final MetaRow row : rows(streamReader.find(expression, streams, true))) {
            final Meta meta = row.getMeta();
            if (meta.getStatus() == Status.DELETED) {
                continue;
            }
            final List<String> bodies = new ArrayList<>();
            try {
                final StreamReader.Fetched first = streamReader.fetch(meta.getId(), 0, null);
                final long total = Math.min(first.totalItems() == null || first.totalItems() == 0
                        ? 1
                        : first.totalItems(), perStream);
                bodies.add(NullSafe.string(first.data()));
                for (long i = 1; i < total; i++) {
                    bodies.add(NullSafe.string(streamReader.fetch(meta.getId(), i, null).data()));
                }
            } catch (final RuntimeException e) {
                // An Events stream Stroom can't read: the others still sampled.
                LOGGER.debug(() -> LogUtil.message("Unable to read Events stream {}", meta.getId()), e);
                continue;
            }
            for (final String data : bodies) {
                if (data.contains("Unknown")) {
                    unknownIn(data, meta, kinds, examples, raws);
                }
            }
        }
        return new SampledUnknown(kinds, examples, raws);
    }

    private static void unknownIn(final String data,
                                  final Meta meta,
                                  final Map<UnknownKind, Integer> kinds,
                                  final Map<UnknownKind, Map<String, Object>> examples,
                                  final Set<Long> raws) {
        final List<XmlElement> events;
        try {
            events = CefEvents.eventsOf(StreamReader.rootClosed(data));
        } catch (final XmlParser.XmlParseException e) {
            return;
        }
        for (final XmlElement event : events) {
            if (!"Unknown".equals(CefEvents.eventKind(event))) {
                continue;
            }
            final Map<String, String> values = CefEvents.eventValues(event);
            final Matcher matcher = DATA_NAME.matcher(String.join(" ", values.keySet()));
            final List<String> found = new ArrayList<>();
            while (matcher.find()) {
                found.add(matcher.group(1));
            }
            final List<String> sorted = found.stream().sorted().toList();
            final UnknownKind key = new UnknownKind(values.getOrDefault("EventDetail/TypeId", ""),
                    List.copyOf(sorted.subList(0, Math.min(6, sorted.size()))));
            kinds.merge(key, 1, Integer::sum);
            examples.computeIfAbsent(key, k -> {
                final Map<String, Object> example = new LinkedHashMap<>();
                example.put("events_stream", meta.getId());
                example.put("raw_stream", meta.getParentMetaId());
                final Map<String, String> shown = new LinkedHashMap<>();
                values.entrySet().stream().limit(14).forEach(e -> shown.put(e.getKey(), e.getValue()));
                example.put("values", shown);
                return example;
            });
            if (meta.getParentMetaId() != null) {
                raws.add(meta.getParentMetaId());
            }
        }
    }

    /// @param pipeline The pipeline.
    /// @return One of the Events streams it wrote, if any (the newest), with its meta values.
    List<MetaRow> anyEvents(final DocRef pipeline) {
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addDocRefTerm(MetaFields.PIPELINE, Condition.IS_DOC_REF, pipeline)
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.EVENTS)
                .build();
        return rows(streamReader.find(expression, 1, true));
    }

    /// The newest streams of a type the pipeline wrote.
    ///
    /// @param pipeline The pipeline.
    /// @param type     The stream type, e.g. [StreamTypeNames#ERROR].
    /// @param limit    The most streams to find.
    /// @return Its streams of that type (not deleted), newest first, with their meta values.
    List<MetaRow> newest(final DocRef pipeline, final String type, final int limit) {
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addDocRefTerm(MetaFields.PIPELINE, Condition.IS_DOC_REF, pipeline)
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, type)
                .build();
        return rows(streamReader.find(expression, limit, true)).stream()
                .filter(row -> row.getMeta().getStatus() != Status.DELETED)
                .toList();
    }

    /// The Events streams the pipeline wrote from these raw streams (their documents are in the index).
    ///
    /// @param pipeline The pipeline.
    /// @param raws     Raw streams.
    /// @return The Events streams' Ids (not deleted), in order.
    List<Long> oldEvents(final DocRef pipeline, final List<Long> raws) {
        final Set<Long> out = new TreeSet<>();
        for (int n = 0; n < raws.size(); n += 50) {
            final ExpressionOperator.Builder parents = ExpressionOperator.builder().op(Op.OR);
            raws.subList(n, Math.min(raws.size(), n + 50))
                    .forEach(raw -> parents.addIdTerm(MetaFields.PARENT_ID, Condition.EQUALS, raw));
            final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                    .addOperator(parents.build())
                    .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.EVENTS)
                    .addDocRefTerm(MetaFields.PIPELINE, Condition.IS_DOC_REF, pipeline)
                    .build();
            rows(streamReader.find(expression, 500, true)).stream()
                    .map(MetaRow::getMeta)
                    .filter(meta -> meta.getStatus() != Status.DELETED)
                    .forEach(meta -> out.add(meta.getId()));
        }
        return List.copyOf(out);
    }

    /// The newest streams of a type on a feed.
    ///
    /// @param feed  The feed's name.
    /// @param type  The stream type, e.g. [StreamTypeNames#RAW_EVENTS].
    /// @param limit The most streams to find.
    /// @return Their Ids (not deleted), newest first.
    List<Long> newestOnFeed(final String feed, final String type, final int limit) {
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, feed)
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, type)
                .build();
        return rows(streamReader.find(expression, limit, true)).stream()
                .map(MetaRow::getMeta)
                .filter(meta -> meta.getStatus() != Status.DELETED)
                .map(Meta::getId)
                .toList();
    }

    /// A few of the newest raw streams on the feed the pipeline's Events are in.
    ///
    /// @param anyEvents Events streams the pipeline wrote ([#anyEvents]).
    /// @return Up to 3 raw streams, newest first; none if the feed isn't known.
    List<Long> newestRaw(final List<MetaRow> anyEvents) {
        final String feed = anyEvents.stream()
                .map(row -> row.getMeta().getFeedName())
                .filter(NullSafe::isNonBlankString)
                .findFirst()
                .orElse(null);
        if (feed == null) {
            return List.of();
        }
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, feed)
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS)
                .build();
        return rows(streamReader.find(expression, 50, true)).stream()
                .map(MetaRow::getMeta)
                .sorted((a, b) -> Long.compare(b.getCreateMs(), a.getCreateMs()))
                .limit(3)
                .map(Meta::getId)
                .toList();
    }

    private static List<MetaRow> rows(final ResultPage<MetaRow> page) {
        return NullSafe.list(NullSafe.get(page, ResultPage::getValues));
    }

    private static boolean nonZero(final String value) {
        try {
            return NullSafe.isNonBlankString(value) && Long.parseLong(value.strip()) != 0;
        } catch (final NumberFormatException e) {
            return true;
        }
    }


    // --------------------------------------------------------------------------------


    /// An Error stream, as a find gives it.
    ///
    /// @param id         Its Id.
    /// @param parentId   The raw stream it is of, or null.
    /// @param createMs   When it was made.
    /// @param attributes Its meta values, e.g. `Warning Count`.
    record ErrorStream(long id, Long parentId, Long createMs, Map<String, String> attributes) {

    }

    /// A batch of Error streams.
    ///
    /// @param errors The Error streams.
    /// @param more   The Id to continue after, or null when there are no more.
    record ErrorBatch(List<ErrorStream> errors, Long more) {

    }

    /// A record the generated translation logged as missed.
    ///
    /// @param raw     The raw stream it is in.
    /// @param created When its Error stream was made, or null.
    /// @param record  The record (from 0).
    /// @param rule    The rule that kept it as Unknown, or null for no rule matched.
    /// @param values  The values the rules test.
    record Logged(long raw, Long created, int record, String rule, Map<String, String> values) {

    }

    /// The records logged in a batch of Error streams.
    ///
    /// @param records The records.
    /// @param read    How many of the Error streams were gone through.
    record LoggedRecords(List<Logged> records, int read) {

    }

    /// A kind of Unknown event: its TypeId and the names of the Data it carries.
    ///
    /// @param typeId Its TypeId, or "".
    /// @param data   Up to 6 Data names, sorted.
    record UnknownKind(String typeId, List<String> data) {

    }

    /// Unknown events sampled.
    ///
    /// @param kinds    How many of each kind, in the order first seen.
    /// @param examples An example of each kind: `events_stream`, `raw_stream`, `values`.
    /// @param raws     The raw streams they came from.
    record SampledUnknown(Map<UnknownKind, Integer> kinds,
                          Map<UnknownKind, Map<String, Object>> examples,
                          Set<Long> raws) {

    }
}
