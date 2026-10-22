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

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// `create_feed` - creates a feed in the build folder, under the name the agent proposes and the user confirms or
/// corrects (the Stroom MCP server's `tools/feeds.py` `create_feed`). In a build on an existing feed's data, it makes
/// only a test feed: for sample files the user chose (`user_samples`), or the one the build's Events are written to
/// (`test_feed`).
class CreateFeedTool extends AbstractBuiltInTool {

    static final String NAME = "create_feed";
    static final String BUILD_ARG = "build";
    static final String NAME_ARG = "name";
    static final String ENCODING_ARG = "encoding";
    static final String STREAM_TYPE_ARG = "stream_type";
    static final String DESCRIPTION_ARG = "description";
    static final String USER_SAMPLES_ARG = "user_samples";
    static final String TEST_FEED_ARG = "test_feed";
    /// The build's argument, as every build tool describes it.
    static final String BUILD_DESCRIPTION = "Build name; its workspace folder is created if needed.";

    // How many of a feed name's earlier streams are looked at.
    private static final int EARLIER = 50;

    private final BuildFeeds buildFeeds;
    private final WriteGuard guard;
    private final BuildRecords records;
    private final Consent consent;
    private final BuildPlan plan;
    private final StreamReader streamReader;

    @Inject
    CreateFeedTool(final BuildFeeds buildFeeds,
                   final WriteGuard guard,
                   final BuildRecords records,
                   final Consent consent,
                   final BuildPlan plan,
                   final StreamReader streamReader) {
        this.buildFeeds = buildFeeds;
        this.guard = guard;
        this.records = records;
        this.consent = consent;
        this.plan = plan;
        this.streamReader = streamReader;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean isReadOnly() {
        // It creates a feed.
        return false;
    }

    @Override
    public boolean confirmsItself() {
        // The user confirms (or corrects) the feed's name in a form before it is made.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("The feed is created in the build folder as the user, who needs Create there and Edit on the "
                      + "feed.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Create a feed in the build folder, under the name you propose: the user confirms "
                             + "it, or corrects it in the confirmation form, before anything is made (call this "
                             + "rather than asking for the name in the chat first). Stroom checks the name against "
                             + "its feed-name rule; a rejected name comes back with the rule so a compliant one can "
                             + "be proposed. A feed of the same name elsewhere is refused before the user is asked, "
                             + "saying where it is; one already in this build is returned as it is (existing: "
                             + "true). A build on an existing feed's data creates no feed, unless the user chooses "
                             + "sample files of their own instead (user_samples): then it is a test feed for those; "
                             + "or names the test feed its Events are written to (test_feed).")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(BUILD_ARG, BUILD_DESCRIPTION)
                        .addStringProperty(NAME_ARG, "Feed name following the environment's convention, e.g. "
                                                     + "'ACME-VPN-V1.0'.")
                        .addStringProperty(ENCODING_ARG, "Character encoding of the data.")
                        .addStringProperty(STREAM_TYPE_ARG, "Stream type received data is stored as.")
                        .addStringProperty(DESCRIPTION_ARG, "What the feed carries and where it comes from.")
                        .addProperty(USER_SAMPLES_ARG, stringArraySchema("Only in a build on an existing feed's "
                                                                         + "data: the sample files the user chose to "
                                                                         + "use instead of that feed's data, as they "
                                                                         + "named them. Files you found in the "
                                                                         + "workspace aren't their choice: ask "
                                                                         + "them."))
                        .addBooleanProperty(TEST_FEED_ARG, "Only in a build on an existing feed's data: true for "
                                                           + "the build's test feed, where processing writes the "
                                                           + "Events made from that feed's streams, under the name "
                                                           + "the user chose (else the server makes "
                                                           + "'<FEED>-MCP-TEST'). Processing writes to the newest "
                                                           + "one from then on.")
                        .required(BUILD_ARG, NAME_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            final Map<String, Object> result = create(call, context);
            return UserActionEntries.withActions(JsonResults.of(result), result);
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    private Map<String, Object> create(final ToolCall call, final ToolCallContext context) {
        final String build = required(call, BUILD_ARG);
        final String proposed = required(call, NAME_ARG);
        final String encoding = NullSafe.nonBlankStringElse(stringArg(call, ENCODING_ARG), "UTF-8");
        final String streamType = NullSafe.nonBlankStringElse(stringArg(call, STREAM_TYPE_ARG), "Raw Events");
        final String description = NullSafe.string(rawArg(call, DESCRIPTION_ARG));
        final boolean testFeed = JsonValues.truthy(flag(call, TEST_FEED_ARG));
        final Optional<Map<String, Object>> existing = buildFeeds.existingFeed(proposed, build);
        if (existing.isPresent()) {
            return plan.withNext(context, build, existing.get());
        }
        buildFeeds.refuseCaseTwin(proposed);
        final List<String> chosen = stringListArg(call, USER_SAMPLES_ARG);
        final List<String> sources = plan.worksFrom(build);
        // Seen in Ask Stroom AI: the user chose a name for the test feed (an earlier build's held the server's),
        // and the agent's create_feed (stream_type Events) was refused as a feed the build doesn't make.
        final boolean asOutput = !sources.isEmpty() && chosen.isEmpty() && (testFeed || "Events".equals(streamType));
        if (testFeed && sources.isEmpty()) {
            throw new ToolRefusal("Build '" + build + "' doesn't work from an existing feed: its pipelines write "
                                  + "their Events into the input's feed, so it has no test feed to name. Leave "
                                  + "test_feed out.");
        }
        if (!sources.isEmpty() && chosen.isEmpty() && !asOutput) {
            // Asked for by the user: an agent asked to onboard an existing feed used the sample files in the VS
            // Code workspace (seen on the local stack: refused processing the feed's stream, it made
            // '<FEED>-MCP-TEST' for a copy of the sample).
            throw new ToolRefusal(BuildFeeds.worksFromMessage(build, sources) + " If the user wants sample files of "
                                  + "their own used instead (ask them: files in the workspace aren't a choice they "
                                  + "made), call again with user_samples= the files they named. To name the test "
                                  + "feed the Events go to, call again with test_feed=true and the name.");
        }
        final Map<String, Object> details = new LinkedHashMap<>();
        final String summary;
        details.put("build", build);
        details.put("feed name", proposed);
        if (asOutput) {
            details.put("works from", sources);
            details.put("holds", "the Events the build's pipelines make from their streams; nothing is uploaded "
                                 + "to it");
            summary = "Create test feed '" + proposed + "' for the Events made from " + String.join(", ", sources);
        } else {
            details.put("encoding", encoding);
            details.put("stream type", streamType);
            if (!sources.isEmpty()) {
                details.put("works from", sources);
                details.put("the user's sample files, instead of its data", chosen);
            }
            summary = sources.isEmpty()
                    ? "Create feed '" + proposed + "'"
                    : "Create test feed '" + proposed + "' for your sample files (" + String.join(", ", chosen)
                      + ") instead of the data in " + String.join(", ", sources);
        }
        consent.require(call, context, Consent.Request.confirmation(NAME, summary, details)
                .withEditable(NAME_ARG, "Feed name", proposed));
        // The user may have corrected it in the form.
        final String name = consent.edited(context, NAME_ARG, proposed);
        if (!name.equals(proposed)) {
            // The name the user settled on in the form, checked as the proposed one was.
            final Optional<Map<String, Object>> theirs = buildFeeds.existingFeed(name, build);
            if (theirs.isPresent()) {
                return plan.withNext(context, build, theirs.get());
            }
            buildFeeds.refuseCaseTwin(name);
        }
        if (asOutput) {
            return testFeed(context, build, name, sources);
        }
        final DocRef ref = createFeed(name, build);
        final FeedDoc doc = buildFeeds.write(buildFeeds.read(ref).copy()
                .encoding(encoding)
                .streamType(streamType)
                .description(description)
                .build());
        final DocRef saved = new DocRef(FeedDoc.TYPE, doc.getUuid(), doc.getName());
        if (!sources.isEmpty()) {
            // The user's choice, kept: upload_sample takes samples for this feed only.
            records.update(build, saved, entry -> entry.put(BuildFeeds.USER_SAMPLES, chosen));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", FeedDoc.TYPE);
        result.put("uuid", doc.getUuid());
        result.put("name", doc.getName());
        result.put("stream_type", doc.getStreamType());
        result.put("encoding", doc.getEncoding());
        result.put("link", context.links().documentTarget(saved));
        // Sample files can go in through Stroom's own upload on the feed's Data tab (asked for by the user, for a
        // client in Stroom, which uploads nothing through the chat).
        UserActionEntries.withEntries(result, List.of(UserActionEntries.openDocument(context.links(), saved,
                "upload sample files on its Data tab, each as its own stream")));
        result.put("large_files", UploadSampleTool.sentByUser(context.links(), saved));
        earlierStreamsNote(doc).ifPresent(note -> result.put("note", note));
        return plan.withNext(context, build, result);
    }

    // The build's test feed for the Events made from the existing feeds it works from, under the user's name.
    private Map<String, Object> testFeed(final ToolCallContext context,
                                         final String build,
                                         final String name,
                                         final List<String> sources) {
        final FeedDoc made;
        try {
            made = buildFeeds.makeTestFeed(build, name, sources);
        } catch (final RuntimeException e) {
            throw rejectedName(e);
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", FeedDoc.TYPE);
        result.put("uuid", made.getUuid());
        result.put("name", made.getName());
        result.put("test_feed", true);
        result.put("works_from", sources);
        result.put("link", context.links().documentTarget(new DocRef(FeedDoc.TYPE, made.getUuid(), made.getName())));
        result.put("hint", "The build's Events go here from now on: create_processor_filter (or reprocess_streams) "
                           + "with the source feed's stream ids. Nothing is uploaded to it.");
        return plan.withNext(context, build, result);
    }

    private DocRef createFeed(final String name, final String build) {
        try {
            return guard.create(FeedDoc.TYPE, name, build, null, false, null);
        } catch (final RuntimeException e) {
            throw rejectedName(e);
        }
    }

    // Stroom's feed-name rule, given back so a compliant name can be proposed.
    private static RuntimeException rejectedName(final RuntimeException e) {
        if (NullSafe.string(e.getMessage()).contains("Invalid name")) {
            return new ToolRefusal("Stroom rejected the feed name: " + e.getMessage() + ". Propose a name that "
                                   + "matches the pattern shown.");
        }
        return e;
    }

    // A feed of this name made before and deleted leaves its streams under the name, out of sight in the UI.
    private Optional<String> earlierStreamsNote(final FeedDoc doc) {
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, doc.getName())
                .build();
        final List<Meta> rows = NullSafe.list(NullSafe.get(streamReader.find(expression, EARLIER, true),
                        ResultPage::getValues)).stream()
                .map(MetaRow::getMeta)
                .toList();
        final long created = Objects.requireNonNullElse(doc.getCreateTimeMs(), 0L);
        final List<Long> earlier = rows.stream()
                .filter(meta -> meta.getStatus() != Status.DELETED && meta.getCreateMs() < created)
                .map(Meta::getId)
                .toList();
        if (earlier.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Stroom still holds " + earlier.size() + (rows.size() == EARLIER
                ? "+"
                : "") + " stream(s) from an earlier feed named " + doc.getName() + ", since deleted (e.g. "
                           + earlier.subList(0, Math.min(5, earlier.size())) + "); the UI doesn't show them. They "
                           + "are not this build's samples: the plan and the stepping and processing tools use only "
                           + "the streams uploaded to this feed from now on. Tell the user.");
    }

    private static Object flag(final ToolCall call, final String name) {
        final Object value = argsMap(call).get(name);
        return value instanceof final String text
                ? Boolean.parseBoolean(text.strip())
                : value;
    }

    private String required(final ToolCall call, final String name) {
        final String value = stringArg(call, name);
        if (value == null) {
            throw new ToolRefusal("Give " + name);
        }
        return value;
    }
}
