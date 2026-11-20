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
import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiSamplingConfig;
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
import stroom.ai.impl.tools.sample.PartSampler;
import stroom.ai.impl.tools.sample.RecordSplitter;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.Shapes;
import stroom.ai.impl.tools.sample.SourceTypeFields;
import stroom.ai.impl.tools.sample.SourceTypeMeta;
import stroom.ai.impl.tools.sample.SourceTypeProfiles;
import stroom.ai.impl.tools.sample.SourceTypeReading;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.SourceTypeScope;
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
import jakarta.inject.Provider;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
    // deeper, and kinds only further in were found at processing), and syslog (seen in testing: a mail gateway's
    // 30,000-line streams could only be stopped at, "can't be read further in a call").
    private static final Set<String> WINDOWED = Set.of(RecordSplitter.DELIMITED, RecordSplitter.JSON_LINES,
            SampleProfiler.KEY_VALUE_FORMAT, SampleProfiler.CEF_FORMAT, SampleProfiler.CRI,
            RecordSplitter.UNKNOWN_TEXT, SampleProfiler.MIXED_LINES, SampleProfiler.SYSLOG_3164_FORMAT,
            SampleProfiler.SYSLOG_5424_FORMAT);
    // Formats whose records may run over several lines, so a window can be started in one only when none does.
    private static final Set<String> LINES_UNLESS_SEEN = Set.of(SampleProfiler.MIXED_LINES,
            SampleProfiler.SYSLOG_3164_FORMAT, SampleProfiler.SYSLOG_5424_FORMAT);
    static final String MAX_PARTS_ARG = "max_parts_per_stream";
    /// Why a stream read only in part can't be read further, in its `per_stream` entry.
    static final String NOT_READ_FURTHER = "not_read_further";
    /// The parts of a stream of several read, in its `per_stream` entry.
    static final String PARTS_READ = "parts_read";
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
    // The answers in the go-ahead form, each said whole, that go ahead with the survey as it is.
    private static final List<String> GO_AHEAD_ANSWERS = List.of(ACCEPT, "yes", "true", "ok", "okay", "go ahead",
            "accept", "proceed");
    // The answers in the go-ahead form, each said whole, that keep on surveying.
    private static final List<String> KEEP_SURVEYING_ANSWERS = List.of(KEEP_SURVEYING, "no", "keep going",
            "carry on surveying", "survey more");
    // The question of the form that asks which field says each record's source.
    static final String SOURCE_FIELD_QUESTION = "Which field says which source a record came from?";
    // The answers in that form, each said whole, that say the feed is one source.
    private static final List<String> ONE_SOURCE_ANSWERS = List.of(SourceTypeFields.NOT_AGGREGATED, "none", "no",
            "one source", "not aggregated", "not an aggregated feed", "-");
    // The question asked before going ahead with the survey as it is.
    static final String ACCEPT_QUESTION = "Go ahead with the survey as it is?";
    // The question asked of streams read only in part that can't be read further.
    private static final String STOP_QUESTION = "Stop and accept them as read, or step records further in first?";
    static final String STOP_THERE = "Stop and accept them as read";
    static final String STEP_FURTHER = "Step records further in first";
    // The share of a part's budget its windows take when it is read in order from its start: a quarter.
    private static final int WINDOW_SHARE = 4;
    // How many more records than the budget a build's scope may have split from a head or window to find its own: a
    // scope of a tenth of the records gets its budget from the text a whole-feed survey reads.
    static final int SCOPE_SPREAD = 10;
    static final String EXAMPLES_ARG = "examples_per_shape";
    static final String BUILD_ARG = "build";
    static final String SOURCE_TYPE_FIELD_ARG = "source_type_field";
    static final String AGGREGATED_ARG = "aggregated";
    static final String FIND_ARG = "find";
    /// The most of a feed's streams, newest first, a call with `find` picks from (those surveyed before left out).
    static final int FIND_STREAMS = 500;
    // The most source type values said where a find's streams held none of the row's records.
    private static final int VALUES_SAID = 5;

    /// The most characters read of a part, asked for.
    static final int MAX_CHARS = 5_000_000;
    // The most streams (or parts) said in a refusal for nothing readable, and the most characters of each one's
    // error.
    private static final int MOST_SAID_STREAMS = 5;
    private static final int MOST_SAID_CHARS = 200;
    // Who chose a source type field taken from the feed's registry.
    private static final String REGISTRY = "registry";
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
    private final SourceTypeRegistries besideFeed;
    private final Provider<BuildRecords> buildRecords;
    private final Provider<BuildPlan> plan;
    private final Provider<AiConfig> aiConfigProvider;

    @Inject
    SurveyFeedTool(final StreamReader streamReader,
                   final FeedNames feedNames,
                   final WriteGuard guard,
                   final SurveyDocuments surveyDocuments,
                   final Consent consent,
                   final FewerStreams fewerStreams,
                   final DocumentAccess documentAccess,
                   final AiAuditLog auditLog,
                   final SourceTypeRegistries besideFeed,
                   final Provider<BuildRecords> buildRecords,
                   final Provider<BuildPlan> plan,
                   final Provider<AiConfig> aiConfigProvider) {
        this.streamReader = streamReader;
        this.feedNames = feedNames;
        this.guard = guard;
        this.surveyDocuments = surveyDocuments;
        this.consent = consent;
        this.fewerStreams = fewerStreams;
        this.documentAccess = documentAccess;
        this.auditLog = auditLog;
        this.besideFeed = besideFeed;
        this.buildRecords = buildRecords;
        this.plan = plan;
        this.aiConfigProvider = aiConfigProvider;
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
                             + "for syslog and other text. Only the head of each stream's parts is read "
                             + "(max_chars_per_stream a part; max_parts_per_stream parts, spread through a stream "
                             + "of more from its first to its last), so multi-GB streams cost the same as small "
                             + "ones; a sample's streams (stream_ids), and a feed's last streams "
                             + "(fewer left than max_streams), are read whole up to 2,000,000 characters a part "
                             + "(unless configured), and coverage says when one wasn't (the feed is then not "
                             + "covered). Every call reads "
                             + "all max_streams streams it picks (a quiet feed's rare kinds of event can sit behind "
                             + "several streams with nothing new: asked for by the user). The feed counts as "
                             + "covered (saturated) when a call with known_signatures reads a full max_streams "
                             + "with nothing new, or when every stream has been read. Returns each shape with its "
                             + "count, share and examples, and where each example is (stream, part, record) for "
                             + "step_records. Call again with skip_stream_ids (all streams read so far) and "
                             + "known_signatures to read further streams for kinds of event not seen yet. With a "
                             + "build, the results are kept in (and continued from) the build's survey doc; once "
                             + "the build's scope over an aggregated feed's source types is chosen "
                             + "(record_source_types), only the scope's records are surveyed, the others counted by "
                             + "source type. Reads only, apart from that doc.")
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
                                                           + "each part read (each part its own); big streams are "
                                                           + "only sampled at their head. Default 250,000, or "
                                                           + "2,000,000 (unless configured) for stream_ids and a "
                                                           + "feed's last streams (read whole).")
                        .addIntegerProperty(MAX_PARTS_ARG, "Parts of a stream to read (a stream of several files "
                                                           + "received at once has one each), not records: "
                                                           + "max_records_per_stream counts records. Default 3, "
                                                           + "or all (up to 20) for stream_ids and a feed's last "
                                                           + "streams; a stream of more has them chosen spread "
                                                           + "through it, its first and last among them.")
                        .addIntegerProperty(MAX_RECORDS_ARG, "Records read from each part of a stream: default 1,000, "
                                                             + "or 10,000 for stream_ids and a feed's last streams "
                                                             + "(read in order from the start, so the user can have "
                                                             + "a part read deeper).")
                        .addStringProperty(READ_DEEPER_ARG, "Only when the client shows no forms: what the user "
                                                            + "said, in the chat, when asked whether to read the "
                                                            + "parts read in part deeper ('stop', a number of "
                                                            + "records more, or 'to the end').")
                        .addStringProperty(KIND_FIELD_ARG, "A field whose values tell the records apart (e.g. "
                                                           + "'msg_act'; two as 'a, b'), a hint for the survey's "
                                                           + "coverage only (event types are catalogued by "
                                                           + "catalogue_event_types); a different one starts the "
                                                           + "survey afresh.")
                        .addStringProperty(ACCEPT_ARG, "Only when the user wants to go ahead with the build's survey "
                                                       + "as it is, before it is covered: 'true' (they confirm in a "
                                                       + "form), or, when the client shows no forms, what they said "
                                                       + "in the chat. Reads nothing: the survey is then covered as "
                                                       + "far as the user chose, and processing can go ahead.")
                        .addProperty(EXAMPLES_ARG, new IntegerSchema(null))
                        .addStringProperty(BUILD_ARG, "Keep the survey record in this build: a Documentation doc "
                                                      + "'<feed> - Survey' with the kinds of event, examples and "
                                                      + "survey state. An existing record is continued: its "
                                                      + "streams are skipped and its shapes are known. It stays "
                                                      + "in the build at promotion, so a later build surveys the "
                                                      + "feed afresh.")
                        .addStringProperty(SOURCE_TYPE_FIELD_ARG, "Only what the user said: the field that says "
                                                                  + "which source each record of an aggregated "
                                                                  + "feed came from (e.g. 'sourcetype', "
                                                                  + "'event.Workload'), a reading into a field "
                                                                  + "holding JSON, XML, a syslog header or "
                                                                  + "key=value ('event>logger_name'; readings "
                                                                  + "tried in turn joined by ' | ', e.g. "
                                                                  + "'event>logger_name | event>xml:@logger'; "
                                                                  + "'event>#format', by format alone, only as "
                                                                  + "their last resort), 'meta:<Attribute>' for a "
                                                                  + "stream meta attribute (a header each part of a "
                                                                  + "stream carries, as stroom:meta reads it), or "
                                                                  + "'none' when they say the feed is one source. "
                                                                  + "Unasked, the build's survey finds a candidate "
                                                                  + "itself and asks them (never a meta attribute).")
                        .addBooleanProperty(AGGREGATED_ARG, "True when the user says the feed aggregates several "
                                                            + "sources: the survey looks harder for the field "
                                                            + "that tells them apart, and asks the user.")
                        .addStringProperty(FIND_ARG, "With build: a source type registry row's name (e.g. one the "
                                                     + "user nominated that the survey hasn't met). Reads the "
                                                     + "feed's streams not surveyed yet, newest first (or just "
                                                     + "stream_ids), for that row's records until it has a few, "
                                                     + "and counts them into the build's survey; says which "
                                                     + "streams held them.")
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
            // A search for one source type's records reads as many streams as it takes: no fewer to agree to.
            final Integer fewer = request.find() == null
                    ? confirmFewerStreams(call, context, feed, request, chosen)
                    : null;
            final Map<String, Object> surveyed = withinTimeLimit(context, () -> survey(call, context, request, feed,
                    chosen, fewer));
            // A build's survey is a step of its plan (surveyed, then covered for an existing feed): what comes next.
            final Map<String, Object> result = new LinkedHashMap<>(request.build() == null || request.find() != null
                    ? surveyed
                    : plan.get().withNext(context, request.build(), surveyed));
            // A client outside Stroom takes only so much of a result (Stroom AI's chat keeps large results itself).
            SurveyOutline.outlineIfLarge(context.caller(), context.workspace(), LargeResults.keyOf(
                    request.build(), feed), result, LargeResults.WHOLE_RESULT_CHARS);
            return JsonResults.of(result);
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
                stringArg(call, ACCEPT_ARG),
                stringArg(call, SOURCE_TYPE_FIELD_ARG),
                "true".equalsIgnoreCase(NullSafe.string(stringArg(call, AGGREGATED_ARG)).strip()),
                NullSafe.isBlankString(stringArg(call, FIND_ARG))
                        ? null
                        : stringArg(call, FIND_ARG).strip());
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
    // answer holds for that feed and count, so later calls go on unasked: kept in the build's survey record (asked
    // for by the user: in node memory alone it was lost on a restart and after four hours), and in this node's
    // memory for a survey with no build. Returns the count agreed, or null when there was nothing to agree.
    private Integer confirmFewerStreams(final ToolCall call,
                                        final ToolCallContext context,
                                        final String feed,
                                        final Request request,
                                        final List<Meta> chosen) {
        if (chosen.isEmpty() && request.maxStreams() < FeedSampling.SURVEY_STREAMS
            && holdsMore(feed, request.streamType(), request.maxStreams())) {
            return askFewerStreams(call, context, feed, request.build(), request.maxStreams());
        } else if (!chosen.isEmpty() && chosen.size() < FeedSampling.SURVEY_STREAMS && !inABuild(feed)
                   && holdsMore(feed, request.streamType(), chosen.size())) {
            // A few of an existing feed's streams, by id: as few as a smaller max_streams, so the user's to confirm
            // too (seen in testing on 0.18.13: an existing feed onboarded from 5 streams). A sample uploaded to a
            // build's feed is read as it is.
            return askFewerStreams(call, context, feed, request.build(), chosen.size());
        }
        return null;
    }

    private Integer askFewerStreams(final ToolCall call,
                                    final ToolCallContext context,
                                    final String feed,
                                    final String build,
                                    final int count) {
        if (agreedInRecord(build, feed, count) || fewerStreams.agreed(feed, count)) {
            return count;
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
        return count;
    }

    // Whether the build's survey record says the user agreed to survey the feed this many streams a call.
    private boolean agreedInRecord(final String build, final String feed, final int count) {
        if (build == null) {
            return false;
        }
        try {
            return surveyDocuments.load(build, feed)
                    .map(record -> SurveyDoc.readState(record.text()))
                    .map(state -> state.get(SurveyDoc.FEWER_STREAMS) instanceof final Number agreed
                                  && agreed.intValue() == count)
                    .orElse(false);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No survey record read for {}: {}", feed, e.getMessage()));
            return false;
        }
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
                                       final List<Meta> chosen,
                                       final Integer fewer) {
        final String build = request.build();
        final SurveyRecord record = build == null
                ? null
                : surveyDocuments.load(build, feed).orElse(null);
        // Only the build's own survey doc is carried on from: a survey doc left beside the feed by an earlier
        // promotion (before survey docs stayed in their build, 2026-11-13) is ignored, and the survey starts afresh.
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
        final Map<String, Object> keptSources = withRegistryScope(build, feed, surveyed == null
                ? Map.of()
                : JsonValues.mapOrEmpty(surveyed.get(SourceTypeProfiles.STATE_KEY)));
        final Set<String> dropped = read == null || state == null
                ? Set.of()
                : droppedShapes(read, state);
        if (request.find() != null) {
            if (givenKinds != null || request.sourceTypeField() != null) {
                throw new ToolRefusal(FIND_ARG + " reads with the build's survey's own kind field and source type "
                                      + "field: call " + NAME + " without " + KIND_FIELD_ARG + " and "
                                      + SOURCE_TYPE_FIELD_ARG + ".");
            }
            return findSourceType(context, request, feed, chosen, new Surveyed(record, surveyed, keptSources,
                    keptKinds, gone, dropped));
        }
        if (request.acceptAsIs() != null) {
            return acceptAsIs(call, context, request, feed, record, state, fewer);
        }
        final Set<Long> skip = new TreeSet<>(request.skipStreamIds());
        final Set<String> known = new LinkedHashSet<>(request.knownSignatures());
        known.removeAll(dropped);
        // A build's scope over an aggregated feed's source types: only its records make shapes. A stream read before
        // for other source types (a narrower scope, or another build's) is read again for those it wasn't read for.
        final Map<String, Object> keptScope = JsonValues.mapOrEmpty(keptSources.get(SourceTypeProfiles.SCOPE));
        final SourceTypeScope scope = build == null || keptScope.isEmpty()
                ? null
                : SourceTypeScope.of(keptSources, build, JsonValues.listOrEmpty(keptScope.get(SourceTypeScope.ROWS))
                        .isEmpty()
                        ? registryRows(build, feed)
                        : List.of()).orElse(null);
        final SourceTypeScope reader = scope != null
                ? scope
                : readerOf(keptSources, build, feed);
        final Map<Long, List<String>> readAgain = new LinkedHashMap<>();
        if (state != null) {
            JsonValues.listOrEmpty(state.get("streams_read")).forEach(id -> {
                final long stream = ((Number) id).longValue();
                final List<String> signed = SurveyDoc.readFor(state, stream);
                if (reader == null || reader.covers(signed)) {
                    skip.add(stream);
                } else {
                    readAgain.put(stream, signed);
                }
            });
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
        final Limits limits = limits(request, !chosen.isEmpty() || exhausted);
        final SurveyRecords kept = new SurveyRecords(build, record, state, request.examples(), fewer);
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
        // The first stream's head, read to choose the source type and kind fields, read once: the survey reads on
        // from it.
        final Map<Long, RawHead> peeked = new LinkedHashMap<>();
        final Heads heads = new Heads(() -> peekRecords(streams.getFirst(), limits, peeked));
        // An aggregated feed's source type field first: the kinds of event are told apart within its source types.
        final PartAttributes attributes = new PartAttributes(streamReader);
        final SourceDecision sources = decideSources(call, context, request, feed, keptSources, heads,
                new Asked(streams.stream().map(Meta::getId).toList(), limits.maxParts(), attributes));
        if (sources.early() != null) {
            return sources.early();
        }
        if (sources.changed() && state != null) {
            // Another field (or none): the source types counted by the old one go.
            state.remove(SourceTypeProfiles.STATE_KEY);
        }
        final Map<String, Object> kinds;
        if (givenKinds != null) {
            kinds = kindsKept(asTheRecordsSpellThem(givenKinds, kindChoice(heads, sources.field())), "user");
        } else if (sources.field() != null && !"user".equals(keptKinds.get("by"))) {
            // An aggregated feed: no kind field is chosen over every source type's records together, nor asked for
            // (seen in testing, 2026-11-18: 65 source types, and only the times they shared put to the user). The
            // event types are told apart within each source type, by catalogue_event_types over the build's scope.
            kinds = SurveyDoc.kindsWithinSourceTypes(sources.field());
        } else if (!keptKinds.isEmpty() && !SurveyDoc.withinSourceTypes(keptKinds)) {
            kinds = keptKinds;
        } else {
            // Not once the call is stopped: nothing more is read.
            final Optional<KindFields.Choice> choice = Thread.currentThread().isInterrupted()
                    ? Optional.empty()
                    : kindChoice(heads, sources.field());
            if (choice.isEmpty()) {
                kinds = null;
            } else if (choice.get().certain() && choice.get().fields().isEmpty()) {
                // No field to tell kinds apart in this head: chosen again from a later call's, not fixed by one.
                kinds = null;
            } else {
                // A hint for the survey's own coverage, never asked of the user: the event types are told apart by
                // catalogue_event_types, by conditions on any of the records' fields (stroom-ai/DESIGN.md, Event
                // type catalogue).
                kinds = kindsKept(choice.get().fields(), "tool");
            }
        }
        if (kinds != null && !SurveyDoc.withinSourceTypes(kinds)) {
            shapes.useKindFields(JsonValues.strings(kinds.get("fields")));
        }
        final SourceTypeProfiles profiles = sources.field() == null
                ? null
                : new SourceTypeProfiles(sources.field());
        if (profiles != null) {
            shapes.observe(profiles::add);
            keepSourceValues(shapes, sources.field(), attributes);
        }
        // The scope chosen for another field, or before the field changed, isn't this survey's.
        final boolean scoped = scope != null && scope.field().equals(sources.field()) && !sources.changed();
        final StreamScopes scopes = new StreamScopes(shapes, scoped
                ? scope
                : null, reader != null && reader.field().equals(sources.field()) && !sources.changed()
                ? reader
                : null, readAgain, profiles);
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
                .toList(), shapes, limits, peeked, profiles, scopes, done -> false);
        if (reading.firstChunk() == null && pending.isEmpty()) {
            if (reading.stopped()) {
                throw new ToolTimeoutException();
            }
            final String why = whyNothingReadable(reading);
            // Kept in the build's record (not this node's memory: a restart, or another node, still sees it), so
            // catalogue_event_types can go on without the survey, saying why it had none.
            buildRecords.get().rememberUnreadableSurvey(request.build(), feed, why);
            throw new ToolRefusal("Nothing readable in the sampled streams of '" + feed + "': " + why);
        }
        // Read now: an earlier survey's note that nothing was readable no longer holds.
        buildRecords.get().forgetUnreadableSurvey(request.build(), feed);
        if (reading.format == null && state != null) {
            reading.format = String.valueOf(state.get("format"));
        }
        final List<SurveyDepth.Part> depth = limits.whole()
                ? deepen(call, context, request, feed, reading, shapes, pending, earlier)
                : List.of();
        final Map<String, Object> result = result(new Outcome(feed, span, chosen, exhausted, !known.isEmpty(),
                state, streams.size(), request.maxStreams(), limits), reading, shapes, context, scopes.scope != null
                ? scopes.scope
                : scopes.reader);
        noteDepth(result, reading, depth);
        noteScope(result, scopes, reading, depth, keptSources, profiles);
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
        if (sources.decision() != null) {
            final Map<String, Object> counted = new LinkedHashMap<>(sources.decision());
            if (profiles != null) {
                counted.putAll(profiles.toState(partSizes(profiles)));
            }
            if (scoped) {
                // The build's scope, kept in the survey state with the field it reads (seeded from the build's
                // registry when record_source_types came before the survey).
                counted.put(SourceTypeProfiles.SCOPE, keptSources.get(SourceTypeProfiles.SCOPE));
            }
            result.put(SourceTypeProfiles.STATE_KEY, counted);
        } else if (build == null) {
            noteAggregated(result, feed, heads);
        }
        return withSourcesSaid(keep(context, kept, result, shapes.shapes()), build, feed);
    }

    // How much of each stream a call reads: a sample's streams, and a feed's last, read whole (up to the most
    // characters of a stream's sample, ai.sampling).
    private Limits limits(final Request request, final boolean whole) {
        final AiSamplingConfig sampling = NullSafe.getOrElseGet(aiConfigProvider.get(), AiConfig::getSampling,
                AiSamplingConfig::new);
        return new Limits(orDefault(request.maxChars(), whole
                ? sampling.getMaxCharsPerStream()
                : FeedSampling.FEED_CHARS), orDefault(request.maxParts(), whole
                // Streams read whole are read through every part: seen in testing, a feed's latest streams uploaded
                // again as one stream of five parts was surveyed through three.
                ? MAX_PARTS
                : FEED_PARTS), orDefault(request.maxRecords(), whole
                ? FeedSampling.SAMPLE_RECORDS
                : FeedSampling.FEED_RECORDS), whole, sampling.getMaxEventChars());
    }

    // A search for one registry row's records (find=; asked for by the user: a source type they nominated, which the
    // survey hadn't met, had no way to be found). The feed's streams not surveyed for the build yet, newest first (or
    // just the streams named), are read through a scope of that row alone, matched as the registry matches (the
    // first row wins), until a few of its records are found or the streams or the call's time run out. Its records
    // count into the build's survey as any survey reading does, so the row's profile fills in and its kinds of event
    // can be classified; each stream read is signed for the row alone, so a later survey reads it again for the
    // others. It asks nothing: the kind and source type fields are the survey's (or the registry's).
    private Map<String, Object> findSourceType(final ToolCallContext context,
                                               final Request request,
                                               final String feed,
                                               final List<Meta> chosen,
                                               final Surveyed surveyed) {
        final String build = request.build();
        if (build == null) {
            throw new ToolRefusal(FIND_ARG + " needs build: the build whose survey the records found count into.");
        }
        final List<SourceTypeRows.Row> rows = registryRows(build, feed);
        if (rows.isEmpty()) {
            throw new ToolRefusal("Feed '" + feed + "' has no source type registry (in build '" + build + "' or "
                                  + "beside the feed), so there is no row '" + request.find() + "' to find: "
                                  + "record_source_types writes the registry, with the rows the user names.");
        }
        final SourceTypeRows.Row row = rows.stream()
                .filter(candidate -> candidate.name().equals(request.find()))
                .findFirst()
                .or(() -> rows.stream().filter(candidate -> candidate.name().equalsIgnoreCase(request.find()))
                        .findFirst())
                .orElseThrow(() -> new ToolRefusal("Feed '" + feed + "''s source type registry has no row '"
                                                   + request.find() + "': its rows are " + rows.stream()
                                                           .map(SourceTypeRows.Row::name).toList() + ". Give "
                                                   + "one of them, or add the row with record_source_types "
                                                   + "first."));
        final String field = findField(build, feed, surveyed.sources());
        final Map<String, Object> state = surveyed.state();
        final Set<Long> before = new TreeSet<>(request.skipStreamIds());
        if (state != null) {
            JsonValues.listOrEmpty(state.get("streams_read")).forEach(id -> before.add(((Number) id).longValue()));
        }
        final boolean named = !chosen.isEmpty();
        final List<Meta> streams;
        final boolean older;
        if (named) {
            streams = chosen.stream().filter(meta -> !request.skipStreamIds().contains(meta.getId())).toList();
            older = false;
        } else {
            final ExpressionOperator live = ExpressionOperator.builder().op(Op.AND)
                    .addTextTerm(MetaFields.FEED, Condition.EQUALS, feed)
                    .addTextTerm(MetaFields.TYPE, Condition.EQUALS, request.streamType())
                    .addOperator(FeedSampling.notDeleted())
                    .build();
            final int most = before.size() + FIND_STREAMS;
            final List<Meta> newest = find(live, most, true);
            older = newest.size() >= most;
            streams = newest.stream()
                    .filter(meta -> !before.contains(meta.getId()))
                    .limit(FIND_STREAMS)
                    .toList();
        }
        final Map<String, Object> keptProfile = rowProfile(surveyed.sources(), Map.of(), rows, row);
        if (streams.isEmpty() && (named || before.isEmpty())) {
            throw new ToolRefusal("No " + request.streamType() + " streams in feed '" + feed + "' to search");
        }
        if (streams.isEmpty()) {
            return nothingLeftToSearch(feed, row, before.size(), keptProfile);
        }
        final Set<String> known = new LinkedHashSet<>(request.knownSignatures());
        if (state != null) {
            JsonValues.mapOrEmpty(state.get("shapes")).keySet().forEach(signature -> known.add(String.valueOf(
                    signature)));
        }
        known.removeAll(surveyed.dropped());
        final Shapes shapes = new Shapes(new ArrayList<>(known), request.examples());
        if (!surveyed.kinds().isEmpty() && !SurveyDoc.withinSourceTypes(surveyed.kinds())) {
            shapes.useKindFields(JsonValues.strings(surveyed.kinds().get("fields")));
        }
        final SourceTypeProfiles profiles = new SourceTypeProfiles(field);
        keepSourceValues(shapes, field, new PartAttributes(streamReader));
        final StreamScopes scopes = new StreamScopes(shapes, new SourceTypeScope(build, field, rows,
                List.of(row.name())), null, Map.of(), profiles);
        final Limits limits = limits(request, named);
        // The streams named are read whole; the feed's until the row has as many records as a shape keeps examples.
        final Reading reading = read(context, CallProgress.of(context), feed, streams, shapes, limits, Map.of(),
                profiles, scopes, done -> !named && done.recordsRead >= request.examples());
        if (reading.firstChunk() == null) {
            if (reading.stopped()) {
                throw new ToolTimeoutException();
            }
            throw new ToolRefusal("Nothing readable in the streams of '" + feed + "' searched for source type '"
                                  + row.name() + "': " + whyNothingReadable(reading));
        }
        final Map<String, Object> result = result(new Outcome(feed, span(streams), chosen, false, !known.isEmpty(),
                state, streams.size(), streams.size(), limits), reading, shapes, context, scopes.scope);
        // The survey's coverage is left as it was: a search for one source type doesn't judge it.
        result.remove("coverage");
        result.put("saturated", state != null && JsonValues.truthy(state.get("saturated")));
        if (state != null && state.get(SurveyDoc.ACCEPTED) != null) {
            result.put(SurveyDoc.ACCEPTED, state.get(SurveyDoc.ACCEPTED));
        }
        final Map<String, Object> signed = new LinkedHashMap<>();
        reading.streamsRead().forEach(stream -> signed.put(String.valueOf(stream), List.of(row.name())));
        if (!signed.isEmpty()) {
            result.put(SurveyDoc.SCOPE_READ, signed);
        }
        noteGone(result, surveyed.gone(), surveyed.dropped());
        final Map<String, Object> counts = profiles.toState(partSizes(profiles));
        final Map<String, Object> counted = new LinkedHashMap<>(sourcesKept(surveyed.sources(), field));
        counted.putAll(counts);
        if (field.equals(surveyed.sources().get(SourceTypeProfiles.FIELD))
            && surveyed.sources().get(SourceTypeProfiles.SCOPE) != null) {
            counted.put(SourceTypeProfiles.SCOPE, surveyed.sources().get(SourceTypeProfiles.SCOPE));
        }
        result.put(SourceTypeProfiles.STATE_KEY, counted);
        final Search search = new Search(row, named, older, shapes.passedOver());
        final Map<String, Object> found = found(search, reading, rowProfile(surveyed.sources(), counts, rows, row),
                counts);
        result.put(FIND_ARG, found);
        result.put("hint", findHint(feed, build, search, reading, found));
        final Map<String, Object> kept = keep(context, new SurveyRecords(build, surveyed.record(), state,
                request.examples(), null), result, shapes.shapes());
        return withSourcesSaid(kept, build, feed);
    }

    // The source type field a search reads by: the survey's, else the registry's; refused when there is neither, or
    // the user said the feed is one source.
    private String findField(final String build, final String feed, final Map<String, Object> sources) {
        if (sources.get(SourceTypeProfiles.FIELD) instanceof final String field && !field.isBlank()) {
            return field;
        }
        if (Boolean.FALSE.equals(sources.get("aggregated"))) {
            throw new ToolRefusal("The build's survey has feed '" + feed + "' as one source (no source type field), "
                                  + "so there is no source type to find. If the user says it aggregates several, "
                                  + "call " + NAME + " with " + SOURCE_TYPE_FIELD_ARG + " as they say first.");
        }
        return registryField(build, feed).orElseThrow(() -> new ToolRefusal(
                "No source type field is known for feed '" + feed + "': survey it with build='" + build + "' first "
                + "(the survey finds the field and asks the user), then " + FIND_ARG + "."));
    }

    // The source type decision the survey keeps (its field, who chose it, when), or the registry's field.
    private static Map<String, Object> sourcesKept(final Map<String, Object> sources, final String field) {
        if (!field.equals(sources.get(SourceTypeProfiles.FIELD))) {
            return sourcesChosen(field, REGISTRY);
        }
        final Map<String, Object> decision = new LinkedHashMap<>();
        for (final String key : List.of(SourceTypeProfiles.FIELD, "aggregated", SourceTypeProfiles.BY, "at")) {
            if (sources.get(key) != null) {
                decision.put(key, sources.get(key));
            }
        }
        return decision;
    }

    // The row's profile from the survey's counts with this call's added: how many of its records were read, and what
    // that rests on.
    private static Map<String, Object> rowProfile(final Map<String, Object> kept,
                                                  final Map<String, Object> call,
                                                  final List<SourceTypeRows.Row> rows,
                                                  final SourceTypeRows.Row row) {
        final Map<String, Object> merged = SourceTypeProfiles.merge(kept.isEmpty()
                ? null
                : kept, call);
        final Map<String, Object> summary = SourceTypeProfiles.summarise(merged, rows).getOrDefault(row.name(),
                Map.of());
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("records_read", summary.getOrDefault("records_read", 0L));
        if (summary.get("records_estimate") != null) {
            out.put("records_estimate", summary.get("records_estimate"));
        }
        out.put("rests_on", merged.get("records") instanceof final Number records && records.longValue() > 0
                ? summary.get("rests_on")
                : "not seen: no survey records read");
        return out;
    }

    // Every stream the build hasn't surveyed was read before this call: none left to search.
    private static Map<String, Object> nothingLeftToSearch(final String feed,
                                                           final SourceTypeRows.Row row,
                                                           final int surveyed,
                                                           final Map<String, Object> profile) {
        final long records = ((Number) profile.get("records_read")).longValue();
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("feed", feed);
        result.put("streams_read", List.of());
        result.put("records_read", 0);
        result.put(FIND_ARG, entry("source_type", row.name(), "match", row.said(), "found", records > 0,
                "streams_read", 0, "profile", profile));
        result.put("hint", "No stream of feed '" + feed + "' is left to search: the build's survey has read every "
                           + "one (" + surveyed + "), and met " + records + " record(s) of source type '"
                           + row.name() + "'." + (records > 0
                ? ""
                : " It may not have arrived in the feed yet: a discovery index, or a later survey, would show it "
                  + "once it does. Tell the user so."));
        return result;
    }

    // What the search found: the row, whether its records were found, the streams that held them (with when they
    // were made, and how many of its records each), and the row's profile now.
    private static Map<String, Object> found(final Search search,
                                             final Reading reading,
                                             final Map<String, Object> profile,
                                             final Map<String, Object> counts) {
        final List<Map<String, Object>> holding = reading.perStream.stream()
                .filter(stream -> stream.get("records") instanceof final Number records && records.longValue() > 0)
                .map(stream -> entry("stream", stream.get("stream"), "created", stream.get("created"), "records",
                        stream.get("records")))
                .toList();
        final Map<String, Object> out = entry("source_type", search.row().name(), "match", search.row().said(),
                "found", reading.recordsRead > 0, "records_found", reading.recordsRead, "streams_read",
                reading.streamsRead().size(), "records_of_other_source_types", search.passedOver());
        out.put("streams_holding", holding);
        if (reading.recordsRead == 0) {
            final Map<String, Long> values = SourceTypeProfiles.valueCounts(counts);
            final List<String> top = values.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(VALUES_SAID)
                    .map(value -> value.getKey() + " (" + value.getValue() + ")")
                    .toList();
            out.put("values_held", top);
        }
        out.put("profile", profile);
        return out;
    }

    // What the agent tells the user of a search, and does next, in plain words.
    private static String findHint(final String feed,
                                   final String build,
                                   final Search search,
                                   final Reading reading,
                                   final Map<String, Object> found) {
        final String name = search.row().name();
        final int read = reading.streamsRead().size();
        final long others = search.passedOver();
        if (reading.recordsRead > 0) {
            final List<Object> streams = JsonValues.maps(found.get("streams_holding")).stream()
                    .map(stream -> stream.get("stream"))
                    .toList();
            return "Found " + reading.recordsRead + " record(s) of source type '" + name + "' in stream(s) "
                   + streams + " (" + read + " stream(s) read). They now count in the build's survey, so '" + name
                   + "' can be classified and translated like the other source types: tell the user where they "
                   + "were found, then carry on with the build's plan.";
        }
        if (search.named()) {
            return "The stream(s) given hold no record of source type '" + name + "' (" + search.row().said() + ") in "
                   + "the " + others + " record(s) read; the source type values they hold are in "
                   + "find.values_held. Tell the user, and ask for a stream that holds it, or offer to search the "
                   + "feed for it: " + NAME + " feed='" + feed + "' build='" + build + "' " + FIND_ARG + "='" + name
                   + "'.";
        }
        if (reading.stopped() || search.older()) {
            final String why = reading.stopped()
                    ? "this session's time limit for a call stopped the search"
                    : "the feed has older streams still to read";
            return "No record of source type '" + name + "' yet in the " + read + " stream(s) read, newest first ("
                   + others + " record(s) of other source types); " + why + ". Call " + NAME + " feed='" + feed
                   + "' build='" + build + "' " + FIND_ARG + "='" + name + "' again to carry on: the streams read "
                   + "are skipped.";
        }
        return "Not found: none of the " + read + " stream(s) read (every stream of the feed the build hadn't "
               + "surveyed, " + others + " record(s)) holds a record of source type '" + name + "' ("
               + search.row().said() + "). It may not have arrived in the feed yet: a discovery index, or a later "
               + "survey, would show it once it does. Tell the user so.";
    }

    // Each shape keeps the source type field's values its records had; a field read from a part's meta has the
    // part's attributes laid over each of its records, so the profiles and scopes read it as a record's.
    private static void keepSourceValues(final Shapes shapes, final String field, final PartAttributes attributes) {
        shapes.keepSourceValues(parsed -> SourceTypeFields.valueOf(parsed, field));
        if (SourceTypeMeta.isMeta(field)) {
            shapes.usePartFields((stream, part) -> attributes.fields(field, stream, part));
        }
    }

    // The source type field, as the user chose it: given now (source_type_field), kept by the survey, or found in
    // the first stream's head and put to the user in a form (or, with no forms, a needs_source_type_field result:
    // nothing surveyed yet). A survey with no build only reads, so nothing is asked. A part's meta attribute is
    // taken only where the user names it (meta:<Attribute>), checked against the parts of the streams to be read.
    private SourceDecision decideSources(final ToolCall call,
                                         final ToolCallContext context,
                                         final Request request,
                                         final String feed,
                                         final Map<String, Object> kept,
                                         final Heads heads,
                                         final Asked asked) {
        final String keptField = kept.get(SourceTypeProfiles.FIELD) instanceof final String field
                ? field
                : null;
        final boolean keptNone = Boolean.FALSE.equals(kept.get("aggregated"));
        if (request.sourceTypeField() != null) {
            final String given = request.sourceTypeField().strip();
            if (saysOneSource(given)) {
                return new SourceDecision(sourcesChosen(null, "user"), null, null, keptField != null);
            }
            if (SourceTypeMeta.isMeta(given) && SourceTypeReading.isReading(given)) {
                throw new ToolRefusal(SOURCE_TYPE_FIELD_ARG + " '" + given + "': a stream meta attribute is read "
                                      + "whole, never stepped into or tried in turn with another reading.");
            }
            final String field = SourceTypeMeta.isMeta(given)
                    ? asked.attributes().field(given, asked.streams(), asked.maxParts())
                    : spelt(given, heads.get()).orElseThrow(() -> new ToolRefusal(
                    SOURCE_TYPE_FIELD_ARG + " '" + given + "' names no field of the records: they have "
                    + new TreeSet<>(fieldsOf(heads.get())) + ". Ask the user which one they meant."));
            return new SourceDecision(SourceTypeReadings.withRowsProposed(sourcesChosen(field, "user"), field,
                    heads.get(), null), field, null, keptField != null && !keptField.equals(field) || keptNone);
        }
        if (keptField != null || keptNone && !request.aggregated()) {
            final Map<String, Object> decision = new LinkedHashMap<>();
            for (final String key : List.of(SourceTypeProfiles.FIELD, "aggregated", SourceTypeProfiles.BY, "at",
                    SourceTypeProfiles.ROWS_PROPOSED)) {
                if (kept.get(key) != null) {
                    decision.put(key, kept.get(key));
                }
            }
            return new SourceDecision(decision, keptField, null, false);
        }
        if (request.build() == null || Thread.currentThread().isInterrupted()) {
            return new SourceDecision(null, null, null, false);
        }
        // A feed with a registry (promoted beside it by an earlier build) has its field settled: not chosen again
        // from this head (seen in an e2e walk: the newest stream, a fifth of its records given a new source type,
        // put Operation before Workload, and the build's scope matched none of the sample's records).
        final Optional<String> registered = registryField(request.build(), feed)
                .flatMap(field -> SourceTypeMeta.isMeta(field)
                        ? Optional.of(field)
                        : spelt(field, heads.get()));
        if (registered.isPresent()) {
            return new SourceDecision(sourcesChosen(registered.get(), REGISTRY), registered.get(), null, false);
        }
        final List<SourceTypeFields.Candidate> candidates = SourceTypeFields.candidates(heads.get(),
                request.aggregated());
        if (candidates.isEmpty()) {
            if (request.aggregated()) {
                throw new ToolRefusal("The user says feed '" + feed + "' aggregates several sources, but no field "
                                      + "of its records tells them apart by its values. Ask them which field says "
                                      + "each record's source, and call " + NAME + " again with "
                                      + SOURCE_TYPE_FIELD_ARG + " as they say.");
            }
            return new SourceDecision(null, null, null, false);
        }
        final List<String> options = new ArrayList<>(candidates.stream()
                .map(SourceTypeFields.Candidate::label)
                .toList());
        options.add(SourceTypeFields.NOT_AGGREGATED);
        final Optional<String> answer = consent.answer(call, context, NAME, sourceIntro(feed, candidates),
                SOURCE_FIELD_QUESTION, options);
        if (answer.isEmpty()) {
            return new SourceDecision(null, null, needsSourceTypeField(consent.whyInChat(context, NAME,
                    SOURCE_FIELD_QUESTION), feed, candidates), false);
        }
        final String said = answer.get().strip();
        // An option, or a field's name (or a reading into held values), said whole: anything else (seen in testing:
        // a question for the agent) is put to the agent to answer, not taken for a field's name.
        if (Consent.exactly(said, ONE_SOURCE_ANSWERS).isPresent()) {
            return new SourceDecision(sourcesChosen(null, "user"), null, null, false);
        }
        final Optional<SourceTypeFields.Candidate> taken = candidates.stream()
                .filter(candidate -> said.equalsIgnoreCase(candidate.label()))
                .findFirst();
        final Optional<String> field = taken.map(SourceTypeFields.Candidate::field)
                .or(() -> fieldSaid(said, heads.get()));
        if (field.isEmpty()) {
            throw consent.wroteInstead(context, NAME, SOURCE_FIELD_QUESTION, said, "ask them in the chat which field "
                                                                               + "says each record's source (or "
                                                                               + "whether the feed is one source), "
                                                                               + "and call " + NAME + " again with "
                                                                               + SOURCE_TYPE_FIELD_ARG + " as they "
                                                                               + "say ('none' for one source).");
        }
        return new SourceDecision(SourceTypeReadings.withRowsProposed(sourcesChosen(field.get(), "user"),
                field.get(), heads.get(), taken.orElse(null)), field.get(), null, false);
    }

    // Whether the user's words say the feed is one source.
    private static boolean saysOneSource(final String said) {
        final String text = said.strip().toLowerCase(Locale.ROOT);
        return SourceTypeFields.NOT_AGGREGATED.equalsIgnoreCase(said.strip()) || Set.of("none", "no", "one source",
                "not aggregated", "-").contains(text) || text.startsWith("not an aggregated")
               || text.startsWith("not aggregated");
    }

    // A field the user wrote in the form, as the records spell it, or a reading into held values; empty for words
    // that are neither, including a reading the tools don't take (a question, say, that holds a '|' or a '+').
    private static Optional<String> fieldSaid(final String said, final List<Map<String, Object>> records) {
        try {
            return spelt(said, records);
        } catch (final ToolRefusal e) {
            LOGGER.debug(() -> LogUtil.message("'{}' isn't a reading the tools take: {}", said, e.getMessage()));
            return Optional.empty();
        }
    }

    // A field the user named, as the records spell it (matched ignoring case), or a reading into held values
    // (SourceTypeReadings); the name as given when the head holds no structured records to check it against.
    private static Optional<String> spelt(final String given, final List<Map<String, Object>> records) {
        return SourceTypeReadings.spelt(given, records, SOURCE_TYPE_FIELD_ARG);
    }

    private static Set<String> fieldsOf(final List<Map<String, Object>> records) {
        final Set<String> names = new LinkedHashSet<>();
        records.forEach(record -> names.addAll(record.keySet()));
        return names;
    }

    private static Map<String, Object> sourcesChosen(final String field, final String by) {
        final Map<String, Object> out = new LinkedHashMap<>();
        if (field == null) {
            out.put("aggregated", false);
        } else {
            out.put(SourceTypeProfiles.FIELD, field);
        }
        out.put(SourceTypeProfiles.BY, by);
        out.put("at", SurveyDoc.at(Instant.now()));
        return out;
    }

    // The form's message: which fields could say each record's source, in plain text.
    private static String sourceIntro(final String feed, final List<SourceTypeFields.Candidate> candidates) {
        final List<String> lines = new ArrayList<>();
        candidates.forEach(candidate -> lines.add(SourceTypeReadings.said(candidate)));
        return "Feed '" + feed + "' looks like it aggregates several sources: the records of each value of this "
               + "field differ in their fields or messages, and each holds kinds of event of its own:\n"
               + String.join("\n", lines) + "\nConfirmed, the survey profiles each source type, and the build's "
               + "source types step lists them for you to choose the source types of this build's group.";
    }

    // With no form shown: the agent asks the user which field, in the chat (why: no form can be shown, the form
    // couldn't be, or the user put it aside for the chat).
    private static Map<String, Object> needsSourceTypeField(final String why,
                                                            final String feed,
                                                            final List<SourceTypeFields.Candidate> candidates) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("feed", feed);
        result.put("status", "needs_source_type_field");
        result.put("candidates", candidates.stream().map(SourceTypeReadings::json).toList());
        SourceTypeReadings.heldHint(candidates).ifPresent(hint -> result.put("held", hint));
        result.put("hint", why + ". Nothing was surveyed yet. The feed looks like it aggregates several sources: the "
                           + "records "
                           + "of each value of these fields differ in their fields or messages. Ask the user in the "
                           + "chat which field says each record's source (show them the candidates and their "
                           + "values), or whether the feed is one source, then call " + NAME + " again with "
                           + SOURCE_TYPE_FIELD_ARG + " as they said ('none' for one source). Don't choose for "
                           + "them.");
        return result;
    }

    // A survey with no build: a field that looks like a source type field is said, with the offer of an
    // assessment build to keep what the survey finds.
    private static void noteAggregated(final Map<String, Object> result, final String feed, final Heads heads) {
        final List<SourceTypeFields.Candidate> candidates = heads.peekedAlready()
                ? SourceTypeFields.candidates(heads.get(), false)
                : List.of();
        if (!candidates.isEmpty()) {
            result.put("aggregated_feed", "The records' " + candidates.getFirst().label() + " looks like it says "
                                          + "which source each came from: the feed may aggregate several sources.");
        }
        result.put("no_build", "Nothing of this survey is kept: it has no build. To keep it (and, for an "
                               + "aggregated feed, a registry of its source types with their profiles), offer the "
                               + "user an assessment build: start_onboarding source_name=<the feed> "
                               + "kind=assessment feed='" + feed + "', then survey_feed build=<it>. Declined, show "
                               + "this survey in the chat.");
    }

    // The source types of the result as the agent reads them: the field, and the records read of each value this
    // call; the full counts are in the survey doc.
    private static Map<String, Object> withSourcesSaid(final Map<String, Object> result,
                                                       final String build,
                                                       final String feed) {
        if (!(result.get(SourceTypeProfiles.STATE_KEY) instanceof final Map<?, ?> counted)) {
            return result;
        }
        final Map<String, Object> said = new LinkedHashMap<>();
        final Map<String, Object> state = JsonValues.mapOrEmpty(counted);
        if (state.get(SourceTypeProfiles.FIELD) == null) {
            said.put("aggregated", false);
            said.put("by", state.get(SourceTypeProfiles.BY));
            result.put(SourceTypeProfiles.STATE_KEY, said);
            return result;
        }
        said.put("field", state.get(SourceTypeProfiles.FIELD));
        said.put("by", state.get(SourceTypeProfiles.BY));
        final Map<String, Long> counts = SourceTypeProfiles.valueCounts(state);
        final Map<String, Long> top = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .forEach(entry -> top.put(entry.getKey(), entry.getValue()));
        said.put("records_read_by_value", top);
        said.put("registry", "the build's source types step: record_source_types build='" + build + "' feed='"
                             + feed + "' profiles each source type and writes the registry, '"
                             + SourceTypeRegistry.docName(feed) + "'");
        result.put(SourceTypeProfiles.STATE_KEY, said);
        return result;
    }

    // Each part the profiles counted records of, and its size, for their estimates.
    private Map<String, Long> partSizes(final SourceTypeProfiles profiles) {
        final Map<String, Long> sizes = new LinkedHashMap<>();
        for (final String key : profiles.partsRead()) {
            final int colon = key.indexOf(':');
            final long size = partSize(Long.parseLong(key.substring(0, colon)),
                    Integer.parseInt(key.substring(colon + 1)));
            if (size > 0) {
                sizes.put(key, size);
            }
        }
        return sizes;
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

    // The kind fields the first stream's start suggests, the source type field aside (kinds are told apart within
    // source types); empty for text, or when it can't be read.
    private static Optional<KindFields.Choice> kindChoice(final Heads heads, final String sourceField) {
        final List<Map<String, Object>> records = heads.get();
        if (records.isEmpty()) {
            return Optional.empty();
        }
        if (sourceField == null) {
            return Optional.of(KindFields.choose(records));
        }
        // A reading's fields read whole are set aside; a held value's names aren't kind fields of the record.
        final SourceTypeReading reading = SourceTypeReading.cached(sourceField);
        final Set<String> aside = reading == null
                ? Set.of(sourceField)
                : reading.paths().stream()
                        .filter(path -> path.held().isEmpty())
                        .map(SourceTypeReading.Path::field)
                        .collect(Collectors.toSet());
        final List<Map<String, Object>> without = records.stream()
                .map(record -> {
                    final Map<String, Object> copy = new LinkedHashMap<>(record);
                    copy.keySet().removeAll(aside);
                    return copy;
                })
                .toList();
        return Optional.of(KindFields.choose(without));
    }

    // The first stream's start, its records parsed, when they are structured (delimited, JSON, key=value); none for
    // text, or when it can't be read.
    private List<Map<String, Object>> peekRecords(final Meta first,
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
                    ? List.of()
                    : parsed;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No kind fields from stream {}: {}", first.getId(), e.getMessage()));
            return List.of();
        }
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
                                           final Map<String, Object> state,
                                           final Integer fewer) {
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
                : ", stream(s) " + idsSaid(inPart) + " read only in part");
        final String given = request.acceptAsIs().strip();
        // Where no form can be shown, or this one couldn't be, accept_as_is is what the user said in the chat; 'true'
        // is their yes once the agent was told to ask them (seen: 'true' taken for "ask them" every time, so the
        // user's yes was asked for again for ever), and before that, the agent's wish to go ahead, put to them.
        final boolean inChat = consent.takesChatAnswer(context, NAME, ACCEPT_QUESTION);
        final Optional<String> answer;
        if (inChat && (!"true".equalsIgnoreCase(given) || consent.wasAskedInChat(context, NAME, ACCEPT_QUESTION))) {
            answer = Optional.of(given);
        } else if (inChat) {
            answer = Optional.empty();
        } else {
            answer = consent.answer(call, context, NAME, said + ". Going ahead with the survey as it is, kinds of "
                                                         + "event only in streams not read go to the mapping's "
                                                         + "catch-all rules, until review_coverage finds them after "
                                                         + "processing.", ACCEPT_QUESTION, List.of(ACCEPT,
                    KEEP_SURVEYING));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("feed", feed);
        if (answer.isEmpty()) {
            consent.askInChat(context, NAME, ACCEPT_QUESTION);
            result.put("status", "needs_user");
            if (inPart.size() > Consent.LINES_SHOWN) {
                result.put("read_in_part", inPart);
            }
            result.put("hint", consent.whyInChat(context, NAME, ACCEPT_QUESTION) + ". Ask the user in the chat "
                               + "whether to go ahead with the survey as it is (" + said + "), then call " + NAME
                               + " again with " + ACCEPT_ARG + " as they said ('true' for their yes).");
            return result;
        }
        final String answered = answer.get();
        if (!inChat && Consent.exactly(answered, GO_AHEAD_ANSWERS).isEmpty()
            && Consent.exactly(answered, KEEP_SURVEYING_ANSWERS).isEmpty()) {
            // Words in the form that are neither answer, whole (seen in testing, in another form: a question for the
            // agent taken for an answer): put to the agent, not taken for keeping on surveying.
            throw consent.wroteInstead(context, NAME, ACCEPT_QUESTION, answered, "ask them in the chat whether to go "
                                                                         + "ahead with the survey as it is ("
                                                                         + said + "), and call " + NAME + " again "
                                                                         + "with " + ACCEPT_ARG + " as they say "
                                                                         + "('true' for their yes).");
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
        return keep(context, new SurveyRecords(request.build(), record, state, request.examples(), fewer),
                result, Map.of());
    }

    // Whether the user's answer goes ahead: the button, or their own yes.
    private static boolean agreesToGoAhead(final String answer) {
        final String text = answer.strip().toLowerCase(Locale.ROOT);
        return ACCEPT.equalsIgnoreCase(answer.strip()) || "true".equals(text) || text.startsWith("yes")
               || text.startsWith("go ahead") || text.startsWith("accept") || text.startsWith("ok")
               || text.startsWith("proceed");
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
        final String said = inPartSaid(reading, other, false);
        // The read_deeper answer only when it answers this question (stop, or step): "1,000 more" was for parts that
        // can be read deeper.
        final String given = NullSafe.string(request.readDeeper()).strip();
        final boolean answersThis = SurveyDepth.decide(given).stop() || STOP_THERE.equalsIgnoreCase(given)
                                    || given.toLowerCase(Locale.ROOT).contains("step");
        final Optional<String> answer = answersThis
                ? Optional.of(given)
                : consent.answer(call, context, NAME, inPartSaid(reading, other, true) + " Kinds of event that only "
                                                       + "appear later in them haven't been seen.",
                        STOP_QUESTION, List.of(STOP_THERE, STEP_FURTHER));
        if (answer.isEmpty()) {
            result.put("coverage", "NOT COVERED: " + said + " The user decides whether to stop and accept them as "
                                   + "read, or step records further in first.");
            result.put("hint", consent.whyInChat(context, NAME, STOP_QUESTION) + ". Ask the user in the chat whether "
                               + "to stop and accept stream(s) " + other + " as read, "
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
        result.put("coverage", "NOT COVERED: " + said + " The user chose to step records further in first.");
        result.put("hint", "Step records further in these streams with step_records (their windows in per_stream), "
                           + "then ask the user again whether to stop there, and call " + NAME + " with stream_ids "
                           + other + " and " + READ_DEEPER_ARG + " as they say. Don't raise " + MAX_CHARS_ARG
                           + ": a call reads no more of them.");
    }

    // What the user is told of streams read only in part that can't be read further: how much of each was read, and
    // why it can't be read further (seen in testing: "can't be read further in a call (records read: per_stream)",
    // a name in the agent's result, left the user asking what it meant).
    // A form's message (forForm) names the first few, and how many more (per_stream, in the chat, has each).
    private static String inPartSaid(final Reading reading, final List<Long> streams, final boolean forForm) {
        final List<String> each = new ArrayList<>();
        for (final long id : streams) {
            final Map<String, Object> stream = reading.perStream.stream()
                    .filter(entry -> entry.get("stream") instanceof final Number number && number.longValue() == id
                                     && entry.containsKey("records"))
                    .findFirst()
                    .orElse(Map.of());
            final String records = stream.get("records") instanceof final Number number
                    ? String.format("%,d", number.longValue())
                    : "some";
            final Object why = stream.get(NOT_READ_FURTHER);
            // A stream of several parts names those read (seen in user testing: told only "can't read further into
            // it" of a stream of 787 parts read through its first 20).
            final List<Integer> partsRead = JsonValues.listOrEmpty(stream.get(PARTS_READ)).stream()
                    .filter(Number.class::isInstance)
                    .map(part -> ((Number) part).intValue())
                    .toList();
            final String where = partsRead.isEmpty()
                    ? " from its start"
                    : " from part" + (partsRead.size() == 1
                            ? " "
                            : "s ") + PartSampler.said(partsRead);
            each.add("Stream " + id + ": " + records + " records" + where + (why != null
                    ? "; " + why
                    : "") + ".");
        }
        final List<String> ids = streams.stream().map(String::valueOf).toList();
        final String named = ids.size() == 1
                ? "stream " + ids.getFirst()
                : forForm && ids.size() > Consent.LINES_SHOWN
                        ? "streams " + idsSaid(streams)
                        : "streams " + String.join(", ", ids.subList(0, ids.size() - 1)) + " and " + ids.getLast();
        return "The survey read only part of " + named + ", too big to read whole in one go." + (forForm
                ? "\n" + Consent.firstLines(each, "each is in the survey's per_stream")
                : " " + String.join(" ", each));
    }

    // Stream ids for a form's message: the first few, and how many more.
    private static String idsSaid(final List<Long> ids) {
        return ids.size() <= Consent.LINES_SHOWN
                ? ids.toString()
                : ids.subList(0, Consent.LINES_SHOWN) + " and " + (ids.size() - Consent.LINES_SHOWN) + " more";
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
                if (reading.scopes != null) {
                    reading.scopes.prepare(part.stream());
                }
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

    // Each stream's head, its records added to the shapes, until `enough` says the reading has what it is for.
    private Reading read(final ToolCallContext context,
                         final CallProgress progress,
                         final String feed,
                         final List<Meta> streams,
                         final Shapes shapes,
                         final Limits limits,
                         final Map<Long, RawHead> peeked,
                         final SourceTypeProfiles profiles,
                         final StreamScopes scopes,
                         final Predicate<Reading> enough) {
        final Reading reading = new Reading();
        reading.peeked.putAll(peeked);
        reading.scopes = scopes;
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
                if (profiles != null) {
                    profiles.streamCreated(meta.getId(), meta.getCreateMs());
                }
                scopes.prepare(meta.getId());
                progress.nowAndThen("Reading stream " + meta.getId() + " (" + n + " of " + streams.size() + "): "
                                    + reading.recordsRead + " records read so far");
                readStream(reading, meta, shapes, limits);
                if (reading.stopped() || enough.test(reading)) {
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
    // data to different parts, so a budget the parts shared could miss kinds of event in the later ones). A stream
    // of more parts than a call reads has them chosen spread through it, from its first to its last (seen in user
    // testing: an OpenWebUI audit stream of 787 parts was read through its first 20 only, and the kinds of event
    // only in its later parts could never be seen). Every part chosen is read to the same depth, its own budget.
    private void readStream(final Reading reading, final Meta meta, final Shapes shapes, final Limits limits) {
        int newHere = 0;
        long readHere = 0;
        int windowsHere = 0;
        boolean cut = false;
        boolean inPart = false;
        // Why the stream can't be read further than this call reads, said to the user if it isn't read whole.
        String notFurther = null;
        final RawHead peekedHead = reading.peeked.remove(meta.getId());
        int parts = peekedHead != null
                ? peekedHead.parts()
                : partCount(meta.getId());
        List<Integer> chosen = PartSampler.spread(parts, limits.maxParts());
        final List<Integer> partsRead = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            final int part = chosen.get(i);
            if (timeIsUp() && !(i == 0 && peekedHead != null)) {
                // Stopped part-way: what is read of it so far counts, as the start of the stream.
                reading.stop();
                cut = true;
                notFurther = "the survey's time ran out";
                break;
            }
            RawHead head;
            try {
                head = i == 0 && peekedHead != null
                        ? peekedHead
                        : streamReader.readHead(meta.getId(), part, limits.maxChars());
            } catch (final StreamReadException e) {
                // A feed's oldest streams are read too, and the likeliest to be on a volume Stroom can no longer
                // read: said for that stream, the rest of the survey still made.
                reading.perStream.add(entry("stream", meta.getId(), "part", part, "error",
                        "Stroom couldn't read it: " + e.getMessage()));
                break;
            }
            if (head.parts() > parts) {
                // More parts than were counted: chosen again (the first is still the first).
                parts = head.parts();
                chosen = PartSampler.spread(parts, limits.maxParts());
            }
            final int budget = limits.maxRecords();
            // Within a build's scope, the budget is of the scope's records: more of the head is split to find them.
            final boolean scoped = reading.scopes != null && reading.scopes.scoped(meta.getId());
            final int most = budget * SPLIT_MORE * (scoped
                    ? SCOPE_SPREAD
                    : 1);
            if (head.truncated() && startsAsArray(head.text())) {
                // One JSON array cut by the read: its whole events read instead, one too long to sample left out
                // (seen in user testing: a newline-free array cut inside a \\u escape).
                head = wholeEvents(reading, meta.getId(), part, head, new PartSampler.Limits(limits.maxEventChars(),
                        limits.maxChars()), limits.maxEventChars());
            }
            partsRead.add(part);
            cut = cut || head.truncated();
            final RecordSplitter.Chunk chunk;
            try {
                // More than are read, so those read can be spread over them (see below).
                chunk = RecordSplitter.split(head.text(), most, head.truncated());
            } catch (final RuntimeException e) {
                // A part this detector cannot split.
                reading.perStream.add(entry("stream", meta.getId(), "part", part, "error",
                        "could not split records: " + e.getMessage()));
                continue;
            }
            if (chunk.note() != null) {
                reading.perStream.add(entry("stream", meta.getId(), "part", part, "note", chunk.note()));
            }
            // The part's records are judged with its own meta (a source type field read from it).
            shapes.at(meta.getId(), part);
            if (reading.format != null && !chunk.format().equals(reading.format)) {
                reading.perStream.add(entry("stream", meta.getId(), "part", part, "note",
                        "reads as " + chunk.format() + ", not " + reading.format + "; skipped"));
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
                picks = LongStream.range(0, scoped
                        ? throughScope(chunk, shapes, 0, inOrder)
                        : inOrder).boxed().toList();
            } else if (scoped) {
                // As below, the records read spread over as many more as the scope's share of the head asks, so
                // about the budget of them are the scope's.
                final int want = windowed
                        ? Math.max(1, budget / 2)
                        : budget;
                final int spread = scopeSpread(chunk, shapes, want);
                picks = windowed
                        ? RecordPicks.headAndSpread(Math.min(chunk.records().size(), spread), spread)
                        : RecordPicks.headAndSpread(chunk.records().size(), spread);
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
                // Only the scope's records count as read: the others are passed over (counted by source type).
                if (shapes.inScope(chunk, (int) index)) {
                    partRead++;
                }
                if (shapes.add(chunk, (int) index, meta.getId(), part)) {
                    newHere++;
                }
            }
            List<List<Long>> ranges = List.of();
            if (windowed && partRead < budget && !timeIsUp()) {
                final WindowsRead read = readWindows(reading, meta, part, head, chunk, shapes, budget - partRead,
                        scoped);
                windowsHere += read.windows();
                partRead += read.records();
                newHere += read.newShapes();
                ranges = read.ranges();
            }
            // Every record of the part read: its whole text was read, and split into fewer records than the most.
            final boolean partWhole = !head.truncated() && chunk.records().size() < most
                                      && picks.size() == chunk.records().size();
            if (!partWhole) {
                inPart = true;
                if (!windowable(chunk)) {
                    notFurther = WINDOWED.contains(chunk.format())
                            ? "its records run over several lines, so it can't be read from part-way through"
                            : "its records (" + chunk.format() + ") can't be read from part-way through";
                }
                if (limits.whole() && windowable(chunk)) {
                    reading.partial.add(new SurveyDepth.Part(meta.getId(), part, picks.size(), ranges,
                            chunk.header(), chunk.format(), partSize(meta.getId(), part),
                            estimate(head, meta.getId(), part, picks.size()), false, null));
                }
            }
            readHere += partRead;
        }
        reading.recordsRead += readHere;
        final boolean someParts = parts > chosen.size();
        if (someParts) {
            notFurther = "it has " + parts + " parts, more than a call reads (" + limits.maxParts() + "), so "
                         + chosen.size() + " were read, spread through it from its first to its last";
        }
        final Map<String, Object> entry = entry("stream", meta.getId(), "created", meta.getCreateMs() != 0
                        ? FeedSampling.iso(meta.getCreateMs())
                        : null,
                "records", readHere, "parts", parts, "head_only", cut || someParts || inPart,
                "new_shapes", newHere);
        if (parts > 1) {
            entry.put(PARTS_READ, partsRead);
        }
        if (windowsHere > 0) {
            entry.put("windows", windowsHere);
        }
        if (notFurther != null && Boolean.TRUE.equals(entry.get("head_only"))) {
            entry.put(NOT_READ_FURTHER, notFurther);
        }
        entry.putAll(FeedSampling.charsetEntry(streamReader.charsetReport(meta.getId())));
        reading.perStream.add(entry);
    }

    // How many parts a stream has; 1 when it can't be told (its first part's head then tells).
    private int partCount(final long streamId) {
        try {
            return Math.max(1, streamReader.partCount(streamId));
        } catch (final RuntimeException e) {
            return 1;
        }
    }

    // Whether a part's head starts as a JSON array.
    private static boolean startsAsArray(final String text) {
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (!Character.isWhitespace(c) && c != '﻿') {
                return c == '[';
            }
        }
        return false;
    }

    // A part that is one JSON array read as its whole events (PartSampler), what was left out said in per_stream;
    // the head as read when the part can't be read so.
    private RawHead wholeEvents(final Reading reading,
                                final long streamId,
                                final int part,
                                final RawHead head,
                                final PartSampler.Limits limits,
                                final int maxEventChars) {
        final PartSampler.Sample sample;
        try {
            sample = streamReader.samplePart(streamId, part, limits);
        } catch (final StreamReadException e) {
            return head;
        }
        if (sample == null || sample.mode() != PartSampler.Mode.JSON_ARRAY) {
            return head;
        }
        sample.said(maxEventChars).forEach(said -> reading.perStream.add(entry("stream", streamId, "part", part,
                "note", said)));
        return new RawHead(sample.text(), !sample.whole(), head.parts());
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
                              final int maxRecords,
                              final boolean scoped) {
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
                        : chunk.header() + "\n" + window.text(), (tail
                        ? take * SPLIT_MORE
                        : take) * (scoped
                        ? SCOPE_SPREAD
                        : 1), false);
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
            final int size = more.records().size();
            final int from;
            final int to;
            if (scoped) {
                // As many records as hold `take` of the scope's: from the window's start, or back from the tail's end.
                from = tail
                        ? backThroughScope(more, shapes, take)
                        : 0;
                to = tail
                        ? size
                        : throughScope(more, shapes, 0, take);
            } else {
                from = tail
                        ? Math.max(0, size - take)
                        : 0;
                to = Math.min(size, from + take);
            }
            for (int index = from; index < to; index++) {
                if (shapes.inScope(more, index)) {
                    records++;
                }
                if (shapes.add(more, index, meta.getId(), part, firstRecord)) {
                    newShapes++;
                }
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
        return !LINES_UNLESS_SEEN.contains(chunk.format())
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

    // The index after the first `take` of the scope's records from `from`: the chunk's end when it holds fewer.
    private static int throughScope(final RecordSplitter.Chunk chunk,
                                    final Shapes shapes,
                                    final int from,
                                    final int take) {
        int found = 0;
        int index = from;
        while (index < chunk.records().size() && found < take) {
            if (shapes.inScope(chunk, index)) {
                found++;
            }
            index++;
        }
        return index;
    }

    // The index from which the chunk's last records hold `take` of the scope's: 0 when it holds fewer.
    private static int backThroughScope(final RecordSplitter.Chunk chunk, final Shapes shapes, final int take) {
        int found = 0;
        int index = chunk.records().size();
        while (index > 0 && found < take) {
            index--;
            if (shapes.inScope(chunk, index)) {
                found++;
            }
        }
        return index;
    }

    // How many of a head's records to spread the picks over for about `want` of them to be the scope's: by the
    // scope's share of the head, at most the head; `want` when the head holds none of the scope's.
    private static int scopeSpread(final RecordSplitter.Chunk chunk, final Shapes shapes, final int want) {
        final int size = chunk.records().size();
        int inScope = 0;
        for (int index = 0; index < size; index++) {
            if (shapes.inScope(chunk, index)) {
                inScope++;
            }
        }
        if (inScope == 0) {
            return Math.min(size, want);
        }
        return (int) Math.min(size, (long) Math.ceil((double) want * size / inScope));
    }

    /// The survey's source types as kept, with the build's scope where the survey keeps none and the build's own
    /// registry has one this build chose: record_source_types may come before the build's first survey (seen in an
    /// eval of a later build on a promoted aggregated feed: the group was chosen first, the survey was unscoped,
    /// and the plan's source types step stayed to do), and the survey reads through the scope whichever came first.
    ///
    /// @param build   The build, or null.
    /// @param feed    The feed.
    /// @param sources The survey state's source types, or empty.
    /// @return The source types, the scope (with the registry's rows) and its field added where seeded.
    Map<String, Object> withRegistryScope(final String build,
                                          final String feed,
                                          final Map<String, Object> sources) {
        if (build == null || sources.get(SourceTypeProfiles.SCOPE) != null
            || Boolean.FALSE.equals(sources.get("aggregated"))) {
            return sources;
        }
        final Optional<SourceTypeRegistries.Found> found;
        try {
            found = besideFeed.registry(build, feed).filter(registry -> !registry.promoted());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No registry read for feed {}: {}", feed, e.getMessage()));
            return sources;
        }
        if (found.isEmpty()) {
            return sources;
        }
        final Map<String, Object> state = found.get().state();
        final Map<String, Object> scope = JsonValues.mapOrEmpty(state.get(SourceTypeProfiles.SCOPE));
        final String field = JsonValues.text(state.get(SourceTypeProfiles.FIELD));
        final Object keptField = sources.get(SourceTypeProfiles.FIELD);
        if (scope.isEmpty() || !build.equals(JsonValues.text(scope.get(SourceTypeScope.BUILD)))
            || NullSafe.isBlankString(field) || (keptField != null && !field.equals(keptField))) {
            return sources;
        }
        final Map<String, Object> seeded = new LinkedHashMap<>(sources);
        if (keptField == null) {
            seeded.putAll(sourcesChosen(field, REGISTRY));
        }
        final Map<String, Object> kept = new LinkedHashMap<>(scope);
        kept.put(SourceTypeScope.ROWS, SourceTypeScope.kept(SourceTypeRegistry.rows(state)));
        seeded.put(SourceTypeProfiles.SCOPE, kept);
        return seeded;
    }

    // The source type field of the feed's registry (the build's, else the one beside the feed); empty when it has
    // none.
    private Optional<String> registryField(final String build, final String feed) {
        try {
            return besideFeed.registry(build, feed)
                    .map(found -> found.state().get(SourceTypeProfiles.FIELD))
                    .filter(field -> field instanceof String text && !text.isBlank())
                    .map(String.class::cast);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No registry read for feed {}: {}", feed, e.getMessage()));
            return Optional.empty();
        }
    }

    // The rows of the feed's registry (the build's, else the one beside the feed); none when it has none.
    private List<SourceTypeRows.Row> registryRows(final String build, final String feed) {
        try {
            return besideFeed.registry(build, feed)
                    .map(found -> SourceTypeRegistry.rows(found.state()))
                    .orElse(List.of());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No registry read for feed {}: {}", feed, e.getMessage()));
            return List.of();
        }
    }

    // Every source type, told apart by the rows a scope kept (another build's) or the registry's, for reading again
    // the streams a narrower scope read; null when no source type field is kept.
    private SourceTypeScope readerOf(final Map<String, Object> sources, final String build, final String feed) {
        if (!(sources.get(SourceTypeProfiles.FIELD) instanceof final String field) || field.isBlank()) {
            return null;
        }
        final List<SourceTypeRows.Row> kept = SourceTypeScope.rowsOf(JsonValues.mapOrEmpty(
                sources.get(SourceTypeProfiles.SCOPE)).get(SourceTypeScope.ROWS));
        return new SourceTypeScope(build, field, kept.isEmpty()
                ? registryRows(build, feed)
                : kept, null);
    }

    // What the call read through the build's scope: each stream read signed by it (kept in the survey state, so a
    // scope that adds source types reads them again for those), the scope and its share of the feed said, and the
    // records of other source types passed over.
    private static void noteScope(final Map<String, Object> result,
                                  final StreamScopes scopes,
                                  final Reading reading,
                                  final List<SurveyDepth.Part> depth,
                                  final Map<String, Object> keptSources,
                                  final SourceTypeProfiles profiles) {
        if (scopes.scope == null && scopes.readAgain.isEmpty()) {
            return;
        }
        final Set<Long> read = new TreeSet<>(reading.streamsRead());
        depth.forEach(part -> read.add(part.stream()));
        final Map<String, Object> signed = new LinkedHashMap<>();
        for (final long stream : read) {
            if (scopes.scope != null) {
                signed.put(String.valueOf(stream), scopes.scope.signature());
            } else if (scopes.readAgain.containsKey(stream)) {
                signed.put(String.valueOf(stream), List.of(SourceTypeScope.ALL));
            }
        }
        if (!signed.isEmpty()) {
            result.put(SurveyDoc.SCOPE_READ, signed);
        }
        if (!scopes.readAgain.isEmpty()) {
            final List<Long> again = scopes.readAgain.keySet().stream().filter(read::contains).toList();
            if (!again.isEmpty()) {
                result.put("read_again", "Stream(s) " + again + " were read before for other source types only: "
                                         + "read again for those they weren't, so the survey's coverage of them "
                                         + "starts again.");
            }
        }
        if (scopes.scope == null) {
            return;
        }
        final Map<String, Object> sources = profiles == null
                ? keptSources
                : SourceTypeProfiles.merge(keptSources, profiles.toState(Map.of()));
        final Map<String, Object> share = scopes.scope.share(sources);
        final Map<String, Object> said = new LinkedHashMap<>();
        said.put("build", scopes.scope.build());
        said.put(SourceTypeScope.SOURCE_TYPES, share.get(SourceTypeScope.SOURCE_TYPES));
        said.put("share_percent", share.get("share_percent"));
        said.put("records_passed_over", scopes.shapes.passedOver());
        said.put("note", "Only the scope's records were surveyed (records_read, the kinds, coverage); the "
                         + scopes.shapes.passedOver() + " record(s) of other source types read past were counted by "
                         + "source type for the registry, and nothing more.");
        result.put("scope", said);
        if (result.get("coverage") instanceof final String coverage) {
            result.put("coverage", coverage + " (within the scope: " + scopes.scope.shareSaid(sources) + ")");
        }
    }

    // The call's result.
    // An aggregated feed's shapes and locations each say the source types their records are of (by the rows of
    // `naming`, the scope's or the registry's; the source type field's values where there are none). Seen in chat
    // 177: the agent checked its group's translation with an address from a SharePoint record, outside the group,
    // as nothing said which source type an example was of, and the check found nothing.
    private Map<String, Object> result(final Outcome outcome,
                                       final Reading reading,
                                       final Shapes shapes,
                                       final ToolCallContext context,
                                       final SourceTypeScope naming) {
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
        result.put("shapes", shapesShown(found, counts, drop, naming));
        result.put("new_shapes", newShapes.size());
        // Records of a kind left untranslated are stepped too, expecting no Event.
        result.put("locations", locations(toStep, drop, naming));
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
                                                         final Map<String, String> drop,
                                                         final SourceTypeScope naming) {
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
            final List<String> sourceTypes = sourceTypesOf(shape, naming);
            if (!sourceTypes.isEmpty()) {
                entry.put(SourceTypeScope.SOURCE_TYPES, sourceTypes);
            }
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
                                                       final Map<String, String> drop,
                                                       final SourceTypeScope naming) {
        final List<Map<String, Object>> locations = new ArrayList<>();
        for (final Map<String, Object> shape : toStep) {
            final String signature = signature(shape);
            final List<String> sourceTypes = sourceTypesOf(shape, naming);
            for (final Map<String, Object> example : (List<Map<String, Object>>) shape.get("examples")) {
                final Map<String, Object> location = new LinkedHashMap<>((Map<String, Object>) example.get(
                        "location"));
                location.put("shape", signature);
                if (!sourceTypes.isEmpty()) {
                    location.put(SourceTypeScope.SOURCE_TYPES, sourceTypes);
                }
                if (drop.containsKey(signature)) {
                    location.put("expect", "none");
                }
                locations.add(location);
            }
        }
        return locations;
    }

    /// The source types an aggregated feed's shape's records are of: the source type field's values they had
    /// ([Shapes#SOURCE_VALUES]), each named by the first of the rows that matches it, or as itself where there are
    /// no rows or none matches.
    ///
    /// @param shape  A shape found.
    /// @param naming The scope or registry whose rows name the values, or null.
    /// @return The source types, in the order first met; none for a feed with no source type field.
    static List<String> sourceTypesOf(final Map<String, Object> shape, final SourceTypeScope naming) {
        final Set<String> named = new LinkedHashSet<>();
        for (final String value : JsonValues.strings(shape.get(Shapes.SOURCE_VALUES))) {
            named.add(naming == null
                    ? value
                    : SourceTypeRows.first(naming.rows(), value).map(SourceTypeRows.Row::name).orElse(value));
        }
        return List.copyOf(named);
    }

    // Writes the survey into the build's survey doc (creating it the first time).
    private Map<String, Object> keep(final ToolCallContext context,
                                     final SurveyRecords kept,
                                     final Map<String, Object> result,
                                     final Map<String, Map<String, Object>> shapes) {
        if (kept.fewer() != null) {
            // The user's agreement to fewer streams a call, kept with the build's survey.
            result.put(SurveyDoc.FEWER_STREAMS, kept.fewer());
        }
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

    // Why no stream gave records: each stream's (and part's) error or note, the first few, each cut short (seen:
    // every part of an Azure audit feed failed to split, and the bare refusal left the agent to guess why).
    static String whyNothingReadable(final List<Map<String, Object>> perStream) {
        final List<String> said = perStream.stream()
                .filter(stream -> stream.get("error") != null || stream.get("note") != null)
                .map(stream -> "stream " + stream.get("stream") + (stream.get("part") == null
                        ? ""
                        : " part " + stream.get("part")) + ": " + head(String.valueOf(
                        stream.get("error") != null
                                ? stream.get("error")
                                : stream.get("note")), MOST_SAID_CHARS))
                .toList();
        if (said.isEmpty()) {
            return "no stream gave any records";
        }
        final String more = said.size() > MOST_SAID_STREAMS
                ? "; and " + (said.size() - MOST_SAID_STREAMS) + " more"
                : "";
        return String.join("; ", said.subList(0, Math.min(MOST_SAID_STREAMS, said.size()))) + more;
    }

    private static String whyNothingReadable(final Reading reading) {
        return whyNothingReadable(reading.perStream);
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
    /// @param readDeeper      What the user said of reading parts deeper, or null.
    /// @param kindField       The kind field the user gave, or null.
    /// @param acceptAsIs      The user's going ahead with the survey as it is, or null.
    /// @param sourceTypeField The source type field the user gave (or `none`), or null.
    /// @param aggregated      True when the user said the feed aggregates sources.
    /// @param find            The registry row whose records are looked for, or null.
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
                           String acceptAsIs,
                           String sourceTypeField,
                           boolean aggregated,
                           String find) {

    }

    /// How much of each stream is read.
    ///
    /// @param maxChars      The most characters of each part's head (its own, not shared by a stream's parts).
    /// @param maxParts      The most parts of a stream, chosen spread through it.
    /// @param maxRecords    The most records of each part.
    /// @param whole         Whether the streams are to be read whole (a sample's, or a feed's last): each part in
    ///                      order from its start, so the user can have it read deeper.
    /// @param maxEventChars The longest event of a part that is one JSON array kept (a longer one is left out).
    private record Limits(int maxChars, int maxParts, int maxRecords, boolean whole, int maxEventChars) {

    }

    /// Where the survey is kept.
    ///
    /// @param build    The build, or null.
    /// @param record   The build's survey doc for the feed as read before the survey, or null.
    /// @param state    The state read from it, or null.
    /// @param examples The most examples kept a shape.
    /// @param fewer    The streams a call the user agreed to, or null.
    private record SurveyRecords(String build,
                                 SurveyRecord record,
                                 Map<String, Object> state,
                                 int examples,
                                 Integer fewer) {

    }

    /// The source type field, as decided for the call.
    ///
    /// @param decision What the survey state keeps of it (`field` or `aggregated: false`, `by`, `at`), or null
    ///                 when nothing is decided.
    /// @param field    The field, or null.
    /// @param early    A result to return at once (the user is to be asked in the chat), or null.
    /// @param changed  True when it differs from the one the survey kept: the old counts go.
    private record SourceDecision(Map<String, Object> decision,
                                  String field,
                                  Map<String, Object> early,
                                  boolean changed) {

    }

    /// What the build's survey kept, as a call reads it.
    ///
    /// @param record  The build's survey doc, or null.
    /// @param state   Its state (streams since deleted left out), or null.
    /// @param sources Its source types (field, counts, scope), or empty.
    /// @param kinds   Its kind fields, or empty.
    /// @param gone    Streams it read that have been deleted since.
    /// @param dropped Shapes seen only in those streams.
    private record Surveyed(SurveyRecord record,
                            Map<String, Object> state,
                            Map<String, Object> sources,
                            Map<String, Object> kinds,
                            Set<Long> gone,
                            Set<String> dropped) {

    }

    /// A search for one registry row's records.
    ///
    /// @param row        The row.
    /// @param named      True when the streams were named (stream_ids), not picked from the feed.
    /// @param older      True when the feed has older streams than those picked.
    /// @param passedOver The records of other source types read past.
    private record Search(SourceTypeRows.Row row, boolean named, boolean older, long passedOver) {

    }

    // The first stream's head, parsed, read when first asked for.
    private static final class Heads {

        private final Supplier<List<Map<String, Object>>> reader;
        private List<Map<String, Object>> records;

        private Heads(final Supplier<List<Map<String, Object>>> reader) {
            this.reader = reader;
        }

        private List<Map<String, Object>> get() {
            if (records == null) {
                records = reader.get();
            }
            return records;
        }

        private boolean peekedAlready() {
            return records != null;
        }
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

    // What each stream is read for: the build's scope; or, for a stream read before for other source types only, the
    // scope's source types it wasn't read for (its records counted by source type when it was first read).
    private static final class StreamScopes {

        private final Shapes shapes;
        private final SourceTypeScope scope;
        private final SourceTypeScope reader;
        private final Map<Long, List<String>> readAgain;
        private final SourceTypeProfiles profiles;

        private StreamScopes(final Shapes shapes,
                             final SourceTypeScope scope,
                             final SourceTypeScope reader,
                             final Map<Long, List<String>> readAgain,
                             final SourceTypeProfiles profiles) {
            this.shapes = shapes;
            this.scope = scope;
            this.reader = reader;
            this.readAgain = readAgain;
            this.profiles = profiles;
        }

        // Sets the shapes to read a stream through what it is read for.
        private void prepare(final long stream) {
            final List<String> signed = readAgain.get(stream);
            if (signed != null && reader != null) {
                shapes.scope(reader.notReadFor(signed));
                shapes.observe(null);
                return;
            }
            shapes.scope(scope == null
                    ? null
                    : scope::inScope);
            shapes.observe(profiles == null
                    ? null
                    : profiles::add);
        }

        // Whether a stream is read through a scope: its budget is of the scope's records.
        private boolean scoped(final long stream) {
            return scope != null || reader != null && readAgain.containsKey(stream);
        }
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
        // What each stream is read for (a build's scope), or null.
        private StreamScopes scopes;

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
    /// What a source type field the user names is checked against: the streams to be read, as far as they are read.
    ///
    /// @param streams    The streams to be read.
    /// @param maxParts   The most parts read of a stream.
    /// @param attributes Their parts' meta attributes, read once each.
    private record Asked(List<Long> streams, int maxParts, PartAttributes attributes) {

    }

    private record WindowsRead(int windows, int records, int newShapes, List<List<Long>> ranges) {

    }
}
