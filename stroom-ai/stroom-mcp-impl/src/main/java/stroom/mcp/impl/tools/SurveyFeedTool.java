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
import stroom.ai.impl.tools.RecordPicks;
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
import stroom.ai.impl.tools.sample.KindFields;
import stroom.ai.impl.tools.sample.RecordSplitter;
import stroom.ai.impl.tools.sample.SampleProfiler;
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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.LongStream;

/// `survey_feed` - samples a feed's streams spread over its lifetime and groups their records into shapes (kinds of
/// event), keeping the survey in a build's survey doc when given a build. It reads streams as the user, and writes only
/// the survey doc, through [WriteGuard].
///
/// It is heavy (a run slot over `/mcp`): it stops between streams when its thread is interrupted, and at the
/// session's time limit for a call it stops reading and returns what it has read, for the next call to carry on
/// from.
class SurveyFeedTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SurveyFeedTool.class);

    static final String NAME = "survey_feed";
    static final String FEED_ARG = "feed";
    static final String STREAM_TYPE_ARG = "stream_type";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String MAX_STREAMS_ARG = "max_streams";
    static final String SKIP_STREAM_IDS_ARG = "skip_stream_ids";
    static final String KNOWN_SIGNATURES_ARG = "known_signatures";
    /// Said when a stream's delimited records hold quoted values over several lines (seen in testing: a Data
    /// Splitter built from the first lines, which held none, split each such record into pieces in processing).
    static final String QUOTED_NEWLINES_NOTE = "Some quoted values in streams {streams} run over several lines. "
                                               + "The Data Splitter must read a record to its end outside quotes "
                                               + "(build_data_splitter spec quoted_newlines=true, saved over the "
                                               + "converter), or processing splits each such record into pieces: "
                                               + "the first translated with fields missing, the rest unmatched.";
    static final String MAX_CHARS_ARG = "max_chars_per_stream";
    /// The most windows read from further into a part than its head, spread to its end.
    static final int WINDOWS = 8;
    // How many times the records a call reads a head is split into, for those read to be spread over them.
    static final int SPLIT_MORE = 4;
    /// The most characters in a window.
    static final int WINDOW_CHARS = 250_000;
    // Further examples a shape shows where they differ, for how many shapes, and how long each.
    private static final int MORE_EXAMPLES = 2;
    private static final int SHAPES_WITH_MORE = 20;
    private static final int MORE_EXAMPLE_CHARS = 300;
    // Formats of one record a line, read from a window as from the head; not XML, a JSON document, or records of
    // several lines, whose records a window can't be started in.
    // Lines of mixed formats too (seen in testing: a NetScaler stream read to its 10,000th record was never offered
    // deeper, and kinds only further in were found at processing).
    private static final Set<String> WINDOWED = Set.of(RecordSplitter.DELIMITED, RecordSplitter.JSON_LINES,
            SampleProfiler.KEY_VALUE_FORMAT, SampleProfiler.CEF_FORMAT, SampleProfiler.CRI,
            RecordSplitter.UNKNOWN_TEXT, SampleProfiler.MIXED_LINES);
    static final String MAX_PARTS_ARG = "max_parts_per_stream";
    /// The most parts of a stream read.
    static final int MAX_PARTS = 20;
    /// The parts of each of a feed's streams read by default when it is sampled across its life.
    static final int FEED_PARTS = 3;
    static final String MAX_RECORDS_ARG = "max_records_per_stream";
    static final String READ_DEEPER_ARG = "read_deeper";
    static final String KIND_FIELD_ARG = "kind_field";
    static final String ACCEPT_ARG = "accept_as_is";
    // The answers to going ahead with the survey as it is, and to streams that can't be read further in a call.
    static final String ACCEPT = "Go ahead with the survey as it is";
    static final String KEEP_SURVEYING = "Keep surveying";
    static final String STOP_THERE = "Stop and accept them as read";
    static final String STEP_FURTHER = "Step records further in first";
    // The share of a part's budget its windows take when it is read in order from its start: a quarter.
    private static final int WINDOW_SHARE = 4;
    static final String EXAMPLES_ARG = "examples_per_shape";
    static final String BUILD_ARG = "build";

    /// The most characters read of a part, asked for.
    static final int MAX_CHARS = 5_000_000;
    /// The most fields naming the kind of event summed in `kinds`.
    static final int MAX_KIND_FIELDS = 6;
    /// The most values of each listed in `kinds`.
    static final int MAX_KIND_VALUES = 25;

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
                                                           + "max_records_per_stream counts records. Default 3, "
                                                           + "or all (up to 20) for stream_ids and a feed's last "
                                                           + "streams.")
                        .addIntegerProperty(MAX_RECORDS_ARG, "Records read from each part of a stream: default 1,000, "
                                                             + "or 10,000 for stream_ids and a feed's last streams "
                                                             + "(read in order from the start, so the user can have "
                                                             + "a part read deeper).")
                        .addStringProperty(READ_DEEPER_ARG, "Only when the client shows no forms: what the user "
                                                            + "said, in the chat, when asked whether to read the "
                                                            + "parts read in part deeper ('stop', a number of "
                                                            + "records more, or 'to the end').")
                        .addStringProperty(KIND_FIELD_ARG, "The field that tells the records' kinds of event apart, "
                                                           + "as the user said (e.g. 'msg_act'; two as 'a, b'), or "
                                                           + "'" + KindFields.ONE_KIND_ANSWER + "'. Only what the "
                                                           + "user said: when the tool isn't sure which field it is, "
                                                           + "it asks them (or, with no forms, says to ask in the "
                                                           + "chat). Kept for the build's survey; a different one "
                                                           + "starts the survey afresh.")
                        .addStringProperty(ACCEPT_ARG, "Only when the user wants to go ahead with the build's survey "
                                                       + "as it is, before it is covered: 'true' (they confirm in a "
                                                       + "form), or, when the client shows no forms, what they said "
                                                       + "in the chat. Reads nothing: the survey is then covered as "
                                                       + "far as the user chose, and processing can go ahead.")
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
            return JsonResults.of(withinTimeLimit(context, () -> survey(call, context, request, feed, chosen)));
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
                bounded(call, MAX_PARTS_ARG, 1, MAX_PARTS),
                maxRecords,
                orDefault(bounded(call, EXAMPLES_ARG, 1, 10), 2),
                stringArg(call, BUILD_ARG),
                stringArg(call, READ_DEEPER_ARG),
                stringArg(call, KIND_FIELD_ARG),
                stringArg(call, ACCEPT_ARG));
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
        if (chosen.isEmpty() && request.maxStreams() < FeedSampling.SURVEY_STREAMS
            && holdsMore(feed, request.streamType(), request.maxStreams())) {
            askFewerStreams(call, context, feed, request.maxStreams());
        } else if (!chosen.isEmpty() && chosen.size() < FeedSampling.SURVEY_STREAMS && !inABuild(feed)
                   && holdsMore(feed, request.streamType(), chosen.size())) {
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

    // Whether the feed holds more streams of the type than a call would read: if not, reading fewer than the default
    // is no choice (seen in testing: a feed of one stream, and the user asked to agree to survey only that one).
    // True when it can't be told.
    private boolean holdsMore(final String feed, final String streamType, final int count) {
        try {
            final ExpressionOperator terms = ExpressionOperator.builder().op(Op.AND)
                    .addTextTerm(MetaFields.FEED, Condition.EQUALS, feed)
                    .addTextTerm(MetaFields.TYPE, Condition.EQUALS, streamType)
                    .build();
            return find(terms, count + 1, true).size() > count;
        } catch (final RuntimeException e) {
            return true;
        }
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

    private Map<String, Object> survey(final ToolCall call,
                                       final ToolCallContext context,
                                       final Request request,
                                       final String feed,
                                       final List<Meta> chosen) {
        final String build = request.build();
        final SurveyRecord record = build == null
                ? null
                : surveyDocuments.load(build, feed).orElse(null);
        final Map<String, Object> read = record == null
                ? null
                : SurveyDoc.readState(record.text());
        // Streams deleted since they were surveyed: their shapes are found again where the data is now.
        final Set<Long> gone = read == null
                ? Set.of()
                : GoneStreams.of(streamReader, SurveyDoc.streams(read));
        final Map<String, Object> surveyed = read == null
                ? null
                : SurveyDoc.withoutStreams(read, gone);
        // The kind field the user gives now, and the one the survey kept: another one starts the survey afresh, as
        // every kind of event it found was told apart by the other.
        final List<String> givenKinds = KindFields.parse(request.kindField());
        final Map<String, Object> keptKinds = surveyed == null
                ? Map.of()
                : JsonValues.mapOrEmpty(surveyed.get(SurveyDoc.KIND_FIELDS));
        final boolean kindsChanged = givenKinds != null && !keptKinds.isEmpty()
                                     && !givenKinds.equals(JsonValues.strings(keptKinds.get("fields")));
        final Map<String, Object> state = kindsChanged
                ? null
                : surveyed;
        final Set<String> dropped = read == null || state == null
                ? Set.of()
                : droppedShapes(read, state);
        if (request.acceptAsIs() != null) {
            return acceptAsIs(call, context, request, feed, record, state);
        }
        final Set<Long> skip = new TreeSet<>(request.skipStreamIds());
        final Set<String> known = new LinkedHashSet<>(request.knownSignatures());
        known.removeAll(dropped);
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
                : FeedSampling.FEED_CHARS), orDefault(request.maxParts(), whole
                // Streams read whole are read through every part: seen in testing, a feed's latest streams uploaded
                // again as one stream of five parts was surveyed through three.
                ? MAX_PARTS
                : FEED_PARTS), orDefault(request.maxRecords(), whole
                ? FeedSampling.SAMPLE_RECORDS
                : FeedSampling.FEED_RECORDS), whole);
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
            final List<Long> inPart = unsettled(state);
            if (state != null && state.get(SurveyDoc.ACCEPTED) != null) {
                // The user went ahead with the survey as it is: it stays covered as far as they chose.
                result.put("coverage", "covered as far as the user chose: they went ahead with the survey as it is");
                result.put("hint", "The user went ahead with the survey as it is: go on with the plan (build_status).");
            } else if (inPart.isEmpty()) {
                result.put("hint", "Every stream in the feed has been read: the survey is complete.");
            } else {
                // Seen in testing: a stream read to its 10,000th record of many more, then a call with no streams
                // left to pick said the survey was complete, and kinds only further in were found at processing.
                result.put("saturated", false);
                result.put("coverage", "NOT COVERED: stream(s) " + inPart + " read only in part");
                result.put("hint", "Every stream in the feed has been read, but " + inPart + " only in part: call "
                                   + NAME + " with stream_ids " + inPart + " to read them further (the user is "
                                   + "asked how far).");
            }
            noteGone(result, gone, dropped);
            return keep(context, kept, result, Map.of());
        }
        if (streams.isEmpty()) {
            throw new ToolRefusal("No " + request.streamType() + " streams in feed '" + feed + "'");
        }
        final Shapes shapes = new Shapes(new ArrayList<>(known), request.examples());
        // The first stream's head, read to choose the kind fields, read once: the survey reads on from it.
        final Map<Long, RawHead> peeked = new LinkedHashMap<>();
        final Map<String, Object> kinds;
        if (givenKinds != null) {
            kinds = kindsKept(asTheRecordsSpellThem(givenKinds, peekKinds(streams.getFirst(), limits, peeked)),
                    "user");
        } else if (!keptKinds.isEmpty()) {
            kinds = keptKinds;
        } else {
            // Not once the call is stopped: nothing more is read.
            final Optional<KindFields.Choice> choice = Thread.currentThread().isInterrupted()
                    ? Optional.empty()
                    : peekKinds(streams.getFirst(), limits, peeked);
            if (choice.isEmpty()) {
                kinds = null;
            } else if (choice.get().certain() && choice.get().fields().isEmpty()) {
                // No field to tell kinds apart in this head: chosen again from a later call's, not fixed by one.
                kinds = null;
            } else if (choice.get().certain()) {
                kinds = kindsKept(choice.get().fields(), "tool");
            } else {
                final Optional<String> answer = consent.answer(call, context, NAME, kindIntro(feed, choice.get()),
                        "Which field tells the kinds of event apart?", choice.get().options());
                if (answer.isEmpty()) {
                    return needsKindField(feed, choice.get());
                }
                final List<String> said = KindFields.fromAnswer(answer.get(), choice.get());
                if (said.isEmpty()) {
                    throw new ToolRefusal("The user's answer ('" + answer.get() + "') names none of the fields "
                                          + "put to them: ask them in the chat which field tells the kinds of event "
                                          + "apart, and call again with " + KIND_FIELD_ARG + ".");
                }
                kinds = kindsKept(said, "user");
            }
        }
        if (kinds != null) {
            shapes.useKindFields(JsonValues.strings(kinds.get("fields")));
        }
        // Parts the user had read deeper that a call's time cut short: read on from where they stopped, not read
        // again from their start (which would count their records twice).
        final Map<String, SurveyDepth.Part> earlier = depthOf(state);
        final List<SurveyDepth.Part> pending = earlier.values().stream()
                .filter(part -> !part.settled() && part.target() > part.readTo())
                .filter(part -> streams.stream().anyMatch(meta -> meta.getId() == part.stream()))
                .toList();
        final Set<Long> continuing = new TreeSet<>();
        pending.forEach(part -> continuing.add(part.stream()));
        final Reading reading = read(context, progress, feed, streams.stream()
                .filter(meta -> !continuing.contains(meta.getId()))
                .toList(), shapes, limits, peeked);
        if (reading.firstChunk() == null && pending.isEmpty()) {
            if (reading.stopped()) {
                throw new ToolTimeoutException();
            }
            throw new ToolRefusal("Nothing readable in the sampled streams of '" + feed + "'");
        }
        if (reading.format == null && state != null) {
            reading.format = String.valueOf(state.get("format"));
        }
        final List<SurveyDepth.Part> depth = limits.whole()
                ? deepen(call, context, request, feed, reading, shapes, pending, earlier)
                : List.of();
        final Map<String, Object> result = result(new Outcome(feed, span, chosen, exhausted, !known.isEmpty(),
                state, streams.size(), request.maxStreams(), limits), reading, shapes, context);
        noteDepth(result, reading, depth);
        settleInPart(call, context, request, result, reading, depth, state, !chosen.isEmpty() || exhausted);
        noteGone(result, gone, dropped);
        if (kinds != null) {
            result.put(SurveyDoc.KIND_FIELDS, kinds);
        }
        if (kindsChanged) {
            result.put("kind_field_changed", "The user's kind field differs from the one the survey kept ("
                                             + keptKinds.get("fields") + "): the survey started afresh, every "
                                             + "stream read again and the kinds of event found again.");
        }
        return keep(context, kept, result, shapes.shapes());
    }

    // The user's kind fields as the records spell them; refused when the records have none of them.
    private static List<String> asTheRecordsSpellThem(final List<String> given,
                                                      final Optional<KindFields.Choice> peeked) {
        if (peeked.isEmpty() || KindFields.oneKind(given)) {
            return given;
        }
        final List<String> spelt = KindFields.asRecordsSpellThem(given, peeked.get().recordFields());
        if (spelt.isEmpty()) {
            throw new ToolRefusal(KIND_FIELD_ARG + " " + given + " names no field of the records: they have "
                                  + new TreeSet<>(peeked.get().recordFields()) + ". Ask the user which one they "
                                  + "meant.");
        }
        return spelt;
    }

    private static Map<String, Object> kindsKept(final List<String> fields, final String by) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("fields", List.copyOf(fields));
        out.put("by", by);
        return out;
    }

    // The kind fields the first stream's start suggests, when its records are structured (delimited, JSON,
    // key=value); empty for text, or when it can't be read.
    private Optional<KindFields.Choice> peekKinds(final Meta first,
                                                  final Limits limits,
                                                  final Map<Long, RawHead> peeked) {
        try {
            final RawHead head = streamReader.readHead(first.getId(), 0, limits.maxChars());
            peeked.put(first.getId(), head);
            final RecordSplitter.Chunk chunk = RecordSplitter.split(head.text(), limits.maxRecords() * SPLIT_MORE,
                    head.truncated());
            final List<Map<String, Object>> parsed = NullSafe.list(chunk.parsed()).stream()
                    .filter(Objects::nonNull)
                    .toList();
            return parsed.isEmpty() || !KindFields.structured(chunk.format())
                    ? Optional.empty()
                    : Optional.of(KindFields.choose(parsed));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No kind fields from stream {}: {}", first.getId(), e.getMessage()));
            return Optional.empty();
        }
    }

    // The form's message: which fields could tell the kinds apart, in plain text.
    private static String kindIntro(final String feed, final KindFields.Choice choice) {
        return "No field of feed '" + feed + "' is named like a kind of event (type, action...), but these could "
               + "tell its kinds apart:\n" + choice.said();
    }

    // With no forms: the agent asks the user which field, in the chat.
    private static Map<String, Object> needsKindField(final String feed, final KindFields.Choice choice) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("feed", feed);
        result.put("status", "needs_kind_field");
        result.put("candidates", choice.candidates().stream().map(candidate -> entry("field", candidate.field(),
                "values", candidate.values().subList(0, Math.min(10, candidate.values().size())), "records",
                candidate.records())).toList());
        result.put("hint", "Nothing was surveyed yet. No field is named like a kind of event, but these could tell "
                           + "the kinds apart. Ask the user in the chat which one does (show them the candidates "
                           + "and their values), or whether the records are all one kind, then call " + NAME
                           + " again with " + KIND_FIELD_ARG + " as they said. Don't choose for them.");
        return result;
    }

    // The streams the survey state says were read only in part, less those whose parts the user settled (read to
    // their end, or chose to stop), and those too big to read further that they chose to stop at.
    private static List<Long> unsettled(final Map<String, Object> state) {
        if (state == null) {
            return List.of();
        }
        final Map<Long, Boolean> settled = new LinkedHashMap<>();
        depthOf(state).values().forEach(part -> settled.merge(part.stream(), part.settled(), Boolean::logicalAnd));
        final Set<Long> stopped = stoppedStreams(state);
        return JsonValues.listOrEmpty(state.get(SurveyDoc.STREAMS_IN_PART)).stream()
                .map(id -> ((Number) id).longValue())
                .filter(id -> !settled.getOrDefault(id, false) && !stopped.contains(id))
                .toList();
    }

    // The streams too big to read further in a call that the user chose to stop at.
    private static Set<Long> stoppedStreams(final Map<String, Object> state) {
        final Set<Long> out = new TreeSet<>();
        if (state != null) {
            JsonValues.listOrEmpty(state.get(SurveyDoc.STOPPED_STREAMS))
                    .forEach(id -> out.add(((Number) id).longValue()));
        }
        return out;
    }

    // The user goes ahead with the build's survey as it is (asked for by the user: seen in testing, the survey of a
    // feed of some 950 streams couldn't be covered, the user asked to go ahead with a limited sample, and there was
    // nowhere to record it, so the agent kept surveying). Confirmed in a form, or as they said in the chat; reads
    // nothing; the survey is then covered as far as they chose.
    private Map<String, Object> acceptAsIs(final ToolCall call,
                                           final ToolCallContext context,
                                           final Request request,
                                           final String feed,
                                           final SurveyRecord record,
                                           final Map<String, Object> state) {
        if (request.build() == null || state == null) {
            throw new ToolRefusal("Nothing surveyed yet" + (request.build() == null
                    ? ": give build"
                    : " for feed '" + feed + "' in build '" + request.build() + "'") + ": survey it first, then the "
                                  + "user may go ahead with it as it is.");
        }
        final int read = JsonValues.listOrEmpty(state.get("streams_read")).size();
        final int kinds = JsonValues.mapOrEmpty(state.get("shapes")).size();
        final List<Long> inPart = unsettled(state);
        final String said = read + " stream(s) of feed '" + feed + "' surveyed, " + kinds + " kind(s) of event found"
                            + (inPart.isEmpty()
                ? ""
                : ", stream(s) " + inPart + " read only in part");
        final String given = request.acceptAsIs().strip();
        final Optional<String> answer = consent.answer(call, context, NAME, said + ". Going ahead with the survey "
                                                                            + "as it is, kinds of event only in "
                                                                            + "streams not read go to the mapping's "
                                                                            + "catch-all rules, until review_coverage "
                                                                            + "finds them after processing.",
                "Go ahead with the survey as it is?", List.of(ACCEPT, KEEP_SURVEYING));
        final String answered = answer.orElse("true".equalsIgnoreCase(given)
                ? null
                : given);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("feed", feed);
        if (answered == null) {
            result.put("status", "needs_user");
            result.put("hint", "Ask the user in the chat whether to go ahead with the survey as it is (" + said
                               + "), then call " + NAME + " again with " + ACCEPT_ARG + " as they said.");
            return result;
        }
        if (!agreesToGoAhead(answered)) {
            result.put("user_says", answered);
            result.put("hint", "The user wants the survey to go on: call " + NAME + " build=" + request.build()
                               + " until it is covered.");
            return result;
        }
        result.put("streams_read", List.of());
        result.put("records_read", 0);
        result.put("saturated", true);
        result.put("coverage", "covered as far as the user chose: they went ahead with the survey as it is (" + said
                               + ")");
        result.put(SurveyDoc.ACCEPTED, entry("at", FeedSampling.iso(System.currentTimeMillis()),
                "streams_read", read));
        result.put("shapes", List.of());
        result.put("new_shapes", 0);
        result.put("locations", List.of());
        result.put("hint", "The user went ahead with the survey as it is: go on with the plan (build_status). "
                           + "review_coverage after processing finds any kind of event the survey didn't see.");
        return keep(context, new SurveyRecords(request.build(), record, state, request.examples()), result,
                Map.of());
    }

    // Whether the user's answer goes ahead: the button, or their own yes.
    private static boolean agreesToGoAhead(final String answer) {
        final String text = answer.strip().toLowerCase(Locale.ROOT);
        return ACCEPT.equalsIgnoreCase(answer.strip()) || text.startsWith("yes") || text.startsWith("go ahead")
               || text.startsWith("accept") || text.startsWith("ok") || text.startsWith("proceed");
    }

    // Streams read only in part for which reading deeper isn't offered (past the parts a call reads, records over
    // several lines, a call's time cut, or the most a call reads): put to the user once the survey has had to read
    // them whole (given by id, or the feed's last), to stop and accept them as read or step records further in;
    // stopping is kept, and settles them (asked for by the user: seen in testing, three 20,000-30,000-record
    // streams that could never be reported read whole, the user's choice to stop had nowhere to go, and the agent
    // was told to raise max_chars_per_stream).
    private void settleInPart(final ToolCall call,
                              final ToolCallContext context,
                              final Request request,
                              final Map<String, Object> result,
                              final Reading reading,
                              final List<SurveyDepth.Part> depth,
                              final Map<String, Object> state,
                              final boolean wholeOnly) {
        if (!wholeOnly || reading.stopped()) {
            return;
        }
        final Set<Long> deep = new TreeSet<>();
        depth.forEach(part -> deep.add(part.stream()));
        final Set<Long> stoppedBefore = stoppedStreams(state);
        final List<Long> other = reading.perStream.stream()
                .filter(stream -> Boolean.TRUE.equals(stream.get("head_only")))
                .map(stream -> ((Number) stream.get("stream")).longValue())
                .filter(id -> !deep.contains(id) && !stoppedBefore.contains(id))
                .toList();
        final boolean depthOpen = depth.stream().anyMatch(part -> !part.settled());
        if (other.isEmpty()) {
            // Every stream read in part is read deeper or stopped at before: covered once no part is left open.
            if (!depthOpen && !stoppedBefore.isEmpty() && !Boolean.TRUE.equals(result.get("saturated"))) {
                result.put("saturated", true);
                result.put("coverage", "covered as far as the user chose: stream(s) " + stoppedBefore + " read only "
                                       + "in part, too big to read further in a call, and they chose to stop there");
            }
            return;
        }
        final String said = "Stream(s) " + other + " can't be read further in a call (records read: per_stream)";
        // The read_deeper answer only when it answers this question (stop, or step): "1,000 more" was for parts that
        // can be read deeper.
        final String given = NullSafe.string(request.readDeeper()).strip();
        final boolean answersThis = SurveyDepth.decide(given).stop() || STOP_THERE.equalsIgnoreCase(given)
                                    || given.toLowerCase(Locale.ROOT).contains("step");
        final Optional<String> answer = answersThis
                ? Optional.of(given)
                : consent.answer(call, context, NAME, said + ": kinds of event only further in them weren't seen.",
                        "Stop and accept them as read, or step records further in first?",
                        List.of(STOP_THERE, STEP_FURTHER));
        if (answer.isEmpty()) {
            result.put("coverage", "NOT COVERED: " + said + "; the user decides whether to stop and accept them as "
                                   + "read, or step records further in first");
            result.put("hint", "Ask the user in the chat whether to stop and accept stream(s) " + other + " as read, "
                               + "or step records further in first, then call " + NAME + " again with stream_ids "
                               + other + " and " + READ_DEEPER_ARG + " as they said. Don't raise "
                               + MAX_CHARS_ARG + ": a call reads no more of them.");
            return;
        }
        if (SurveyDepth.decide(answer.get()).stop() || STOP_THERE.equalsIgnoreCase(answer.get().strip())) {
            result.put(SurveyDoc.STOPPED_STREAMS, other);
            if (!depthOpen) {
                result.put("saturated", true);
                result.put("coverage", "covered as far as the user chose: stream(s) " + other + " read only in part, "
                                       + "too big to read further in a call, and they chose to stop there");
                result.put("hint", "Covered as far as the user chose: go on with the plan (build_status).");
            }
            return;
        }
        result.put("coverage", "NOT COVERED: " + said + "; the user chose to step records further in first");
        result.put("hint", "Step records further in these streams with step_records (their windows in per_stream), "
                           + "then ask the user again whether to stop there, and call " + NAME + " with stream_ids "
                           + other + " and " + READ_DEEPER_ARG + " as they say. Don't raise " + MAX_CHARS_ARG
                           + ": a call reads no more of them.");
    }

    // Each part's depth as the survey state keeps it, by stream:part.
    private static Map<String, SurveyDepth.Part> depthOf(final Map<String, Object> state) {
        final Map<String, SurveyDepth.Part> out = new LinkedHashMap<>();
        if (state != null) {
            JsonValues.mapOrEmpty(state.get(SurveyDepth.DEPTH)).forEach((key, value) -> {
                @SuppressWarnings("unchecked") final Map<String, Object> map = (Map<String, Object>) value;
                out.put(String.valueOf(key), SurveyDepth.Part.fromMap(map));
            });
        }
        return out;
    }

    // The parts read in part: put to the user (or their answer given as read_deeper, where the client shows no
    // forms), read deeper as far as they say, and each kept with their choice. A part they chose to stop before isn't
    // asked about again.
    private List<SurveyDepth.Part> deepen(final ToolCall call,
                                          final ToolCallContext context,
                                          final Request request,
                                          final String feed,
                                          final Reading reading,
                                          final Shapes shapes,
                                          final List<SurveyDepth.Part> pending,
                                          final Map<String, SurveyDepth.Part> earlier) {
        final List<SurveyDepth.Part> parts = new ArrayList<>(pending);
        final List<SurveyDepth.Part> fresh = new ArrayList<>();
        for (final SurveyDepth.Part part : reading.partial) {
            final SurveyDepth.Part before = earlier.get(part.key());
            if (before != null && before.stopped()) {
                parts.add(part.withChoice(SurveyDepth.STOP));
            } else {
                fresh.add(part);
            }
        }
        if (!fresh.isEmpty()) {
            final Optional<String> answer = NullSafe.isNonBlankString(request.readDeeper())
                    ? Optional.of(request.readDeeper())
                    : consent.answer(call, context, NAME, SurveyDepth.intro(feed, fresh), SurveyDepth.QUESTION,
                            SurveyDepth.suggestions(fresh));
            if (answer.isPresent()) {
                final SurveyDepth.Decision decision = SurveyDepth.decide(answer.get());
                fresh.forEach(part -> parts.add(part.withChoice(decision.choiceFor(part))));
            } else {
                // No forms: the agent asks the user in the chat (see noteDepth).
                parts.addAll(fresh);
            }
        }
        final List<SurveyDepth.Part> out = new ArrayList<>();
        for (final SurveyDepth.Part part : parts) {
            if (!part.settled() && part.target() > part.readTo() && !timeIsUp()) {
                final SurveyDepth.Deeper deeper = SurveyDepth.readDeeper(streamReader, shapes, part,
                        SurveyFeedTool::timeIsUp);
                reading.recordsRead += deeper.records();
                out.add(deeper.part());
            } else {
                out.add(part);
            }
        }
        return out;
    }

    // What the parts read in part came to: their depth in the result (kept in the survey state), and the coverage
    // and hint said for them. Covered once every such part is read whole or the user chose to stop it, and nothing
    // else was read only in part.
    private static void noteDepth(final Map<String, Object> result,
                                  final Reading reading,
                                  final List<SurveyDepth.Part> depth) {
        if (depth.isEmpty()) {
            return;
        }
        result.put(SurveyDepth.DEPTH, depth.stream().map(SurveyDepth.Part::toMap).toList());
        result.put("records_read", reading.recordsRead);
        final Set<Long> deep = new TreeSet<>();
        depth.forEach(part -> deep.add(part.stream()));
        final boolean otherwiseInPart = reading.perStream.stream()
                .anyMatch(stream -> Boolean.TRUE.equals(stream.get("head_only"))
                                    && !deep.contains(((Number) stream.get("stream")).longValue()));
        final List<SurveyDepth.Part> open = depth.stream().filter(part -> !part.settled()).toList();
        if (open.isEmpty() && !otherwiseInPart) {
            final List<SurveyDepth.Part> stopped = depth.stream().filter(SurveyDepth.Part::stopped).toList();
            result.put("saturated", true);
            result.put("coverage", stopped.isEmpty()
                    ? "covered: every part read to its end"
                    : "covered as far as the user chose: " + stopped.stream()
                            .map(part -> "stream " + part.stream() + " part " + (part.part() + 1) + " read to "
                                         + StreamReader.thousands(part.readTo()) + " of about "
                                         + StreamReader.thousands(part.estimate()) + " records")
                            .toList() + "; they chose to stop there");
            return;
        }
        if (open.isEmpty()) {
            return;
        }
        result.put("saturated", false);
        final boolean unasked = open.stream().anyMatch(part -> part.choice() == null);
        result.put("coverage", "NOT COVERED: " + open.size() + " part(s) read only in part: " + open.stream()
                .map(part -> "stream " + part.stream() + " part " + (part.part() + 1) + " to "
                             + StreamReader.thousands(part.readTo()) + " of about "
                             + StreamReader.thousands(part.estimate()))
                .toList() + (unasked
                ? ""
                : "; the time for a call ran out before they were read as far as the user asked"));
        result.put("hint", unasked
                ? "This client shows no forms: ask the user in the chat whether to read these parts deeper and how "
                  + "far (stop, a number of records more, or to the end), then call survey_feed again with the "
                  + "same streams and read_deeper=<what they said>."
                : "Call survey_feed again with the same streams: it reads on from where it stopped, as far as the "
                  + "user asked.");
    }

    // The shapes the survey knew that pruning gone streams left with no example.
    private static Set<String> droppedShapes(final Map<String, Object> before, final Map<String, Object> after) {
        final Set<String> dropped = new LinkedHashSet<>(JsonValues.mapOrEmpty(before.get("shapes")).keySet());
        dropped.removeAll(JsonValues.mapOrEmpty(after.get("shapes")).keySet());
        return dropped;
    }

    // Says which streams surveyed before are gone, and that their shapes are being found again.
    private static void noteGone(final Map<String, Object> result, final Set<Long> gone, final Set<String> dropped) {
        if (gone.isEmpty()) {
            return;
        }
        result.put("gone_streams", new ArrayList<>(gone));
        result.put("gone_note", gone.size() + " stream(s) surveyed before are gone (deleted, or no longer there): "
                                + gone + ". They are no longer counted as read, nor their examples as locations"
                                + (dropped.isEmpty()
                ? ""
                : "; " + dropped.size() + " shape(s) seen only in them are surveyed again, so they get "
                  + "locations in the streams there are now") + ". The survey is not covered until it has been "
                                + "read again.");
    }

    // Each stream's head, its records added to the shapes.
    private Reading read(final ToolCallContext context,
                         final CallProgress progress,
                         final String feed,
                         final List<Meta> streams,
                         final Shapes shapes,
                         final Limits limits,
                         final Map<Long, RawHead> peeked) {
        final Reading reading = new Reading();
        reading.peeked.putAll(peeked);
        progress.say("Reading the head of " + streams.size() + " stream" + (streams.size() != 1
                ? "s"
                : "") + " of " + feed);
        int n = 0;
        try {
            for (final Meta meta : streams) {
                n++;
                // A head read already (to choose the kind fields) is surveyed, however late.
                if (timeIsUp() && !reading.peeked.containsKey(meta.getId())) {
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

    // One stream's parts, each up to its own budget of records (asked for by the user: a source can write different
    // data to different parts, so a budget the parts shared could miss kinds of event in the later ones).
    private void readStream(final Reading reading, final Meta meta, final Shapes shapes, final Limits limits) {
        int newHere = 0;
        long readHere = 0;
        int windowsHere = 0;
        boolean cut = false;
        boolean inPart = false;
        int parts = 1;
        int part = 0;
        while (part < Math.min(parts, limits.maxParts())) {
            if (timeIsUp() && !(part == 0 && reading.peeked.containsKey(meta.getId()))) {
                // Stopped part-way: what is read of it so far counts, as the start of the stream.
                reading.stop();
                cut = true;
                break;
            }
            final RawHead head;
            try {
                final RawHead before = part == 0
                        ? reading.peeked.remove(meta.getId())
                        : null;
                head = before != null
                        ? before
                        : streamReader.readHead(meta.getId(), part, limits.maxChars());
            } catch (final StreamReadException e) {
                // A feed's oldest streams are read too, and the likeliest to be on a volume Stroom can no longer
                // read: said for that stream, the rest of the survey still made.
                reading.perStream.add(entry("stream", meta.getId(), "part", part, "error",
                        "Stroom couldn't read it: " + e.getMessage()));
                break;
            }
            parts = head.parts();
            cut = cut || head.truncated();
            final int budget = limits.maxRecords();
            final RecordSplitter.Chunk chunk;
            try {
                // More than are read, so those read can be spread over them (see below).
                chunk = RecordSplitter.split(head.text(), budget * SPLIT_MORE, head.truncated());
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
            if (holdsLineBreaks(chunk)) {
                reading.quotedNewlinesIn.add(meta.getId());
            }
            final boolean windowed = head.truncated() && windowable(chunk);
            final List<Long> picks;
            if (limits.whole()) {
                // In order from the part's start, so reading it deeper goes on from there; the windows (a quarter of
                // the budget) a first look further in.
                final int inOrder = Math.min(chunk.records().size(), windowed
                        ? budget - budget / WINDOW_SHARE
                        : budget);
                picks = LongStream.range(0, inOrder).boxed().toList();
            } else {
                // A part bigger than its head: the head takes at most half the records, the windows spread to its
                // end the rest. A head holding more records than are read: a block at its start and the rest spread
                // over it (RecordPicks). Seen in testing: a 20,000-record stream's head filled all 10,000, no window
                // was read, and a kind of record only near its end was never seen.
                picks = windowed
                        ? RecordPicks.headAndSpread(Math.min(chunk.records().size(), Math.max(1, budget / 2)),
                                Math.max(1, budget / 2))
                        : RecordPicks.headAndSpread(chunk.records().size(), budget);
            }
            int partRead = 0;
            for (final long index : picks) {
                if (shapes.add(chunk, (int) index, meta.getId(), part)) {
                    newHere++;
                }
                partRead++;
            }
            List<List<Long>> ranges = List.of();
            if (windowed && partRead < budget && !timeIsUp()) {
                final WindowsRead read = readWindows(reading, meta, part, head, chunk, shapes, budget - partRead);
                windowsHere += read.windows();
                partRead += read.records();
                newHere += read.newShapes();
                ranges = read.ranges();
            }
            // Every record of the part read: its whole text was read, and split into fewer records than the most.
            final boolean partWhole = !head.truncated() && chunk.records().size() < budget * SPLIT_MORE
                                      && picks.size() == chunk.records().size();
            if (!partWhole) {
                inPart = true;
                if (limits.whole() && windowable(chunk)) {
                    reading.partial.add(new SurveyDepth.Part(meta.getId(), part, picks.size(), ranges,
                            chunk.header(), chunk.format(), partSize(meta.getId(), part),
                            estimate(head, meta.getId(), part, picks.size()), false, null));
                }
            }
            readHere += partRead;
            part++;
        }
        reading.recordsRead += readHere;
        final Map<String, Object> entry = entry("stream", meta.getId(), "created", meta.getCreateMs() != 0
                        ? FeedSampling.iso(meta.getCreateMs())
                        : null,
                "records", readHere, "parts", parts, "head_only", cut || parts > limits.maxParts() || inPart,
                "new_shapes", newHere);
        if (windowsHere > 0) {
            entry.put("windows", windowsHere);
        }
        entry.putAll(FeedSampling.charsetEntry(streamReader.charsetReport(meta.getId())));
        reading.perStream.add(entry);
    }

    // A part's size in bytes; 0 when it can't be told.
    private long partSize(final long streamId, final int part) {
        try {
            return streamReader.partSize(streamId, part);
        } catch (final RuntimeException e) {
            return 0;
        }
    }

    // About how many records a part holds: its size over the bytes its head's lines take, a record a line (the
    // formats read deeper are); never fewer than those read.
    private long estimate(final RawHead head, final long streamId, final int part, final long read) {
        final long size = partSize(streamId, part);
        final byte[] bytes = head.text().getBytes(StandardCharsets.UTF_8);
        final long lines = head.text().chars().filter(c -> c == '\n').count();
        if (size == 0 || bytes.length == 0 || lines == 0) {
            return read;
        }
        return Math.max(read, size * lines / bytes.length);
    }

    // Windows of a part further in than its head, to its end, their records added to the shapes where they are (a
    // record a line, after the header line).
    private WindowsRead readWindows(final Reading reading,
                              final Meta meta,
                              final int part,
                              final RawHead head,
                              final RecordSplitter.Chunk chunk,
                              final Shapes shapes,
                              final int maxRecords) {
        final List<StreamReader.Window> windows;
        try {
            windows = streamReader.readWindows(meta.getId(), part,
                    head.text().getBytes(StandardCharsets.UTF_8).length, WINDOWS, WINDOW_CHARS);
        } catch (final StreamReadException e) {
            reading.perStream.add(entry("stream", meta.getId(), "part", part, "note",
                    "only its head was read: Stroom couldn't read further in: " + e.getMessage()));
            return new WindowsRead(0, 0, 0, List.of());
        }
        int read = 0;
        int records = 0;
        int newShapes = 0;
        final List<List<Long>> ranges = new ArrayList<>();
        // Each window an equal share of the records left, so the last (the part's end) is read too: seen in testing,
        // the first windows took them all and a kind of record near the end was never seen.
        final int share = Math.max(1, maxRecords / Math.max(1, windows.size()));
        for (int w = 0; w < windows.size(); w++) {
            final StreamReader.Window window = windows.get(w);
            if (records >= maxRecords || timeIsUp()) {
                break;
            }
            final int take = Math.min(share, maxRecords - records);
            // The last window is the part's tail: its last records are read, so a kind of event only at the very end
            // is seen however few records a window has (seen in the e2e suite: the tail's first records read, its
            // last kind missed).
            final boolean tail = w == windows.size() - 1;
            final RecordSplitter.Chunk more;
            try {
                more = RecordSplitter.splitAs(chunk.format(), chunk.header() == null
                        ? window.text()
                        : chunk.header() + "\n" + window.text(), tail
                        ? take * SPLIT_MORE
                        : take, false);
            } catch (final RuntimeException e) {
                continue;
            }
            if (!more.format().equals(chunk.format()) || !Objects.equals(more.header(), chunk.header())) {
                continue;
            }
            read++;
            final long firstRecord = window.firstLine() - (chunk.header() == null
                    ? 0
                    : 1);
            final int from = tail
                    ? Math.max(0, more.records().size() - take)
                    : 0;
            final int to = Math.min(more.records().size(), from + take);
            for (int index = from; index < to; index++) {
                if (shapes.add(more, index, meta.getId(), part, firstRecord)) {
                    newShapes++;
                }
                records++;
            }
            ranges.add(List.of(firstRecord + from, (long) (to - from)));
        }
        return new WindowsRead(read, records, newShapes, ranges);
    }

    // Whether a part can be read further in than its head, from a window as from its start: a record a line. Not
    // when a record spans lines (a delimited quoted value, or mixed lines with the lines that continue an entry),
    // whose records a window can't be started in.
    private static boolean windowable(final RecordSplitter.Chunk chunk) {
        if (!WINDOWED.contains(chunk.format()) || holdsLineBreaks(chunk)) {
            return false;
        }
        return !SampleProfiler.MIXED_LINES.equals(chunk.format())
               || chunk.records().stream().noneMatch(record -> record.indexOf('\n') >= 0);
    }

    // Whether delimited records hold a quoted value over several lines, which the record splitter reads whole.
    private static boolean holdsLineBreaks(final RecordSplitter.Chunk chunk) {
        return RecordSplitter.DELIMITED.equals(chunk.format())
               && chunk.parsed().stream()
                       .filter(Objects::nonNull)
                       .flatMap(record -> record.values().stream())
                       .anyMatch(value -> value instanceof final String text && text.indexOf('\n') >= 0);
    }

    // The call's result.
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
        if (!partly.isEmpty()) {
            result.put(SurveyDoc.STREAMS_IN_PART, partly);
        }
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
        if (!reading.quotedNewlinesIn.isEmpty()) {
            result.put("quoted_newlines", QUOTED_NEWLINES_NOTE.replace("{streams}",
                    String.valueOf(List.copyOf(reading.quotedNewlinesIn))));
        }
        final Map<String, Map<String, Long>> kinds = kinds(found);
        if (!kinds.isEmpty()) {
            result.put("kinds", kinds);
        }
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
        // At the most a call reads, a stream is sampled (its head and windows to its end), never read whole: said so,
        // not to read again at the size just read (seen in testing: told to survey again with 5,000,000 after a
        // call with 5,000,000).
        final String again = outcome.limits().maxChars() >= MAX_CHARS
                ? "They are bigger than a call reads: their head and windows spread to their end were read "
                  + "(per_stream windows). Step records further in with step_records to check them; a kind of event "
                  + "in none of them may still be there, which review_coverage finds once the feed is processed"
                : "Survey them again with stream_ids=" + partly + " and max_chars_per_stream="
                  + Math.min(MAX_CHARS, (long) outcome.limits().maxChars() * 4) + " (up to 5,000,000)";
        final boolean chosen = !outcome.chosen().isEmpty();
        if (chosen && !partly.isEmpty()) {
            return "NOT COVERED: only part of stream(s) " + partly + " was read (" + recordsRead + " records: a "
                   + "head, and windows spread to the end where per_stream gives windows); "
                   + "there may be other kinds of event further in. " + again;
        }
        if (chosen) {
            return "all " + readIds.size() + " given stream(s) read whole: these are the kinds of event the sample "
                   + "holds";
        }
        if (outcome.exhausted() && !partly.isEmpty()) {
            return "NOT COVERED: every stream in the feed was picked, but only part of stream(s) " + partly
                   + " was read (" + recordsRead + " records); there may be other kinds of event further in. "
                   + again;
        }
        if (outcome.exhausted()) {
            return "covered: every stream in the feed has been read whole";
        }
        if (saturated) {
            final String heads = partly.isEmpty()
                    ? ""
                    : " (only part of stream(s) " + partly + ": big streams are sampled, their head and windows "
                      + "further in)";
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
        int withMore = 0;
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
            // More of the shape's records where their text differs (work package E9: the agent classifies a kind
            // better from several of its records), for the commonest shapes, kept short.
            final List<String> more = examples.stream().skip(1)
                    .map(example -> String.valueOf(example.get("text")))
                    .filter(text -> !text.equals(String.valueOf(examples.getFirst().get("text"))))
                    .distinct()
                    .limit(MORE_EXAMPLES)
                    .map(text -> head(text, MORE_EXAMPLE_CHARS))
                    .toList();
            if (!more.isEmpty() && withMore < SHAPES_WITH_MORE) {
                entry.put("more_examples", more);
                withMore++;
            }
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

    // The values of the fields that name the kind of event, each with how many records hold it, over every shape
    // found (not only those shown), commonest first. Seen: a 790 KB survey regrouped by x-type and x-action with jq,
    // its shapes being their combinations.
    static Map<String, Map<String, Long>> kinds(final List<Map<String, Object>> found) {
        final Map<String, Map<String, Long>> counted = new LinkedHashMap<>();
        for (final Map<String, Object> shape : found) {
            final String naming = namingPart(signature(shape));
            if (naming == null) {
                continue;
            }
            for (final String pair : naming.split(", ")) {
                final int eq = pair.indexOf('=');
                if (eq > 0) {
                    counted.computeIfAbsent(pair.substring(0, eq), k -> new LinkedHashMap<>())
                            .merge(pair.substring(eq + 1), count(shape), Long::sum);
                }
            }
        }
        final Map<String, Map<String, Long>> kinds = new LinkedHashMap<>();
        counted.entrySet().stream().limit(MAX_KIND_FIELDS).forEach(field -> {
            final Map<String, Long> values = new LinkedHashMap<>();
            field.getValue().entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(MAX_KIND_VALUES)
                    .forEach(value -> values.put(value.getKey(), value.getValue()));
            kinds.put(field.getKey(), values);
        });
        return kinds;
    }

    // The naming pairs of a signature (`row:a=x, b=y`, `kv:...`, `keys | a=x, b=y`), or null for one without them.
    private static String namingPart(final String signature) {
        if (signature.startsWith("row:") || signature.startsWith("kv:")) {
            return signature.substring(signature.indexOf(':') + 1);
        }
        final int bar = signature.indexOf(" | ");
        return !signature.startsWith("text:") && !signature.startsWith("cef:") && bar >= 0
                ? signature.substring(bar + 3)
                : null;
    }

    // The first characters of a text, counted in code points (a character outside the BMP is one).
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
    /// @param maxParts        The most parts of a stream, or null for the default.
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
                           Integer maxParts,
                           Integer maxRecords,
                           int examples,
                           String build,
                           String readDeeper,
                           String kindField,
                           String acceptAsIs) {

    }

    /// How much of each stream is read.
    ///
    /// @param maxChars   The most characters of a part.
    /// @param maxParts   The most parts of a stream.
    /// @param maxRecords The most records of each part.
    /// @param whole      Whether the streams are to be read whole (a sample's, or a feed's last): each part in order
    ///                   from its start, so the user can have it read deeper.
    private record Limits(int maxChars, int maxParts, int maxRecords, boolean whole) {

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
        // Streams whose delimited records hold a quoted value over several lines.
        private final Set<Long> quotedNewlinesIn = new LinkedHashSet<>();
        // The parts of streams to be read whole that were read only in part, and can be read deeper.
        private final List<SurveyDepth.Part> partial = new ArrayList<>();
        // The first parts' heads read already (to choose the kind fields), by stream.
        private final Map<Long, RawHead> peeked = new LinkedHashMap<>();

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

    // What a part's windows gave: how many were read, their records, the shapes new among them, and where their
    // records are (each [first, count]).
    private record WindowsRead(int windows, int records, int newShapes, List<List<Long>> ranges) {

    }
}
