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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.McpEndpointConfig;
import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.StroomLinks;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.sample.SampleTexts;
import stroom.ai.shared.AiToolCategory;
import stroom.config.common.UriFactory;
import stroom.docref.DocRef;
import stroom.meta.api.StandardHeaderArguments;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// `upload_sample` - sends sample data into a build's feed as the user, as the real source's data is received (the
/// Stroom MCP server's `tools/feeds.py` `upload_sample` and `upload_ticket`). Text the user pasted is sent at once
/// ([FeedUploads]); files on the user's disk are given a command each, for the user's terminal, that posts the
/// file to `/mcp/upload` as the user ([SampleUploads]), never through the model.
class UploadSampleTool extends AbstractBuiltInTool {

    static final String NAME = "upload_sample";
    static final String FEED_ARG = "feed";
    static final String SAMPLE_ARG = "sample";
    static final String FILES_ARG = "files";
    static final String HEADERS_ARG = "headers";
    static final String STREAM_TYPE_ARG = "stream_type";
    static final String EFFECTIVE_TIME_ARG = "effective_time";
    static final String RAW_REFERENCE = "Raw Reference";
    /// A reference sample's default effective time.
    static final String ALWAYS_EFFECTIVE = "2000-01-01T00:00:00.000Z";

    private final BuildFeeds buildFeeds;
    private final WriteGuard guard;
    private final BuildRecords records;
    private final BuildPlan plan;
    private final FeedUploads feedUploads;
    private final SampleUploads sampleUploads;
    private final SecurityContext securityContext;
    private final Provider<AiConfig> aiConfigProvider;
    private final Provider<UriFactory> uriFactoryProvider;

    @Inject
    UploadSampleTool(final BuildFeeds buildFeeds,
                     final WriteGuard guard,
                     final BuildRecords records,
                     final BuildPlan plan,
                     final FeedUploads feedUploads,
                     final SampleUploads sampleUploads,
                     final SecurityContext securityContext,
                     final Provider<AiConfig> aiConfigProvider,
                     final Provider<UriFactory> uriFactoryProvider) {
        this.buildFeeds = buildFeeds;
        this.guard = guard;
        this.records = records;
        this.plan = plan;
        this.feedUploads = feedUploads;
        this.sampleUploads = sampleUploads;
        this.securityContext = securityContext;
        this.aiConfigProvider = aiConfigProvider;
        this.uriFactoryProvider = uriFactoryProvider;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public boolean isReadOnly() {
        // A stream in the build's feed.
        return false;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.IMPORT_DATA_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("Data is stored as the user, as Stroom's own Upload stores it: they need Import Data and "
                      + "Edit on the feed, and the data must pass Stroom's receipt checks. Files (files=) are "
                      + "posted to /mcp/upload, which needs Use MCP Tools.")
                .build();
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Send sample data to a feed as the real source's data is received (Stroom's receipt "
                             + "checks, then stored as the user, as Stroom's Upload stores it), and return the "
                             + "receipt id and the raw stream it created. Upload each sample file as its own call, "
                             + "so each becomes a stream and every file is stepped. Only upload to feeds in a build "
                             + "(test feeds for updates), never to a production feed whose processor filters would "
                             + "pick the data up. Sample files on the user's disk go with files=: a command per file "
                             + "for the user's terminal sends each from their disk, whole, not through you. sample= "
                             + "is only for text the user pasted into the chat.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(FEED_ARG, "Feed name, e.g. one created with create_feed.")
                        .addStringProperty(SAMPLE_ARG, "The raw data to send, exactly as the source produces it. "
                                                       + "Only for text the user pasted into the chat, exactly as "
                                                       + "pasted. A sample file on the user's disk goes with "
                                                       + "files= instead, whatever its size: your reader may cut "
                                                       + "it (VS Code's read_file cuts a line at 2,000 "
                                                       + "characters), and an agent that passed files' text "
                                                       + "uploaded 2 of each file's 985 records.")
                        .addProperty(FILES_ARG, stringArraySchema("The sample files on the user's disk, every one, "
                                                                  + "whatever their size: their paths as the "
                                                                  + "user's terminal sees them, e.g. "
                                                                  + "'sample-data/fortios/001_1.json'. Nothing is "
                                                                  + "sent now: you get a short-lived curl command "
                                                                  + "per file to run in the user's terminal, which "
                                                                  + "sends the file from their disk to Stroom whole, "
                                                                  + "as them (signed in with their Stroom API key), "
                                                                  + "and prints its stream id."))
                        .addProperty(HEADERS_ARG, ObjectSchema.builder()
                                .description("Extra receipt headers, e.g. {'MyHost': 'ws01'}; readable in XSLT with "
                                             + "stroom:meta().")
                                .build())
                        .addStringProperty(STREAM_TYPE_ARG, "'Raw Events', or 'Raw Reference' for a reference "
                                                            + "feed.")
                        .addStringProperty(EFFECTIVE_TIME_ARG, "Reference data only: from when it applies (ISO 8601 "
                                                               + "UTC). A lookup uses the reference data in effect "
                                                               + "at the event stream's time. Default: "
                                                               + ALWAYS_EFFECTIVE + ", a table that always applied, "
                                                               + "so it covers sample events uploaded before it.")
                        .required(FEED_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            return JsonResults.of(upload(call, context));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        }
    }

    /// How sample files reach the feed: upload_sample's commands, or the user in the Stroom UI.
    ///
    /// Seen (Qwen3 Coder Next, VS Code): a curl to Stroom's datafeed given here for the user, with 'Bearer <their
    /// token or API key>', was run three times from the agent's terminal (PowerShell's curl alias, then
    /// Invoke-WebRequest, then a 401) before it called upload_sample. No such command is given now: the agent has
    /// no token for the datafeed.
    ///
    /// @param links How the caller links to Stroom.
    /// @param feed  The feed.
    /// @return What the agent is told to do next with the user's sample files.
    static String sentByUser(final StroomLinks links, final DocRef feed) {
        return "Next: upload_sample feed=" + feed.getName() + " files=[their paths], and run the command it gives "
               + "for each file in the user's terminal: it sends the file from their disk whole, and prints its "
               + "stream_id. Never send files to Stroom's datafeed yourself (you have no token for it), and don't "
               + "pass their text through you. Only if there is no terminal, the user uploads them in Stroom (the "
               + "feed, " + links.documentTarget(feed) + ", its Data tab, Upload), and stream_meta feeds=['"
               + feed.getName() + "'] gives each stream id. Either way, carry on with stream_ids.";
    }

    private Map<String, Object> upload(final ToolCall call, final ToolCallContext context) {
        final String feed = stringArg(call, FEED_ARG);
        if (feed == null) {
            throw new ToolRefusal("Give feed");
        }
        final String sample = rawArg(call, SAMPLE_ARG);
        final List<String> files = stringListArg(call, FILES_ARG);
        final Map<String, String> headers = headers(call);
        final String streamType = NullSafe.nonBlankStringElse(stringArg(call, STREAM_TYPE_ARG), "Raw Events");
        String effectiveTime = stringArg(call, EFFECTIVE_TIME_ARG);
        if (RAW_REFERENCE.equals(streamType) && effectiveTime == null) {
            // Seen (eval case 15, Haiku): a directory uploaded without one took effect when it arrived, after the
            // event samples, whose lookups then found no reference data.
            effectiveTime = ALWAYS_EFFECTIVE;
        }
        if (!files.isEmpty() && NullSafe.isEmptyString(sample)) {
            final Map<String, String> fileHeaders = new LinkedHashMap<>();
            if (effectiveTime != null) {
                fileHeaders.put(StandardHeaderArguments.EFFECTIVE_TIME, effectiveTime);
            }
            fileHeaders.putAll(headers);
            return commands(context, feed, files, streamType, fileHeaders);
        }
        if (NullSafe.isEmptyString(sample)) {
            throw new ToolRefusal("Give files (the sample files' paths on the user's disk) or sample (only text the "
                                  + "user pasted into the chat)");
        }
        SampleTexts.checkSample(sample, SAMPLE_ARG);
        checkImportData(feed);
        // Before anything waits for them: a path no command can carry safely is refused.
        FeedUploads.checkPaths(files);
        final DocRef match = buildFeeds.buildFeed(context.session(), feed);
        if (buildFeeds.sampleFiles(match)) {
            // Seen: the terminal command failed, and the agent uploaded two records of each file as text instead.
            throw new ToolRefusal("Feed " + feed + "'s samples are files on the user's disk (it was given commands "
                                  + "for them): text isn't taken for it. Call upload_sample with files=[their "
                                  + "paths] for fresh commands, and run each in the user's terminal exactly as "
                                  + "given (copy it whole; don't retype or edit it).");
        }
        final Map<String, String> receipt = new LinkedHashMap<>();
        receipt.put(StandardHeaderArguments.TYPE, streamType);
        if (effectiveTime != null) {
            receipt.put(StandardHeaderArguments.EFFECTIVE_TIME, effectiveTime);
        }
        receipt.putAll(headers);
        final byte[] data = sample.getBytes(StandardCharsets.UTF_8);
        final FeedUploads.Sent sent = feedUploads.send(feed, streamType, receipt, SAMPLE_ARG,
                new ByteArrayInputStream(data), Math.max(sampleUploads.maxBytes(), data.length));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("feed", feed);
        if (sent.streamId() == null) {
            result.put("receipt_id", sent.receiptId());
            result.put("stream_id", null);
            result.put("hint", "Stroom accepted the data but the stream is not visible yet; check stream_meta "
                               + "feeds=['" + feed + "'] shortly.");
            return result;
        }
        result.putAll(sent.toMap());
        result.put("hint", "One stream per sample file: upload the next file, or go on with the plan (next).");
        return plan.withNext(context, plan.buildOf(match).orElse(null), result);
    }

    // upload_sample with files=: the files waiting for this user, and a curl command per file, for their terminal.
    private Map<String, Object> commands(final ToolCallContext context,
                                         final String feed,
                                         final List<String> files,
                                         final String streamType,
                                         final Map<String, String> headers) {
        checkImportData(feed);
        // Before anything waits for them: a path no command can carry safely is refused.
        FeedUploads.checkPaths(files);
        final DocRef match = buildFeeds.buildFeed(context.session(), feed);
        checkUploadsReachable(context, match);
        final String build = guard.buildOf(match).orElse(null);
        final SampleUploads.Pending pending = sampleUploads.issue(securityContext.getUserRef().getUuid(), feed,
                match.getUuid(), streamType, headers, build, files);
        if (build != null) {
            records.update(build, match, entry -> entry.put(BuildFeeds.SAMPLE_FILES, true));
        }
        final String url = uriFactoryProvider.get().publicUri(SampleUploads.PATH + "/" + pending.id()).toString();
        final long minutes = Math.max(1, (pending.expiresMs() - sampleUploads.now()) / 60_000);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("feed", feed);
        result.put("expires_in_minutes", minutes);
        result.put("max_mb", sampleUploads.maxMb());
        result.put("commands", FeedUploads.commands(url, files));
        result.put("hint", "Run each file's command in the user's terminal (powershell on Windows, else bash), from "
                           + "the folder its path is relative to; they approve it. It sends the file as them: "
                           + "with their Stroom API key (or token) in " + FeedUploads.KEY_VARIABLE + ", which they "
                           + "set in that terminal first; never ask for the key in the chat, and never put it in "
                           + "the command. Pass the command to the terminal exactly as given, copied whole: don't "
                           + "retype or edit it. Each prints JSON with the stream_id. The commands last " + minutes
                           + " minute(s), each file once: if they run out, call upload_sample with files= again. "
                           + "If curl.exe reports CRYPT_E_NO_REVOCATION_CHECK (Windows can't reach the "
                           + "certificate's revocation list), add --ssl-no-revoke; never --insecure, which drops the "
                           + "TLS check altogether. Once every file has its stream_id: build_status build="
                           + plan.buildOf(match).orElse("<the build>") + " gives the next step. Don't read or pass "
                           + "the files' text yourself.");
        return result;
    }

    // The commands post to /mcp/upload, which is off with the MCP endpoint and needs Use MCP Tools.
    private void checkUploadsReachable(final ToolCallContext context, final DocRef feed) {
        final boolean enabled = NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getMcpEndpoint,
                McpEndpointConfig::isEnabled, true);
        final String why;
        if (!enabled) {
            why = "is turned off";
        } else if (!securityContext.hasAppPermission(AppPermission.USE_MCP_TOOLS)) {
            why = "needs the '" + AppPermission.USE_MCP_TOOLS.getDisplayValue() + "' permission, which the user "
                  + "doesn't have";
        } else {
            return;
        }
        throw new ToolRefusal("The commands post the files to Stroom's MCP endpoint (" + SampleUploads.PATH + "), "
                              + "which " + why + ": the user uploads the files in Stroom instead (the feed, "
                              + context.links().documentTarget(feed) + ", its Data tab, Upload, each file as its "
                              + "own stream), and stream_meta feeds=['" + feed.getName() + "'] gives each stream "
                              + "id.");
    }

    private void checkImportData(final String feed) {
        if (!securityContext.hasAppPermission(AppPermission.IMPORT_DATA_PERMISSION)) {
            throw new ToolRefusal("Sending data to feed " + feed + " needs the '"
                                  + AppPermission.IMPORT_DATA_PERMISSION.getDisplayValue() + "' permission, which "
                                  + "the user doesn't have: ask an administrator for it.");
        }
    }

    private static Map<String, String> headers(final ToolCall call) {
        final Map<String, String> headers = new LinkedHashMap<>();
        if (argsMap(call).get(HEADERS_ARG) instanceof final Map<?, ?> given) {
            given.forEach((name, value) -> {
                if (name != null && value != null) {
                    headers.put(String.valueOf(name), String.valueOf(value));
                }
            });
        }
        return headers;
    }
}
