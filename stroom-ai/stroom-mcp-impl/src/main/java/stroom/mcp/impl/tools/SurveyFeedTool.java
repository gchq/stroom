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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.CallProgress;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamReader.RawHead;
import stroom.ai.impl.tools.StreamReader.StreamReadException;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolDescription;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.IntegerSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.sample.FeedSampling;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecord;
import stroom.ai.impl.tools.sample.RecordSplitter;
import stroom.ai.impl.tools.sample.Shapes;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.data.shared.StreamTypeNames;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// `survey_feed` - samples a feed's streams spread over its lifetime and groups their records into shapes (kinds of
/// event), keeping the survey in a build's survey doc when given a build (the Stroom MCP server's
/// `tools/sampling.py` `survey_feed`). It reads streams as the user, and writes only the survey doc, through
/// [WriteGuard].
///
/// It is heavy (a run slot over `/mcp`): it stops between streams when its thread is interrupted, and at the
/// session's time limit for a call it stops reading and returns what it has read, for the next call to carry on
/// from.
class SurveyFeedTool extends AbstractBuiltInTool {

    static final String NAME = "survey_feed";
    static final String FEED_ARG = "feed";
    static final String STREAM_TYPE_ARG = "stream_type";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String MAX_STREAMS_ARG = "max_streams";
    static final String SKIP_STREAM_IDS_ARG = "skip_stream_ids";
    static final String KNOWN_SIGNATURES_ARG = "known_signatures";
    static final String MAX_CHARS_ARG = "max_chars_per_stream";
    static final String MAX_PARTS_ARG = "max_parts_per_stream";
    static final String MAX_RECORDS_ARG = "max_records_per_stream";
    static final String EXAMPLES_ARG = "examples_per_shape";
    static final String BUILD_ARG = "build";

    /// The most characters read of a part, asked for.
    static final int MAX_CHARS = 5_000_000;

    private final StreamReader streamReader;
    private final FeedNames feedNames;
    private final WriteGuard guard;
    private final SurveyDocuments surveyDocuments;
    private final Consent consent;
    private final FewerStreams fewerStreams;
    private final DocumentAccess documentAccess;
    private final AiAuditLog auditLog;

    @Inject
    SurveyFeedTool(final StreamReader streamReader,
                   final FeedNames feedNames,
                   final WriteGuard guard,
                   final SurveyDocuments surveyDocuments,
                   final Consent consent,
                   final FewerStreams fewerStreams,
                   final DocumentAccess documentAccess,
                   final AiAuditLog auditLog) {
        this.streamReader = streamReader;
        this.feedNames = feedNames;
        this.guard = guard;
        this.surveyDocuments = surveyDocuments;
        this.consent = consent;
        this.fewerStreams = fewerStreams;
        this.documentAccess = documentAccess;
        this.auditLog = auditLog;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public boolean isReadOnly() {
        // With a build, it keeps the survey doc in it.
        return false;
    }

    @Override
    public boolean changesOnlyItsBuild() {
        // Through WriteGuard, only what is in the build's folder.
        return true;
    }

    @Override
    public boolean isReadOnly(final ToolCall call) {
        // Without a build it only reads.
        return stringArg(call, BUILD_ARG) == null;
    }

    @Override
    public boolean hasReadOnlyCalls() {
        return true;
    }

    @Override
    public boolean usesRunSlot() {
        // It reads many streams.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.VIEW_DATA_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .note("The feed's streams are read as the user, who needs View on the feed; with a build, the "
                      + "survey doc is made in (or changed in) the build's folder as the user.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Sample a feed's streams spread over its lifetime (newest, oldest, then the middle, "
                             + "then the quarters...) and group their records into shapes (kinds of event): by "
                             + "fields and event-naming values for structured data, by masked message templates "
                             + "for syslog and other text. Only the head of each stream is read "
                             + "(max_chars_per_stream per part, max_parts_per_stream parts), so multi-GB streams "
                             + "cost the same as small ones; a sample's streams (stream_ids), and a feed's last "
                             + "streams (fewer left than max_streams), are read whole up to 2,000,000 characters, "
                             + "and coverage says when one wasn't (the feed is then not covered). Every call reads "
                             + "all max_streams streams it picks (a quiet feed's rare kinds of event can sit behind "
                             + "several streams with nothing new: asked for by the user). The feed counts as "
                             + "covered (saturated) when a call with known_signatures reads a full max_streams "
                             + "with nothing new, or when every stream has been read. Returns each shape with its "
                             + "count, share and examples, and where each example is (stream, part, record) for "
                             + "step_records. Call again with skip_stream_ids (all streams read so far) and "
                             + "known_signatures to read further streams for kinds of event not seen yet. With a "
                             + "build, the results are kept in (and continued from) the build's survey doc. Reads "
                             + "only, apart from that doc.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(FEED_ARG, "The feed that already holds the raw data.")
                        .addStringProperty(STREAM_TYPE_ARG, "Stream type to sample.")
                        .addProperty(STREAM_IDS_ARG, idsSchema("Survey exactly these streams instead of picking "
                                                               + "from the feed: the sample streams an onboarding "
                                                               + "uploaded (one per file), to see every kind of "
                                                               + "event they hold and where. Not to pick an "
                                                               + "existing feed's streams: leave that to "
                                                               + "max_streams (fewer than 10 of them, by id, is "
                                                               + "confirmed by the user in a form)."))
                        .addIntegerProperty(MAX_STREAMS_ARG, "Streams to read in this call, spread over the feed's "
                                                             + "lifetime: leave the default (10) unless the user "
                                                             + "gives a number. More, up to 100, as they ask; "
                                                             + "fewer is confirmed by the user in a form.")
                        .addProperty(SKIP_STREAM_IDS_ARG, idsSchema("Streams already read (streams_read from "
                                                                    + "earlier calls): this call picks others."))
                        .addProperty(KNOWN_SIGNATURES_ARG, stringArraySchema("Shape signatures from earlier "
                                                                             + "calls; locations then only cover "
                                                                             + "new shapes."))
                        .addIntegerProperty(MAX_CHARS_ARG, "Read at most this many characters from the start of "
                                                           + "each part; big streams are only sampled at their "
                                                           + "head. Default 250,000, or 2,000,000 for stream_ids "
                                                           + "and a feed's last streams (read whole).")
                        .addIntegerProperty(MAX_PARTS_ARG, "Parts of a stream to read (a stream of several files "
                                                           + "received at once has one each), not records: "
                                                           + "max_records_per_stream counts records.")
                        .addIntegerProperty(MAX_RECORDS_ARG, "Default 1,000, or 10,000 for stream_ids and a feed's "
                                                             + "last streams.")
                        .addProperty(EXAMPLES_ARG, new IntegerSchema(null))
                        .addStringProperty(BUILD_ARG, "Keep the survey record in this build: a Documentation doc "
                                                      + "'<feed> - Survey' with the kinds of event, examples and "
                                                      + "survey state. An existing record is continued: its "
                                                      + "streams are skipped and its shapes are known.")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            final Request request = request(call);
            if (!documentAccess.canViewData()) {
                throw new ToolRefusal("You don't have permission to view data, so the feed's streams can't be "
                                      + "read.");
            }
            final List<Meta> chosen = metas(request.streamIds());
            final String feed = feed(call, context, request, chosen);
            confirmFewerStreams(call, context, feed, request, chosen);
            return JsonResults.of(withinTimeLimit(context, () -> survey(context, request, feed, chosen)));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final ToolTimeoutException e) {
            return JsonResults.refused(Thread.currentThread().isInterrupted()
                    ? "The survey was stopped: the call was cancelled."
                    : "The survey ran past this session's time limit for a call (" + limitText(context) + ") "
                      + "before it had read a stream: call again.");
        }
    }

    // The arguments, checked.
    private Request request(final ToolCall call) {
        final Integer maxChars = bounded(call, MAX_CHARS_ARG, 1000, MAX_CHARS);
        final Integer maxRecords = bounded(call, MAX_RECORDS_ARG, 10, FeedSampling.SAMPLE_RECORDS);
        return new Request(
                stringArg(call, FEED_ARG),
                NullSafe.nonBlankStringElse(stringArg(call, STREAM_TYPE_ARG), StreamTypeNames.RAW_EVENTS),
                StartOnboardingTool.streamIds(call, STREAM_IDS_ARG),
                orDefault(bounded(call, MAX_STREAMS_ARG, 1, 100), FeedSampling.SURVEY_STREAMS),
                StartOnboardingTool.streamIds(call, SKIP_STREAM_IDS_ARG),
                stringListArg(call, KNOWN_SIGNATURES_ARG),
                maxChars,
                orDefault(bounded(call, MAX_PARTS_ARG, 1, 20), 3),
                maxRecords,
                orDefault(bounded(call, EXAMPLES_ARG, 1, 10), 2),
                stringArg(call, BUILD_ARG));
    }

    // The streams named, by id.
    private List<Meta> metas(final List<Long> streamIds) {
        final List<Meta> metas = new ArrayList<>();
        for (final long streamId : streamIds) {
            try {
                metas.add(streamReader.meta(streamId));
            } catch (final StreamReadException e) {
                throw new ToolRefusal(e.getMessage());
            }
        }
        return metas;
    }

    // The feed named, as Stroom spells it (the user picks the feed they meant in a form), or the first stream's.
    private String feed(final ToolCall call,
                        final ToolCallContext context,
                        final Request request,
                        final List<Meta> chosen) {
        if (request.feed() == null) {
            if (chosen.isEmpty()) {
                throw new ToolRefusal("Give feed, or stream_ids");
            }
            return chosen.getFirst().getFeedName();
        }
        return feedNames.resolve(request.feed(), NAME, call.id(), context);
    }

    // A survey call reading fewer than SURVEY_STREAMS streams is the user's to ask for, confirmed in a form (seen in
    // VS Code: an agent surveyed a live feed 3 streams at a time, and the user had to ask for a larger sample). The
    // answer holds for that feed and count, so later calls go on unasked. Returns when it may go ahead.
    private void confirmFewerStreams(final ToolCall call,
                                     final ToolCallContext context,
                                     final String feed,
                                     final Request request,
                                     final List<Meta> chosen) {
        if (chosen.isEmpty() && request.maxStreams() < FeedSampling.SURVEY_STREAMS) {
            askFewerStreams(call, context, feed, request.maxStreams());
        } else if (!chosen.isEmpty() && chosen.size() < FeedSampling.SURVEY_STREAMS && !inABuild(feed)) {
            // A few of an existing feed's streams, by id: as few as a smaller max_streams, so the user's to confirm
            // too (seen in testing on 0.18.13: an existing feed onboarded from 5 streams). A sample uploaded to a
            // build's feed is read as it is.
            askFewerStreams(call, context, feed, chosen.size());
        }
    }

    private void askFewerStreams(final ToolCall call,
                                 final ToolCallContext context,
                                 final String feed,
                                 final int count) {
        if (fewerStreams.agreed(feed, count)) {
            return;
        }
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("feed", feed);
        details.put("streams a call", count);
        details.put("default", FeedSampling.SURVEY_STREAMS);
        details.put("fewer streams", "rarer kinds of event are less likely to be found in a call; the survey still "
                                     + "goes on until it is covered");
        consent.require(call, context, Consent.Request.confirmation(NAME,
                "Survey feed '" + feed + "' only " + count + " stream(s) a call (the default is "
                + FeedSampling.SURVEY_STREAMS + ")", details));
        fewerStreams.agree(feed, count);
    }

    // Whether a feed is one a build made (its uploaded samples, a test feed); not for an existing feed. True when
    // it can't be told.
    private boolean inABuild(final String feed) {
        try {
            return feedNames.feedsNamed(feed).stream().anyMatch(ref -> guard.buildOf(ref).isPresent());
        } catch (final RuntimeException e) {
            return true;
        }
    }

    private Map<String, Object> survey(final ToolCallContext context,
                                       final Request request,
                                       final String feed,
                                       final List<Meta> chosen) {
        final String build = request.build();
        final SurveyRecord record = build == null
                ? null
                : surveyDocuments.load(build, feed).orElse(null);
        final Map<String, Object> state = record == null
                ? null
                : SurveyDoc.readState(record.text());
        final Set<Long> skip = new TreeSet<>(request.skipStreamIds());
        final Set<String> known = new LinkedHashSet<>(request.knownSignatures());
        if (state != null) {
            JsonValues.listOrEmpty(state.get("streams_read")).forEach(id -> skip.add(((Number) id).longValue()));
            JsonValues.mapOrEmpty(state.get("shapes")).keySet().forEach(signature -> known.add(String.valueOf(
                    signature)));
        }
        final CallProgress progress = CallProgress.of(context);
        final List<Meta> streams;
        final Map<String, String> span;
        if (!chosen.isEmpty()) {
            // Streams named are read, even ones the build's survey record read before (in part, say): only the
            // ones the call says to skip are not.
            streams = chosen.stream().filter(meta -> !request.skipStreamIds().contains(meta.getId())).toList();
            span = span(chosen);
        } else {
            final ExpressionOperator terms = ExpressionOperator.builder().op(Op.AND)
                    .addTextTerm(MetaFields.FEED, Condition.EQUALS, feed)
                    .addTextTerm(MetaFields.TYPE, Condition.EQUALS, request.streamType())
                    .build();
            final FeedSampling.Spread spread = FeedSampling.pickSpread(this::find, terms, request.maxStreams(),
                    skip, finding(progress));
            streams = spread.picked();
            span = spread.span();
        }
        // Fewer streams left than a call reads: the feed's last ones, so each is read whole where it can be, as a
        // sample's are. Seen in production: a 750 KB sample read only to its 108th record of 1,000 hid most of its
        // kinds of event; and in Ask Stroom AI, a one-stream feed read to its 334th record of 1,000 was called
        // covered.
        final boolean exhausted = chosen.isEmpty() && streams.size() < request.maxStreams();
        final boolean whole = !chosen.isEmpty() || exhausted;
        final Limits limits = new Limits(orDefault(request.maxChars(), whole
                ? FeedSampling.SAMPLE_CHARS
                : FeedSampling.FEED_CHARS), request.maxParts(), orDefault(request.maxRecords(), whole
                ? FeedSampling.SAMPLE_RECORDS
                : FeedSampling.FEED_RECORDS));
        final SurveyRecords kept = new SurveyRecords(build, record, state, request.examples());
        if (streams.isEmpty() && !skip.isEmpty()) {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("feed", feed);
            result.put("streams_read", List.of());
            result.put("records_read", 0);
            result.put("saturated", true);
            result.put("shapes", List.of());
            result.put("new_shapes", 0);
            result.put("locations", List.of());
            result.put("time_range", span);
            result.put("hint", "Every stream in the feed has been read: the survey is complete.");
            return keep(context, kept, result, Map.of());
        }
        if (streams.isEmpty()) {
            throw new ToolRefusal("No " + request.streamType() + " streams in feed '" + feed + "'");
        }
        final Shapes shapes = new Shapes(new ArrayList<>(known), request.examples());
        final Reading reading = read(context, progress, feed, streams, shapes, limits);
        if (reading.firstChunk() == null) {
            if (reading.stopped()) {
                throw new ToolTimeoutException();
            }
            throw new ToolRefusal("Nothing readable in the sampled streams of '" + feed + "'");
        }
        final Map<String, Object> result = result(new Outcome(feed, span, chosen, exhausted, !known.isEmpty(),
                state, streams.size(), request.maxStreams(), limits), reading, shapes, context);
        return keep(context, kept, result, shapes.shapes());
    }

    // Each stream's head, its records added to the shapes.
    private Reading read(final ToolCallContext context,
                         final CallProgress progress,
                         final String feed,
                         final List<Meta> streams,
                         final Shapes shapes,
                         final Limits limits) {
        final Reading reading = new Reading();
        progress.say("Reading the head of " + streams.size() + " stream" + (streams.size() != 1
                ? "s"
                : "") + " of " + feed);
        int n = 0;
        try {
            for (final Meta meta : streams) {
                n++;
                if (timeIsUp()) {
                    reading.stop();
                    break;
                }
                progress.nowAndThen("Reading stream " + meta.getId() + " (" + n + " of " + streams.size() + "): "
                                    + reading.recordsRead + " records read so far");
                readStream(reading, meta, shapes, limits);
                if (reading.stopped()) {
                    break;
                }
            }
        } finally {
            auditLog.streamsViewed(context.session(), reading.streamsRead(), "Surveyed the kinds of event in "
                                                                + reading.streamsRead().size() + " stream(s) of "
                                                                + feed + " with the " + NAME + " tool");
        }
        return reading;
    }

    // One stream's parts, up to the limits.
    private void readStream(final Reading reading, final Meta meta, final Shapes shapes, final Limits limits) {
        int newHere = 0;
        int readHere = 0;
        boolean cut = false;
        int parts = 1;
        int part = 0;
        while (part < Math.min(parts, limits.maxParts()) && readHere < limits.maxRecords()) {
            if (timeIsUp()) {
                // Stopped part-way: what is read of it so far counts, as the start of the stream.
                reading.stop();
                cut = true;
                break;
            }
            final RawHead head;
            try {
                head = streamReader.readHead(meta.getId(), part, limits.maxChars());
            } catch (final StreamReadException e) {
                // A feed's oldest streams are read too, and the likeliest to be on a volume Stroom can no longer
                // read: said for that stream, the rest of the survey still made.
                reading.perStream.add(entry("stream", meta.getId(), "part", part, "error",
                        "Stroom couldn't read it: " + e.getMessage()));
                break;
            }
            parts = head.parts();
            cut = cut || head.truncated();
            final RecordSplitter.Chunk chunk;
            try {
                chunk = RecordSplitter.split(head.text(), limits.maxRecords() - readHere, head.truncated());
            } catch (final RuntimeException e) {
                // A part this detector cannot split.
                reading.perStream.add(entry("stream", meta.getId(), "part", part, "error",
                        "could not split records: " + e.getMessage()));
                part++;
                continue;
            }
            if (reading.format != null && !chunk.format().equals(reading.format)) {
                reading.perStream.add(entry("stream", meta.getId(), "part", part, "note",
                        "reads as " + chunk.format() + ", not " + reading.format + "; skipped"));
                part++;
                continue;
            }
            if (reading.format == null) {
                reading.format = chunk.format();
                reading.firstChunk = chunk;
            }
            for (int index = 0; index < chunk.records().size(); index++) {
                if (shapes.add(chunk, index, meta.getId(), part)) {
                    newHere++;
                }
                readHere++;
            }
            part++;
        }
        reading.recordsRead += readHere;
        final Map<String, Object> entry = entry("stream", meta.getId(), "created", meta.getCreateMs() != 0
                        ? FeedSampling.iso(meta.getCreateMs())
                        : null,
                "records", readHere, "parts", parts, "head_only", cut || parts > limits.maxParts()
                                                                   || readHere >= limits.maxRecords(),
                "new_shapes", newHere);
        entry.putAll(FeedSampling.charsetEntry(streamReader.charsetReport(meta.getId())));
        reading.perStream.add(entry);
    }

    // The call's result, as the Stroom MCP server gave it.
    private Map<String, Object> result(final Outcome outcome,
                                       final Reading reading,
                                       final Shapes shapes,
                                       final ToolCallContext context) {
        final List<Map<String, Object>> found = new ArrayList<>(shapes.shapes().values().stream()
                .filter(shape -> count(shape) > 0)
                .toList());
        found.sort(Comparator.comparingLong(SurveyFeedTool::count).reversed());
        final Map<String, Long> counts = new LinkedHashMap<>();
        found.forEach(shape -> counts.put(signature(shape), count(shape)));
        final List<Map<String, Object>> newShapes = found.stream()
                .filter(shape -> !Boolean.TRUE.equals(shape.get("known")))
                .toList();
        final List<Map<String, Object>> toStep = outcome.known()
                ? newShapes
                : found;
        final Map<String, String> drop = SurveyDoc.dropped(outcome.state());
        final List<Long> readIds = reading.streamsRead();
        final List<Long> partly = reading.perStream.stream()
                .filter(stream -> Boolean.TRUE.equals(stream.get("head_only")))
                .map(stream -> ((Number) stream.get("stream")).longValue())
                .toList();
        final boolean chosen = !outcome.chosen().isEmpty();
        // Covered: a full batch after the first added nothing new, or the feed has no unread streams left (fewer
        // than asked for were there to pick); chosen streams: every one was read, so the sample is covered as far
        // as it goes.
        boolean saturated = (outcome.known() && newShapes.isEmpty() && readIds.size() >= outcome.maxStreams())
                            || outcome.exhausted() || chosen;
        // Not when a given stream, or the feed's last, was read only in part: the kinds of event further in weren't
        // seen. Seen in Ask Stroom AI: 334 of a stream's 1,000 records read, and "covered: every stream ... read".
        final boolean wholeOnly = chosen || outcome.exhausted();
        saturated = saturated && !(wholeOnly && !partly.isEmpty()) && !reading.stopped();
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("feed", outcome.feed());
        result.put("format", reading.format);
        result.put("time_range", outcome.span());
        result.put("streams_read", readIds);
        result.put("records_read", reading.recordsRead);
        if (RecordSplitter.XML.equals(reading.format) && NullSafe.isNonEmptyString(reading.firstChunk.root())) {
            // An XML document's root, with its namespace, which its records (the examples) inherit.
            result.put("records_in", reading.firstChunk.root().lines().findFirst().orElse(""));
        }
        result.put("saturated", saturated);
        result.put("coverage", reading.stopped()
                ? stoppedCoverage(context, readIds.size(), outcome.picked())
                : coverage(outcome, saturated, readIds, partly, reading.recordsRead));
        result.put("per_stream", reading.perStream);
        result.put("shapes", shapesShown(found, counts, drop));
        result.put("new_shapes", newShapes.size());
        // Records of a kind left untranslated are stepped too, expecting no Event.
        result.put("locations", locations(toStep, drop));
        result.put("hint", FeedSampling.hint(saturated, !newShapes.isEmpty(), outcome.known()));
        return result;
    }

    private static String coverage(final Outcome outcome,
                                   final boolean saturated,
                                   final List<Long> readIds,
                                   final List<Long> partly,
                                   final long recordsRead) {
        final String again = "Survey them again with stream_ids=" + partly + " and max_chars_per_stream="
                             + Math.min(MAX_CHARS, (long) outcome.limits().maxChars() * 4) + " (up to 5,000,000)";
        final boolean chosen = !outcome.chosen().isEmpty();
        if (chosen && !partly.isEmpty()) {
            return "NOT COVERED: only the start of stream(s) " + partly + " was read (" + recordsRead + " records); "
                   + "there may be other kinds of event further in. " + again;
        }
        if (chosen) {
            return "all " + readIds.size() + " given stream(s) read whole: these are the kinds of event the sample "
                   + "holds";
        }
        if (outcome.exhausted() && !partly.isEmpty()) {
            return "NOT COVERED: every stream in the feed was picked, but only the start of stream(s) " + partly
                   + " was read (" + recordsRead + " records); there may be other kinds of event further in. "
                   + again;
        }
        if (outcome.exhausted()) {
            return "covered: every stream in the feed has been read whole";
        }
        if (saturated) {
            final String heads = partly.isEmpty()
                    ? ""
                    : " (only the start of stream(s) " + partly + ": big streams are sampled at their head)";
            return "covered: no new kinds of event in the last " + readIds.size() + " streams read" + heads;
        }
        return "NOT COVERED YET: keep surveying before treating the translation as complete";
    }

    // The coverage of a call its time limit stopped: not covered, whatever was found.
    private static String stoppedCoverage(final ToolCallContext context, final int read, final int picked) {
        return "NOT COVERED YET: this session's time limit for a call (" + limitText(context) + ") stopped the "
               + "survey after " + read + " of the " + picked + " streams picked; call again with "
               + "skip_stream_ids=every stream read so far to read the others, and keep surveying before treating "
               + "the translation as complete";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> shapesShown(final List<Map<String, Object>> found,
                                                         final Map<String, Long> counts,
                                                         final Map<String, String> drop) {
        final List<Map<String, Object>> shown = new ArrayList<>();
        for (final Map<String, Object> shape : found.subList(0, Math.min(60, found.size()))) {
            final String signature = signature(shape);
            final List<Long> streams = (List<Long>) shape.get("streams");
            final List<Map<String, Object>> examples = (List<Map<String, Object>>) shape.get("examples");
            final Map<String, Object> entry = entry("signature", signature, "new",
                    !Boolean.TRUE.equals(shape.get("known")), "count", count(shape),
                    "share_percent", Shapes.share(counts, signature),
                    "streams", new ArrayList<>(streams.subList(0, Math.min(10, streams.size()))),
                    "example", examples.isEmpty()
                            ? null
                            : head(String.valueOf(examples.getFirst().get("text")), 500),
                    "locations", examples.stream().map(example -> example.get("location")).toList());
            if (drop.containsKey(signature)) {
                entry.put("handling", "drop");
                entry.put("reason", drop.get(signature));
            }
            shown.add(entry);
        }
        return shown;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> locations(final List<Map<String, Object>> toStep,
                                                       final Map<String, String> drop) {
        final List<Map<String, Object>> locations = new ArrayList<>();
        for (final Map<String, Object> shape : toStep) {
            final String signature = signature(shape);
            for (final Map<String, Object> example : (List<Map<String, Object>>) shape.get("examples")) {
                final Map<String, Object> location = new LinkedHashMap<>((Map<String, Object>) example.get(
                        "location"));
                location.put("shape", signature);
                if (drop.containsKey(signature)) {
                    location.put("expect", "none");
                }
                locations.add(location);
            }
        }
        return locations;
    }

    // Writes the survey into the build's survey doc (creating it the first time).
    private Map<String, Object> keep(final ToolCallContext context,
                                     final SurveyRecords kept,
                                     final Map<String, Object> result,
                                     final Map<String, Map<String, Object>> shapes) {
        return FeedSampling.keep(surveyDocuments.records(context.session()), kept.build(), kept.record(),
                kept.state(), result, shapes, kept.examples());
    }

    private static ArraySchema idsSchema(final String description) {
        return ArraySchema.builder()
                .items(new IntegerSchema(ToolDescription.of("A stream id.")))
                .description(description)
                .build();
    }

    // Streams found as the user, stopping when the call's time is up.
    private List<Meta> find(final ExpressionOperator expression, final int limit, final boolean newestFirst) {
        ToolDeadline.check();
        return NullSafe.list(NullSafe.get(streamReader.find(expression, limit, newestFirst), ResultPage::getValues))
                .stream()
                .map(MetaRow::getMeta)
                .toList();
    }

    // The first message of finding streams is said; the rest now and then.
    private static Consumer<String> finding(final CallProgress progress) {
        final boolean[] first = {true};
        return message -> {
            if (first[0]) {
                first[0] = false;
                progress.say(message);
            } else {
                progress.nowAndThen(message);
            }
        };
    }

    // True once the call's time limit has passed; the call stops at once if its thread is interrupted (the run
    // stopped, or the client gave up on the request).
    private static boolean timeIsUp() {
        try {
            ToolDeadline.check();
            return false;
        } catch (final ToolTimeoutException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw e;
            }
            return true;
        }
    }

    private static <T> T withinTimeLimit(final ToolCallContext context, final Supplier<T> work) {
        final Optional<Duration> limit = context.timeLimit();
        return limit.isPresent()
                ? ToolDeadline.run(limit.get(), work)
                : work.get();
    }

    private static String limitText(final ToolCallContext context) {
        return context.timeLimit()
                .map(limit -> limit.toMillis() % 1000 == 0
                        ? limit.toSeconds() + " s"
                        : limit.toMillis() + " ms")
                .orElse("none");
    }

    private static Map<String, String> span(final List<Meta> metas) {
        final List<Long> times = metas.stream().map(Meta::getCreateMs).filter(ms -> ms != 0).toList();
        if (times.isEmpty()) {
            return Map.of();
        }
        final Map<String, String> span = new LinkedHashMap<>();
        span.put("oldest", FeedSampling.iso(times.stream().mapToLong(Long::longValue).min().orElseThrow()));
        span.put("newest", FeedSampling.iso(times.stream().mapToLong(Long::longValue).max().orElseThrow()));
        return span;
    }

    // An integer argument within bounds, or null if it was not given.
    private Integer bounded(final ToolCall call, final String name, final int min, final int max) {
        if (stringArg(call, name) == null) {
            return null;
        }
        final Integer value = intArg(call, name);
        if (value == null || value < min || value > max) {
            throw new ToolRefusal(name + " must be a whole number from " + min + " to " + max + ", not '"
                                  + stringArg(call, name) + "'");
        }
        return value;
    }

    private static int orDefault(final Integer value, final int otherwise) {
        return value == null
                ? otherwise
                : value;
    }

    private static long count(final Map<String, Object> shape) {
        return ((Number) shape.get("count")).longValue();
    }

    private static String signature(final Map<String, Object> shape) {
        return String.valueOf(shape.get("signature"));
    }

    // The first characters of a text, counted as Python counts them (code points).
    private static String head(final String text, final int chars) {
        return text.codePointCount(0, text.length()) <= chars
                ? text
                : text.substring(0, text.offsetByCodePoints(0, chars));
    }

    private static Map<String, Object> entry(final Object... keysAndValues) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }


    // --------------------------------------------------------------------------------


    /// A call's arguments.
    ///
    /// @param feed            The feed named, or null.
    /// @param streamType      The stream type to sample.
    /// @param streamIds       The streams named, or none.
    /// @param maxStreams      Streams to read in the call.
    /// @param skipStreamIds   Streams already read.
    /// @param knownSignatures Shape signatures from earlier calls.
    /// @param maxChars        The most characters of a part, or null for the default.
    /// @param maxParts        The most parts of a stream.
    /// @param maxRecords      The most records of a stream, or null for the default.
    /// @param examples        The most examples a shape.
    /// @param build           The build to keep the survey in, or null.
    private record Request(String feed,
                           String streamType,
                           List<Long> streamIds,
                           int maxStreams,
                           List<Long> skipStreamIds,
                           List<String> knownSignatures,
                           Integer maxChars,
                           int maxParts,
                           Integer maxRecords,
                           int examples,
                           String build) {

    }

    /// How much of each stream is read.
    ///
    /// @param maxChars   The most characters of a part.
    /// @param maxParts   The most parts of a stream.
    /// @param maxRecords The most records of a stream.
    private record Limits(int maxChars, int maxParts, int maxRecords) {

    }

    /// Where the survey is kept.
    ///
    /// @param build    The build, or null.
    /// @param record   The build's survey doc for the feed as read before the survey, or null.
    /// @param state    The state read from it, or null.
    /// @param examples The most examples kept a shape.
    private record SurveyRecords(String build, SurveyRecord record, Map<String, Object> state, int examples) {

    }

    /// What the result is worked out from, besides what was read.
    ///
    /// @param feed       The feed.
    /// @param span       The feed's (or the streams') time range.
    /// @param chosen     The streams named, or none.
    /// @param exhausted  True if fewer streams were left to pick than the call reads.
    /// @param known      True if shapes were known from earlier calls.
    /// @param state      The survey doc's state, or null.
    /// @param picked     How many streams were picked to read.
    /// @param maxStreams Streams the call reads.
    /// @param limits     How much of each stream was read.
    private record Outcome(String feed,
                           Map<String, String> span,
                           List<Meta> chosen,
                           boolean exhausted,
                           boolean known,
                           Map<String, Object> state,
                           int picked,
                           int maxStreams,
                           Limits limits) {

    }

    // What has been read so far.
    private static final class Reading {

        private final List<Map<String, Object>> perStream = new ArrayList<>();
        private long recordsRead;
        private String format;
        private RecordSplitter.Chunk firstChunk;
        private boolean stopped;

        private void stop() {
            stopped = true;
        }

        private boolean stopped() {
            return stopped;
        }

        private RecordSplitter.Chunk firstChunk() {
            return firstChunk;
        }

        // The streams read: every stream with a records entry.
        private List<Long> streamsRead() {
            return perStream.stream()
                    .filter(stream -> stream.containsKey("records"))
                    .map(stream -> ((Number) stream.get("stream")).longValue())
                    .toList();
        }
    }
}
