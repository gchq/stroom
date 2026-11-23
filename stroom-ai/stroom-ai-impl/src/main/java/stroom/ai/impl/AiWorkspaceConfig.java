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

package stroom.ai.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// Limits on the files in 'Ask Stroom AI' chat workspaces, and on the tools that work on them.
@JsonPropertyOrder(alphabetic = true)
public class AiWorkspaceConfig extends AbstractConfig implements IsStroomConfig {

    static final long DEFAULT_MAX_FILE_BYTES = 20L * 1024 * 1024;
    static final long DEFAULT_MAX_CHAT_BYTES = 100L * 1024 * 1024;
    static final long DEFAULT_MAX_USER_BYTES = 500L * 1024 * 1024;
    static final int DEFAULT_MAX_LINES_PER_READ = 500;
    static final int DEFAULT_MAX_CHARS_PER_READ = 20_000;
    static final StroomDuration DEFAULT_TOOL_TIME_LIMIT = StroomDuration.ofSeconds(30);
    static final StroomDuration DEFAULT_QUERY_TIME_LIMIT = StroomDuration.ofMinutes(2);
    static final int DEFAULT_MAX_QUERY_LENGTH = 10_000;
    static final int DEFAULT_MAX_QUERY_ROWS = 10_000;
    static final int DEFAULT_MAX_EVENTS_PER_FETCH = 500;
    static final int DEFAULT_MAX_EVENT_BYTES = 1024 * 1024;
    static final int DEFAULT_MAX_STREAM_STORE_READS = 500;
    static final long DEFAULT_MAX_TOOL_ARGUMENT_BYTES = 1024 * 1024;
    static final StroomDuration DEFAULT_EQL_MAXSPAN = StroomDuration.ofMinutes(5);
    static final int DEFAULT_JOIN_WORKERS = 4;
    static final int DEFAULT_JOIN_PUSH_DOWN_KEYS = 50_000;
    static final long DEFAULT_JOIN_MAX_KEYS = 1_000_000;
    static final long DEFAULT_JOIN_MAX_ROWS = 10_000_000;
    static final StroomDuration DEFAULT_JOIN_TIME_LIMIT = StroomDuration.ofMinutes(15);
    static final StroomDuration DEFAULT_TIMELINE_IDLE_CAP = StroomDuration.ofMinutes(30);
    static final int DEFAULT_TIMELINE_MAX_EVENTS = 200_000;
    static final int DEFAULT_CHARACTERISE_EXCERPT_CHARS = 500;
    static final int DEFAULT_CHARACTERISE_BATCH_ITEMS = 50;
    static final int DEFAULT_CHARACTERISE_MAX_ITEMS = 1_000;

    private final long maxFileBytes;
    private final long maxChatBytes;
    private final long maxUserBytes;
    private final int maxLinesPerRead;
    private final int maxCharsPerRead;
    private final StroomDuration toolTimeLimit;
    private final StroomDuration queryTimeLimit;
    private final int maxQueryLength;
    private final int maxQueryRows;
    private final int maxEventsPerFetch;
    private final int maxEventBytes;
    private final int maxStreamStoreReads;
    private final long maxToolArgumentBytes;
    private final StroomDuration eqlDefaultMaxspan;
    private final int joinWorkers;
    private final int joinPushDownKeys;
    private final long joinMaxKeys;
    private final long joinMaxRows;
    private final StroomDuration joinTimeLimit;
    private final StroomDuration timelineIdleCap;
    private final int timelineMaxEvents;
    private final int characteriseExcerptChars;
    private final int characteriseBatchItems;
    private final int characteriseMaxItems;

    public AiWorkspaceConfig() {
        maxFileBytes = DEFAULT_MAX_FILE_BYTES;
        maxChatBytes = DEFAULT_MAX_CHAT_BYTES;
        maxUserBytes = DEFAULT_MAX_USER_BYTES;
        maxLinesPerRead = DEFAULT_MAX_LINES_PER_READ;
        maxCharsPerRead = DEFAULT_MAX_CHARS_PER_READ;
        toolTimeLimit = DEFAULT_TOOL_TIME_LIMIT;
        queryTimeLimit = DEFAULT_QUERY_TIME_LIMIT;
        maxQueryLength = DEFAULT_MAX_QUERY_LENGTH;
        maxQueryRows = DEFAULT_MAX_QUERY_ROWS;
        maxEventsPerFetch = DEFAULT_MAX_EVENTS_PER_FETCH;
        maxEventBytes = DEFAULT_MAX_EVENT_BYTES;
        maxStreamStoreReads = DEFAULT_MAX_STREAM_STORE_READS;
        maxToolArgumentBytes = DEFAULT_MAX_TOOL_ARGUMENT_BYTES;
        eqlDefaultMaxspan = DEFAULT_EQL_MAXSPAN;
        joinWorkers = DEFAULT_JOIN_WORKERS;
        joinPushDownKeys = DEFAULT_JOIN_PUSH_DOWN_KEYS;
        joinMaxKeys = DEFAULT_JOIN_MAX_KEYS;
        joinMaxRows = DEFAULT_JOIN_MAX_ROWS;
        joinTimeLimit = DEFAULT_JOIN_TIME_LIMIT;
        timelineIdleCap = DEFAULT_TIMELINE_IDLE_CAP;
        timelineMaxEvents = DEFAULT_TIMELINE_MAX_EVENTS;
        characteriseExcerptChars = DEFAULT_CHARACTERISE_EXCERPT_CHARS;
        characteriseBatchItems = DEFAULT_CHARACTERISE_BATCH_ITEMS;
        characteriseMaxItems = DEFAULT_CHARACTERISE_MAX_ITEMS;
    }

    /// The limits, with the default EQL sequence window.
    ///
    /// @param maxFileBytes         The largest a workspace file may be, or null for the default.
    /// @param maxChatBytes         The most a chat's workspace files may hold, or null for the default.
    /// @param maxUserBytes         The most a user's chat workspaces may hold, or null for the default.
    /// @param maxLinesPerRead      The most lines a tool returns in one call, or null for the default.
    /// @param maxCharsPerRead      The most characters a tool returns in one call, or null for the default.
    /// @param toolTimeLimit        The longest a workspace tool call may run, or null for the default.
    /// @param queryTimeLimit       The longest a query run by the agent may take, or null for the default.
    /// @param maxQueryLength       The longest a query run by the agent may be, or null for the default.
    /// @param maxEventsPerFetch    The most source events fetched in one call, or null for the default.
    /// @param maxEventBytes        The largest source event fetched, or null for the default.
    /// @param maxStreamStoreReads  The most streams read from the stream store in one call, or null for the default.
    /// @param maxQueryRows         The most rows a query run by the agent may return, or null for the default.
    /// @param maxToolArgumentBytes The largest a tool call's arguments may be, or null for the default.
    public AiWorkspaceConfig(final Long maxFileBytes,
                             final Long maxChatBytes,
                             final Long maxUserBytes,
                             final Integer maxLinesPerRead,
                             final Integer maxCharsPerRead,
                             final StroomDuration toolTimeLimit,
                             final StroomDuration queryTimeLimit,
                             final Integer maxQueryLength,
                             final Integer maxEventsPerFetch,
                             final Integer maxEventBytes,
                             final Integer maxStreamStoreReads,
                             final Integer maxQueryRows,
                             final Long maxToolArgumentBytes) {
        this(maxFileBytes, maxChatBytes, maxUserBytes, maxLinesPerRead, maxCharsPerRead, toolTimeLimit,
                queryTimeLimit, maxQueryLength, maxEventsPerFetch, maxEventBytes, maxStreamStoreReads, maxQueryRows,
                maxToolArgumentBytes, null);
    }

    /// The limits, with the default streaming join settings.
    ///
    /// @param maxFileBytes         The largest a workspace file may be, or null for the default.
    /// @param maxChatBytes         The most a chat's workspace files may hold, or null for the default.
    /// @param maxUserBytes         The most a user's chat workspaces may hold, or null for the default.
    /// @param maxLinesPerRead      The most lines a tool returns in one call, or null for the default.
    /// @param maxCharsPerRead      The most characters a tool returns in one call, or null for the default.
    /// @param toolTimeLimit        The longest a workspace tool call may run, or null for the default.
    /// @param queryTimeLimit       The longest a query run by the agent may take, or null for the default.
    /// @param maxQueryLength       The longest a query run by the agent may be, or null for the default.
    /// @param maxEventsPerFetch    The most source events fetched in one call, or null for the default.
    /// @param maxEventBytes        The largest source event fetched, or null for the default.
    /// @param maxStreamStoreReads  The most streams read from the stream store in one call, or null for the default.
    /// @param maxQueryRows         The most rows a query run by the agent may return, or null for the default.
    /// @param maxToolArgumentBytes The largest a tool call's arguments may be, or null for the default.
    /// @param eqlDefaultMaxspan    The window a sequence suggests, or null for the default.
    public AiWorkspaceConfig(final Long maxFileBytes,
                             final Long maxChatBytes,
                             final Long maxUserBytes,
                             final Integer maxLinesPerRead,
                             final Integer maxCharsPerRead,
                             final StroomDuration toolTimeLimit,
                             final StroomDuration queryTimeLimit,
                             final Integer maxQueryLength,
                             final Integer maxEventsPerFetch,
                             final Integer maxEventBytes,
                             final Integer maxStreamStoreReads,
                             final Integer maxQueryRows,
                             final Long maxToolArgumentBytes,
                             final StroomDuration eqlDefaultMaxspan) {
        this(maxFileBytes, maxChatBytes, maxUserBytes, maxLinesPerRead, maxCharsPerRead, toolTimeLimit,
                queryTimeLimit, maxQueryLength, maxEventsPerFetch, maxEventBytes, maxStreamStoreReads, maxQueryRows,
                maxToolArgumentBytes, eqlDefaultMaxspan, null, null, null, null);
    }

    /// @param maxFileBytes         The largest a workspace file may be, or null for the default.
    /// @param maxChatBytes         The most a chat's workspace files may hold, or null for the default.
    /// @param maxUserBytes         The most a user's chat workspaces may hold, or null for the default.
    /// @param maxLinesPerRead      The most lines a tool returns in one call, or null for the default.
    /// @param maxCharsPerRead      The most characters a tool returns in one call, or null for the default.
    /// @param toolTimeLimit        The longest a workspace tool call may run, or null for the default.
    /// @param queryTimeLimit       The longest a query run by the agent may take, or null for the default.
    /// @param maxQueryLength       The longest a query run by the agent may be, or null for the default.
    /// @param maxEventsPerFetch    The most source events fetched in one call, or null for the default.
    /// @param maxEventBytes        The largest source event fetched, or null for the default.
    /// @param maxStreamStoreReads  The most streams read from the stream store in one call, or null for the default.
    /// @param maxQueryRows         The most rows a query run by the agent may return, or null for the default.
    /// @param maxToolArgumentBytes The largest a tool call's arguments may be, or null for the default.
    /// @param eqlDefaultMaxspan    The window a sequence suggests, or null for the default.
    /// @param joinWorkers          The workers a streaming join uses, or null for the default.
    /// @param joinPushDownKeys     The most keys a side pushed down as a filter may have, or null for the default.
    /// @param joinMaxKeys          The most keys a streaming join merges, or null for the default.
    /// @param joinMaxRows          The most rows a streaming join reads, or null for the default.
    public AiWorkspaceConfig(final Long maxFileBytes,
                             final Long maxChatBytes,
                             final Long maxUserBytes,
                             final Integer maxLinesPerRead,
                             final Integer maxCharsPerRead,
                             final StroomDuration toolTimeLimit,
                             final StroomDuration queryTimeLimit,
                             final Integer maxQueryLength,
                             final Integer maxEventsPerFetch,
                             final Integer maxEventBytes,
                             final Integer maxStreamStoreReads,
                             final Integer maxQueryRows,
                             final Long maxToolArgumentBytes,
                             final StroomDuration eqlDefaultMaxspan,
                             final Integer joinWorkers,
                             final Integer joinPushDownKeys,
                             final Long joinMaxKeys,
                             final Long joinMaxRows) {
        this(maxFileBytes, maxChatBytes, maxUserBytes, maxLinesPerRead, maxCharsPerRead, toolTimeLimit,
                queryTimeLimit, maxQueryLength, maxEventsPerFetch, maxEventBytes, maxStreamStoreReads, maxQueryRows,
                maxToolArgumentBytes, eqlDefaultMaxspan, joinWorkers, joinPushDownKeys, joinMaxKeys, joinMaxRows,
                null);
    }

    /// @param maxFileBytes         The largest a workspace file may be, or null for the default.
    /// @param maxChatBytes         The most a chat's workspace files may hold, or null for the default.
    /// @param maxUserBytes         The most a user's chat workspaces may hold, or null for the default.
    /// @param maxLinesPerRead      The most lines a tool returns in one call, or null for the default.
    /// @param maxCharsPerRead      The most characters a tool returns in one call, or null for the default.
    /// @param toolTimeLimit        The longest a workspace tool call may run, or null for the default.
    /// @param queryTimeLimit       The longest a query run by the agent may take, or null for the default.
    /// @param maxQueryLength       The longest a query run by the agent may be, or null for the default.
    /// @param maxEventsPerFetch    The most source events fetched in one call, or null for the default.
    /// @param maxEventBytes        The largest source event fetched, or null for the default.
    /// @param maxStreamStoreReads  The most streams read from the stream store in one call, or null for the default.
    /// @param maxQueryRows         The most rows a query run by the agent may return, or null for the default.
    /// @param maxToolArgumentBytes The largest a tool call's arguments may be, or null for the default.
    /// @param eqlDefaultMaxspan    The window a sequence suggests, or null for the default.
    /// @param joinWorkers          The workers a streaming join uses, or null for the default.
    /// @param joinPushDownKeys     The most keys a side pushed down as a filter may have, or null for the default.
    /// @param joinMaxKeys          The most keys a streaming join merges, or null for the default.
    /// @param joinMaxRows          The most rows a streaming join reads, or null for the default.
    /// @param joinTimeLimit        The longest a streaming join may run when the user agrees, or null for the
    ///                             default.
    public AiWorkspaceConfig(final Long maxFileBytes,
                             final Long maxChatBytes,
                             final Long maxUserBytes,
                             final Integer maxLinesPerRead,
                             final Integer maxCharsPerRead,
                             final StroomDuration toolTimeLimit,
                             final StroomDuration queryTimeLimit,
                             final Integer maxQueryLength,
                             final Integer maxEventsPerFetch,
                             final Integer maxEventBytes,
                             final Integer maxStreamStoreReads,
                             final Integer maxQueryRows,
                             final Long maxToolArgumentBytes,
                             final StroomDuration eqlDefaultMaxspan,
                             final Integer joinWorkers,
                             final Integer joinPushDownKeys,
                             final Long joinMaxKeys,
                             final Long joinMaxRows,
                             final StroomDuration joinTimeLimit) {
        this(maxFileBytes, maxChatBytes, maxUserBytes, maxLinesPerRead, maxCharsPerRead, toolTimeLimit,
                queryTimeLimit, maxQueryLength, maxEventsPerFetch, maxEventBytes, maxStreamStoreReads, maxQueryRows,
                maxToolArgumentBytes, eqlDefaultMaxspan, joinWorkers, joinPushDownKeys, joinMaxKeys, joinMaxRows,
                joinTimeLimit, null, null, null, null, null);
    }

    /// @param maxFileBytes             The largest a workspace file may be, or null for the default.
    /// @param maxChatBytes             The most a chat's workspace files may hold, or null for the default.
    /// @param maxUserBytes             The most a user's chat workspaces may hold, or null for the default.
    /// @param maxLinesPerRead          The most lines a tool returns in one call, or null for the default.
    /// @param maxCharsPerRead          The most characters a tool returns in one call, or null for the default.
    /// @param toolTimeLimit            The longest a workspace tool call may run, or null for the default.
    /// @param queryTimeLimit           The longest a query run by the agent may take, or null for the default.
    /// @param maxQueryLength           The longest a query run by the agent may be, or null for the default.
    /// @param maxEventsPerFetch        The most source events fetched in one call, or null for the default.
    /// @param maxEventBytes            The largest source event fetched, or null for the default.
    /// @param maxStreamStoreReads      The most streams read from the stream store in one call, or null for the
    ///                                 default.
    /// @param maxQueryRows             The most rows a query run by the agent may return, or null for the default.
    /// @param maxToolArgumentBytes     The largest a tool call's arguments may be, or null for the default.
    /// @param eqlDefaultMaxspan        The window a sequence suggests, or null for the default.
    /// @param joinWorkers              The workers a streaming join uses, or null for the default.
    /// @param joinPushDownKeys         The most keys a side pushed down as a filter may have, or null for the
    ///                                 default.
    /// @param joinMaxKeys              The most keys a streaming join merges, or null for the default.
    /// @param joinMaxRows              The most rows a streaming join reads, or null for the default.
    /// @param joinTimeLimit            The longest a streaming join may run when the user agrees, or null for the
    ///                                 default.
    /// @param timelineIdleCap          How long with no event ends an activity timeline's interval, or null for the
    ///                                 default.
    /// @param timelineMaxEvents        The most events an activity timeline reads, or null for the default.
    /// @param characteriseExcerptChars The most characters of an item's text a characterisation sends to the
    ///                                 model, or null for the default.
    /// @param characteriseBatchItems   The items a characterisation sends the model in one call, or null for the
    ///                                 default.
    /// @param characteriseMaxItems     The most items a characterisation sends the model in one run without the
    ///                                 user agreeing, or null for the default.
    @JsonCreator
    public AiWorkspaceConfig(@JsonProperty("maxFileBytes") final Long maxFileBytes,
                             @JsonProperty("maxChatBytes") final Long maxChatBytes,
                             @JsonProperty("maxUserBytes") final Long maxUserBytes,
                             @JsonProperty("maxLinesPerRead") final Integer maxLinesPerRead,
                             @JsonProperty("maxCharsPerRead") final Integer maxCharsPerRead,
                             @JsonProperty("toolTimeLimit") final StroomDuration toolTimeLimit,
                             @JsonProperty("queryTimeLimit") final StroomDuration queryTimeLimit,
                             @JsonProperty("maxQueryLength") final Integer maxQueryLength,
                             @JsonProperty("maxEventsPerFetch") final Integer maxEventsPerFetch,
                             @JsonProperty("maxEventBytes") final Integer maxEventBytes,
                             @JsonProperty("maxStreamStoreReads") final Integer maxStreamStoreReads,
                             @JsonProperty("maxQueryRows") final Integer maxQueryRows,
                             @JsonProperty("maxToolArgumentBytes") final Long maxToolArgumentBytes,
                             @JsonProperty("eqlDefaultMaxspan") final StroomDuration eqlDefaultMaxspan,
                             @JsonProperty("joinWorkers") final Integer joinWorkers,
                             @JsonProperty("joinPushDownKeys") final Integer joinPushDownKeys,
                             @JsonProperty("joinMaxKeys") final Long joinMaxKeys,
                             @JsonProperty("joinMaxRows") final Long joinMaxRows,
                             @JsonProperty("joinTimeLimit") final StroomDuration joinTimeLimit,
                             @JsonProperty("timelineIdleCap") final StroomDuration timelineIdleCap,
                             @JsonProperty("timelineMaxEvents") final Integer timelineMaxEvents,
                             @JsonProperty("characteriseExcerptChars") final Integer characteriseExcerptChars,
                             @JsonProperty("characteriseBatchItems") final Integer characteriseBatchItems,
                             @JsonProperty("characteriseMaxItems") final Integer characteriseMaxItems) {
        this.maxFileBytes = Objects.requireNonNullElse(maxFileBytes, DEFAULT_MAX_FILE_BYTES);
        this.maxChatBytes = Objects.requireNonNullElse(maxChatBytes, DEFAULT_MAX_CHAT_BYTES);
        this.maxUserBytes = Objects.requireNonNullElse(maxUserBytes, DEFAULT_MAX_USER_BYTES);
        this.maxLinesPerRead = Objects.requireNonNullElse(maxLinesPerRead, DEFAULT_MAX_LINES_PER_READ);
        this.maxCharsPerRead = Objects.requireNonNullElse(maxCharsPerRead, DEFAULT_MAX_CHARS_PER_READ);
        this.toolTimeLimit = Objects.requireNonNullElse(toolTimeLimit, DEFAULT_TOOL_TIME_LIMIT);
        this.queryTimeLimit = Objects.requireNonNullElse(queryTimeLimit, DEFAULT_QUERY_TIME_LIMIT);
        this.maxQueryLength = Objects.requireNonNullElse(maxQueryLength, DEFAULT_MAX_QUERY_LENGTH);
        this.maxEventsPerFetch = Objects.requireNonNullElse(maxEventsPerFetch, DEFAULT_MAX_EVENTS_PER_FETCH);
        this.maxEventBytes = Objects.requireNonNullElse(maxEventBytes, DEFAULT_MAX_EVENT_BYTES);
        this.maxStreamStoreReads = Objects.requireNonNullElse(maxStreamStoreReads, DEFAULT_MAX_STREAM_STORE_READS);
        this.maxQueryRows = Objects.requireNonNullElse(maxQueryRows, DEFAULT_MAX_QUERY_ROWS);
        this.maxToolArgumentBytes = Objects.requireNonNullElse(maxToolArgumentBytes,
                DEFAULT_MAX_TOOL_ARGUMENT_BYTES);
        this.eqlDefaultMaxspan = Objects.requireNonNullElse(eqlDefaultMaxspan, DEFAULT_EQL_MAXSPAN);
        this.joinWorkers = Objects.requireNonNullElse(joinWorkers, DEFAULT_JOIN_WORKERS);
        this.joinPushDownKeys = Objects.requireNonNullElse(joinPushDownKeys, DEFAULT_JOIN_PUSH_DOWN_KEYS);
        this.joinMaxKeys = Objects.requireNonNullElse(joinMaxKeys, DEFAULT_JOIN_MAX_KEYS);
        this.joinMaxRows = Objects.requireNonNullElse(joinMaxRows, DEFAULT_JOIN_MAX_ROWS);
        this.joinTimeLimit = Objects.requireNonNullElse(joinTimeLimit, DEFAULT_JOIN_TIME_LIMIT);
        this.timelineIdleCap = Objects.requireNonNullElse(timelineIdleCap, DEFAULT_TIMELINE_IDLE_CAP);
        this.timelineMaxEvents = Objects.requireNonNullElse(timelineMaxEvents, DEFAULT_TIMELINE_MAX_EVENTS);
        this.characteriseExcerptChars = Objects.requireNonNullElse(characteriseExcerptChars,
                DEFAULT_CHARACTERISE_EXCERPT_CHARS);
        this.characteriseBatchItems = Objects.requireNonNullElse(characteriseBatchItems,
                DEFAULT_CHARACTERISE_BATCH_ITEMS);
        this.characteriseMaxItems = Objects.requireNonNullElse(characteriseMaxItems, DEFAULT_CHARACTERISE_MAX_ITEMS);
    }

    @JsonPropertyDescription("The largest a single workspace file may be, in bytes.")
    @JsonProperty("maxFileBytes")
    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    @JsonPropertyDescription("The most a chat's workspace files may hold in all, in bytes.")
    @JsonProperty("maxChatBytes")
    public long getMaxChatBytes() {
        return maxChatBytes;
    }

    @JsonPropertyDescription("The most all of a user's chat workspaces may hold in all, in bytes.")
    @JsonProperty("maxUserBytes")
    public long getMaxUserBytes() {
        return maxUserBytes;
    }

    @JsonPropertyDescription("The most lines of a file a tool returns to the model in one call.")
    @JsonProperty("maxLinesPerRead")
    public int getMaxLinesPerRead() {
        return maxLinesPerRead;
    }

    @JsonPropertyDescription("The most characters a tool returns to the model in one call.")
    @JsonProperty("maxCharsPerRead")
    public int getMaxCharsPerRead() {
        return maxCharsPerRead;
    }

    @JsonPropertyDescription("The longest a single workspace tool call (e.g. a search, query or table step) may " +
                             "run before it is stopped.")
    @JsonProperty("toolTimeLimit")
    public StroomDuration getToolTimeLimit() {
        return toolTimeLimit;
    }

    @JsonPropertyDescription("The longest a StroomQL query run by the agent (run_stroomql) may take, including " +
                             "writing its results to the workspace.")
    @JsonProperty("queryTimeLimit")
    public StroomDuration getQueryTimeLimit() {
        return queryTimeLimit;
    }

    @JsonPropertyDescription("The longest a StroomQL query run by the agent (run_stroomql) may be, in characters.")
    @JsonProperty("maxQueryLength")
    public int getMaxQueryLength() {
        return maxQueryLength;
    }

    @JsonPropertyDescription("The most rows a StroomQL query run by the agent (run_stroomql) may return. A query "
                             + "with no limit is given this one, and a larger limit is lowered to it, so the "
                             + "agent's searches are never unbounded.")
    @JsonProperty("maxQueryRows")
    public int getMaxQueryRows() {
        return maxQueryRows;
    }

    @JsonPropertyDescription("The most source events the agent may fetch in one call of fetch_events.")
    @JsonProperty("maxEventsPerFetch")
    public int getMaxEventsPerFetch() {
        return maxEventsPerFetch;
    }

    @JsonPropertyDescription("The largest a single source event fetched by the agent (fetch_events) may be, in "
                             + "bytes. Larger events are left out.")
    @JsonProperty("maxEventBytes")
    public int getMaxEventBytes() {
        return maxEventBytes;
    }

    @JsonPropertyDescription("The most streams the agent's stream_meta tool may read attributes for from the "
                             + "stream store in one call, for values the database no longer holds (or never "
                             + "held), such as the headers each part was received with.")
    @JsonProperty("maxStreamStoreReads")
    public int getMaxStreamStoreReads() {
        return maxStreamStoreReads;
    }

    @JsonPropertyDescription("The largest a tool call's arguments (Stroom's own tools or an MCP server's) may "
                             + "be, in bytes, once the content of the workspace files they name (with "
                             + "{\"$workspace\": \"<path>\"}) has been put in.")
    @JsonProperty("maxToolArgumentBytes")
    public long getMaxToolArgumentBytes() {
        return maxToolArgumentBytes;
    }

    @JsonPropertyDescription("The window an EQL sequence run by the agent (sequence_query) suggests when the user "
                             + "hasn't said how far apart its steps may be: the default answer of the question "
                             + "the user is asked, and the suggestion given over MCP. At most 24 hours.")
    @JsonProperty("eqlDefaultMaxspan")
    public StroomDuration getEqlDefaultMaxspan() {
        return eqlDefaultMaxspan;
    }

    @JsonPropertyDescription("The workers a streaming join run by the agent (sequence_query's stream engine) uses, "
                             + "each reading its own range of the join key's values from Elasticsearch: more "
                             + "finish sooner, and put as many times the search load on the cluster. 1 reads "
                             + "the join in one stream.")
    @JsonProperty("joinWorkers")
    public int getJoinWorkers() {
        return joinWorkers;
    }

    @JsonPropertyDescription("The most join keys a step of a streaming join may have for it to be read first and "
                             + "its keys pushed down as a terms filter on the other steps (e.g. a team's users), "
                             + "so the larger sides are cut before they stream. Batched under Elasticsearch's "
                             + "terms limit (65,536 by default).")
    @JsonProperty("joinPushDownKeys")
    public int getJoinPushDownKeys() {
        return joinPushDownKeys;
    }

    @JsonPropertyDescription("The most join keys a streaming join run by the agent may merge before it stops, "
                             + "keeping what it has found. A join estimated near it is asked about before it runs.")
    @JsonProperty("joinMaxKeys")
    public long getJoinMaxKeys() {
        return joinMaxKeys;
    }

    @JsonPropertyDescription("The most rows (events, and keys read as keys) a streaming join run by the agent may "
                             + "read from Elasticsearch before it stops, keeping what it has found. A join "
                             + "estimated near it is asked about before it runs.")
    @JsonProperty("joinMaxRows")
    public long getJoinMaxRows() {
        return joinMaxRows;
    }

    @JsonPropertyDescription("The longest a streaming join run by the agent (sequence_query's stream engine) may "
                             + "run when its estimate says it won't fit the query time limit and the user agrees "
                             + "(a form in the chat, runAnyway over MCP). Otherwise a join runs within the query "
                             + "time limit. Its workers share it; one past it stops, keeping what it has found.")
    @JsonProperty("joinTimeLimit")
    public StroomDuration getJoinTimeLimit() {
        return joinTimeLimit;
    }

    @JsonPropertyDescription("How long with no event from the user on a host ends an interval of an activity "
                             + "timeline (activity_timeline), marked uncertain, where the sources hold no lock, "
                             + "sleep or logoff event to end it. A call or a skill may give another.")
    @JsonProperty("timelineIdleCap")
    public StroomDuration getTimelineIdleCap() {
        return timelineIdleCap;
    }

    @JsonPropertyDescription("The most events an activity timeline (activity_timeline) reads from its sources for "
                             + "one user and period before it stops, keeping what it has read.")
    @JsonProperty("timelineMaxEvents")
    public int getTimelineMaxEvents() {
        return timelineMaxEvents;
    }

    @JsonPropertyDescription("The most characters of an item's text (a message, an AI chat prompt) an activity "
                             + "timeline's characterisation sends to the model, from its start.")
    @JsonProperty("characteriseExcerptChars")
    public int getCharacteriseExcerptChars() {
        return characteriseExcerptChars;
    }

    @JsonPropertyDescription("The items an activity timeline's characterisation sends to the model in one call, "
                             + "outside the chat's conversation.")
    @JsonProperty("characteriseBatchItems")
    public int getCharacteriseBatchItems() {
        return characteriseBatchItems;
    }

    @JsonPropertyDescription("The most items an activity timeline's characterisation sends to the model in one run "
                             + "before the user is asked (runAnyway over MCP); past it, the rest are left "
                             + "unclassified.")
    @JsonProperty("characteriseMaxItems")
    public int getCharacteriseMaxItems() {
        return characteriseMaxItems;
    }

    @Override
    public String toString() {
        return "AiWorkspaceConfig{" +
               "maxFileBytes=" + maxFileBytes +
               ", maxChatBytes=" + maxChatBytes +
               ", maxUserBytes=" + maxUserBytes +
               ", maxLinesPerRead=" + maxLinesPerRead +
               ", maxCharsPerRead=" + maxCharsPerRead +
               ", toolTimeLimit=" + toolTimeLimit +
               ", queryTimeLimit=" + queryTimeLimit +
               ", maxQueryLength=" + maxQueryLength +
               ", maxQueryRows=" + maxQueryRows +
               ", maxEventsPerFetch=" + maxEventsPerFetch +
               ", maxEventBytes=" + maxEventBytes +
               ", maxStreamStoreReads=" + maxStreamStoreReads +
               ", maxToolArgumentBytes=" + maxToolArgumentBytes +
               ", eqlDefaultMaxspan=" + eqlDefaultMaxspan +
               ", joinWorkers=" + joinWorkers +
               ", joinPushDownKeys=" + joinPushDownKeys +
               ", joinMaxKeys=" + joinMaxKeys +
               ", joinMaxRows=" + joinMaxRows +
               ", joinTimeLimit=" + joinTimeLimit +
               ", timelineIdleCap=" + timelineIdleCap +
               ", timelineMaxEvents=" + timelineMaxEvents +
               ", characteriseExcerptChars=" + characteriseExcerptChars +
               ", characteriseBatchItems=" + characteriseBatchItems +
               ", characteriseMaxItems=" + characteriseMaxItems +
               '}';
    }
}
