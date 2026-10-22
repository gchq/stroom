/*
 * Copyright 2018 Crown Copyright
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

import stroom.ai.impl.db.AiDbConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class AiConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    static final StroomDuration DEFAULT_MAX_RUN_DURATION = StroomDuration.ofHours(2);
    static final StroomDuration DEFAULT_UNATTENDED_AFTER = StroomDuration.ofMinutes(10);
    static final int DEFAULT_MAX_RUNS_PER_USER = 3;
    static final int DEFAULT_MAX_RUNS_PER_NODE = 20;
    static final StroomDuration DEFAULT_WORKSPACE_FILE_RETENTION = StroomDuration.ofDays(7);
    static final StroomDuration DEFAULT_CHAT_RETENTION = StroomDuration.ofDays(90);
    static final int DEFAULT_TOOL_GROUP_THRESHOLD = 24;
    static final StroomDuration DEFAULT_ELICITATION_TIMEOUT = StroomDuration.ofMinutes(10);
    static final int DEFAULT_MAX_ELICITATIONS_PER_NODE = 5;
    static final int DEFAULT_MAX_CHAT_TITLE_WORDS = 12;
    static final int DEFAULT_MAX_TOOL_ITERATIONS = 100;
    static final long DEFAULT_MAX_TAB_SNAPSHOT_BYTES = 2L * 1024 * 1024;

    private final AiDbConfig dbConfig;
    private final CacheConfig chatResponseCache;
    private final StroomDuration maxRunDuration;
    private final StroomDuration unattendedAfter;
    private final int maxRunsPerUser;
    private final int maxRunsPerNode;
    private final AiCompactionConfig compaction;
    private final StroomDuration workspaceFileRetention;
    private final StroomDuration chatRetention;
    private final AiWorkspaceConfig workspace;
    private final boolean allowApproveEverything;
    private final int toolGroupThreshold;
    private final StroomDuration elicitationTimeout;
    private final int maxElicitationsPerNode;
    private final boolean askReadOnlyToolsAfterUntrustedContent;
    private final int maxChatTitleWords;
    private final boolean suggestFollowUps;
    private final int maxToolIterations;
    private final boolean shareActiveTab;
    private final long maxTabSnapshotBytes;
    private final McpEndpointConfig mcpEndpoint;
    private final ErrorTriageConfig errorTriage;
    private final AiPipelineStagesConfig pipelineStages;

    public AiConfig() {
        dbConfig = new AiDbConfig();
        chatResponseCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();
        maxRunDuration = DEFAULT_MAX_RUN_DURATION;
        unattendedAfter = DEFAULT_UNATTENDED_AFTER;
        maxRunsPerUser = DEFAULT_MAX_RUNS_PER_USER;
        maxRunsPerNode = DEFAULT_MAX_RUNS_PER_NODE;
        compaction = new AiCompactionConfig();
        workspaceFileRetention = DEFAULT_WORKSPACE_FILE_RETENTION;
        chatRetention = DEFAULT_CHAT_RETENTION;
        workspace = new AiWorkspaceConfig();
        allowApproveEverything = false;
        toolGroupThreshold = DEFAULT_TOOL_GROUP_THRESHOLD;
        elicitationTimeout = DEFAULT_ELICITATION_TIMEOUT;
        maxElicitationsPerNode = DEFAULT_MAX_ELICITATIONS_PER_NODE;
        askReadOnlyToolsAfterUntrustedContent = false;
        maxChatTitleWords = DEFAULT_MAX_CHAT_TITLE_WORDS;
        suggestFollowUps = true;
        maxToolIterations = DEFAULT_MAX_TOOL_ITERATIONS;
        shareActiveTab = true;
        maxTabSnapshotBytes = DEFAULT_MAX_TAB_SNAPSHOT_BYTES;
        mcpEndpoint = new McpEndpointConfig();
        errorTriage = new ErrorTriageConfig();
        pipelineStages = new AiPipelineStagesConfig();
    }

    /// As the constructor that reads the config, with the attached tab shared and the default tab snapshot limit.
    public AiConfig(final AiDbConfig dbConfig,
                    final CacheConfig chatResponseCache,
                    final StroomDuration maxRunDuration,
                    final StroomDuration unattendedAfter,
                    final Integer maxRunsPerUser,
                    final Integer maxRunsPerNode,
                    final AiCompactionConfig compaction,
                    final StroomDuration workspaceFileRetention,
                    final StroomDuration chatRetention,
                    final AiWorkspaceConfig workspace,
                    final Boolean allowApproveEverything,
                    final Integer toolGroupThreshold,
                    final StroomDuration elicitationTimeout,
                    final Integer maxElicitationsPerNode,
                    final Boolean askReadOnlyToolsAfterUntrustedContent,
                    final Integer maxChatTitleWords,
                    final Boolean suggestFollowUps,
                    final Integer maxToolIterations) {
        this(dbConfig, chatResponseCache, maxRunDuration, unattendedAfter, maxRunsPerUser, maxRunsPerNode,
                compaction, workspaceFileRetention, chatRetention, workspace, allowApproveEverything,
                toolGroupThreshold, elicitationTimeout, maxElicitationsPerNode,
                askReadOnlyToolsAfterUntrustedContent, maxChatTitleWords, suggestFollowUps, maxToolIterations, null,
                null, null, null, null);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public AiConfig(@JsonProperty("db") final AiDbConfig dbConfig,
                    @JsonProperty("chatResponseCache") final CacheConfig chatResponseCache,
                    @JsonProperty("maxRunDuration") final StroomDuration maxRunDuration,
                    @JsonProperty("unattendedAfter") final StroomDuration unattendedAfter,
                    @JsonProperty("maxRunsPerUser") final Integer maxRunsPerUser,
                    @JsonProperty("maxRunsPerNode") final Integer maxRunsPerNode,
                    @JsonProperty("compaction") final AiCompactionConfig compaction,
                    @JsonProperty("workspaceFileRetention") final StroomDuration workspaceFileRetention,
                    @JsonProperty("chatRetention") final StroomDuration chatRetention,
                    @JsonProperty("workspace") final AiWorkspaceConfig workspace,
                    @JsonProperty("allowApproveEverything") final Boolean allowApproveEverything,
                    @JsonProperty("toolGroupThreshold") final Integer toolGroupThreshold,
                    @JsonProperty("elicitationTimeout") final StroomDuration elicitationTimeout,
                    @JsonProperty("maxElicitationsPerNode") final Integer maxElicitationsPerNode,
                    @JsonProperty("askReadOnlyToolsAfterUntrustedContent")
                    final Boolean askReadOnlyToolsAfterUntrustedContent,
                    @JsonProperty("maxChatTitleWords") final Integer maxChatTitleWords,
                    @JsonProperty("suggestFollowUps") final Boolean suggestFollowUps,
                    @JsonProperty("maxToolIterations") final Integer maxToolIterations,
                    @JsonProperty("shareActiveTab") final Boolean shareActiveTab,
                    @JsonProperty("maxTabSnapshotBytes") final Long maxTabSnapshotBytes,
                    @JsonProperty("mcpEndpoint") final McpEndpointConfig mcpEndpoint,
                    @JsonProperty("errorTriage") final ErrorTriageConfig errorTriage,
                    @JsonProperty("pipelineStages") final AiPipelineStagesConfig pipelineStages) {
        this.dbConfig = dbConfig;
        this.chatResponseCache = chatResponseCache;
        this.maxRunDuration = Objects.requireNonNullElse(maxRunDuration, DEFAULT_MAX_RUN_DURATION);
        this.unattendedAfter = Objects.requireNonNullElse(unattendedAfter, DEFAULT_UNATTENDED_AFTER);
        this.maxRunsPerUser = Objects.requireNonNullElse(maxRunsPerUser, DEFAULT_MAX_RUNS_PER_USER);
        this.maxRunsPerNode = Objects.requireNonNullElse(maxRunsPerNode, DEFAULT_MAX_RUNS_PER_NODE);
        this.compaction = Objects.requireNonNullElseGet(compaction, AiCompactionConfig::new);
        this.workspaceFileRetention = Objects.requireNonNullElse(workspaceFileRetention,
                DEFAULT_WORKSPACE_FILE_RETENTION);
        this.chatRetention = Objects.requireNonNullElse(chatRetention, DEFAULT_CHAT_RETENTION);
        this.workspace = Objects.requireNonNullElseGet(workspace, AiWorkspaceConfig::new);
        this.allowApproveEverything = Objects.requireNonNullElse(allowApproveEverything, false);
        this.toolGroupThreshold = Objects.requireNonNullElse(toolGroupThreshold, DEFAULT_TOOL_GROUP_THRESHOLD);
        this.elicitationTimeout = Objects.requireNonNullElse(elicitationTimeout, DEFAULT_ELICITATION_TIMEOUT);
        this.maxElicitationsPerNode = Objects.requireNonNullElse(maxElicitationsPerNode,
                DEFAULT_MAX_ELICITATIONS_PER_NODE);
        this.askReadOnlyToolsAfterUntrustedContent = Objects.requireNonNullElse(
                askReadOnlyToolsAfterUntrustedContent, false);
        this.maxChatTitleWords = Math.max(1, Objects.requireNonNullElse(maxChatTitleWords,
                DEFAULT_MAX_CHAT_TITLE_WORDS));
        this.suggestFollowUps = Objects.requireNonNullElse(suggestFollowUps, true);
        this.maxToolIterations = Math.max(1, Objects.requireNonNullElse(maxToolIterations,
                DEFAULT_MAX_TOOL_ITERATIONS));
        this.shareActiveTab = Objects.requireNonNullElse(shareActiveTab, true);
        this.maxTabSnapshotBytes = Math.max(0, Objects.requireNonNullElse(maxTabSnapshotBytes,
                DEFAULT_MAX_TAB_SNAPSHOT_BYTES));
        this.mcpEndpoint = Objects.requireNonNullElseGet(mcpEndpoint, McpEndpointConfig::new);
        this.errorTriage = Objects.requireNonNullElseGet(errorTriage, ErrorTriageConfig::new);
        this.pipelineStages = Objects.requireNonNullElseGet(pipelineStages, AiPipelineStagesConfig::new);
    }

    /// As the constructor that reads the config, with the default error triage and pipeline stages.
    public AiConfig(final AiDbConfig dbConfig,
                    final CacheConfig chatResponseCache,
                    final StroomDuration maxRunDuration,
                    final StroomDuration unattendedAfter,
                    final Integer maxRunsPerUser,
                    final Integer maxRunsPerNode,
                    final AiCompactionConfig compaction,
                    final StroomDuration workspaceFileRetention,
                    final StroomDuration chatRetention,
                    final AiWorkspaceConfig workspace,
                    final Boolean allowApproveEverything,
                    final Integer toolGroupThreshold,
                    final StroomDuration elicitationTimeout,
                    final Integer maxElicitationsPerNode,
                    final Boolean askReadOnlyToolsAfterUntrustedContent,
                    final Integer maxChatTitleWords,
                    final Boolean suggestFollowUps,
                    final Integer maxToolIterations,
                    final Boolean shareActiveTab,
                    final Long maxTabSnapshotBytes,
                    final McpEndpointConfig mcpEndpoint) {
        this(dbConfig, chatResponseCache, maxRunDuration, unattendedAfter, maxRunsPerUser, maxRunsPerNode,
                compaction, workspaceFileRetention, chatRetention, workspace, allowApproveEverything,
                toolGroupThreshold, elicitationTimeout, maxElicitationsPerNode,
                askReadOnlyToolsAfterUntrustedContent, maxChatTitleWords, suggestFollowUps, maxToolIterations,
                shareActiveTab, maxTabSnapshotBytes, mcpEndpoint, null, null);
    }

    /// @return What marks a pipeline as each stage (translation, indexing, reference and so on), and where the
    /// templates for each stage's new pipelines are looked for.
    @JsonProperty("pipelineStages")
    public AiPipelineStagesConfig getPipelineStages() {
        return pipelineStages;
    }

    /// @return How the tools triage pipeline errors: the environment's own rules.
    @JsonProperty("errorTriage")
    public ErrorTriageConfig getErrorTriage() {
        return errorTriage;
    }

    /// @return Stroom's own MCP endpoint, through which MCP clients use Stroom's tools.
    @JsonProperty("mcpEndpoint")
    public McpEndpointConfig getMcpEndpoint() {
        return mcpEndpoint;
    }

    /// @return True if what the user sees in their active document tab (unsaved changes included) is shared with
    /// the questions they ask in a chat.
    @JsonPropertyDescription("If true, users can attach a document tab to an 'Ask Stroom AI' chat (the + chip by "
                             + "the input), and what they see in it (unsaved changes included) is shared with each "
                             + "question until they remove it or close the tab. Only documents the user can view "
                             + "are shared.")
    @JsonProperty("shareActiveTab")
    public boolean isShareActiveTab() {
        return shareActiveTab;
    }

    /// @return The most bytes of a tab's content kept for one question; parts beyond it are cut short.
    @JsonPropertyDescription("The most bytes of an attached tab's content (its text parts, as edited) kept for one "
                             + "question in an 'Ask Stroom AI' chat. Parts beyond the limit are cut short, and the "
                             + "agent is told so.")
    @JsonProperty("maxTabSnapshotBytes")
    public long getMaxTabSnapshotBytes() {
        return maxTabSnapshotBytes;
    }

    @JsonPropertyDescription("The most calls to the model the agent may make for one question while it uses "
                             + "tools. On the last it is offered no tools, so it sums up what it has done; the user "
                             + "can reply 'continue' for it to carry on. Stops a model looping for ever.")
    @JsonProperty("maxToolIterations")
    public int getMaxToolIterations() {
        return maxToolIterations;
    }

    @JsonPropertyDescription("If true, after each answer the chat's model is asked for up to three things the user "
                             + "might ask next, shown as links above the input (one small extra call to the model "
                             + "per answer; not made when the agent asked the user for something during the turn), "
                             + "and, when the user attaches something to a chat that has a conversation or several "
                             + "things attached, for questions about what is attached.")
    @JsonProperty("suggestFollowUps")
    public boolean isSuggestFollowUps() {
        return suggestFollowUps;
    }

    @JsonPropertyDescription("The most words in the title the chat's model is asked for, to name a chat after what "
                             + "it is about. Longer titles are cut short with an ellipsis where they are shown.")
    @JsonProperty("maxChatTitleWords")
    public int getMaxChatTitleWords() {
        return maxChatTitleWords;
    }

    @JsonPropertyDescription("If true, once a chat's run has read data from tools or attached tables (which could "
                             + "hold instructions planted by someone else), even read-only tools on trusted MCP "
                             + "servers ask the user before running. A read-only tool changes nothing, but its "
                             + "arguments still go to its server, so planted instructions could have the model put "
                             + "data in them. A tool the user has allowed for the chat still runs.")
    @JsonProperty("askReadOnlyToolsAfterUntrustedContent")
    public boolean isAskReadOnlyToolsAfterUntrustedContent() {
        return askReadOnlyToolsAfterUntrustedContent;
    }

    @Override
    @JsonProperty("db")
    public AiDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonPropertyDescription("Caches the answers given by the ai() XSLT and StroomQL functions, keyed on the " +
                             "model, system prompt and message, so that repeated identical questions do not " +
                             "each result in a call to the model. Set maximumSize to 0 to ask the model every " +
                             "time.")
    @JsonProperty("chatResponseCache")
    public CacheConfig getChatResponseCache() {
        return chatResponseCache;
    }

    @JsonPropertyDescription("The longest an 'Ask Stroom AI' agent run may take to answer a question. A run " +
                             "that reaches the limit is stopped, and the user can resume it.")
    @JsonProperty("maxRunDuration")
    public StroomDuration getMaxRunDuration() {
        return maxRunDuration;
    }

    @JsonPropertyDescription("How long after the user's browser last checked on any of their 'Ask Stroom AI' " +
                             "chats an agent run is treated as unattended. An unattended run may only use " +
                             "read-only tools; anything that could change something waits for the user.")
    @JsonProperty("unattendedAfter")
    public StroomDuration getUnattendedAfter() {
        return unattendedAfter;
    }

    @JsonPropertyDescription("The most 'Ask Stroom AI' agent runs a user may have working at the same time, " +
                             "across the cluster. Further questions are queued until one finishes. Runs waiting " +
                             "for the user do not count.")
    @JsonProperty("maxRunsPerUser")
    public int getMaxRunsPerUser() {
        return maxRunsPerUser;
    }

    @JsonPropertyDescription("The most 'Ask Stroom AI' agent runs this node will work on at the same time. " +
                             "Further questions are queued until one finishes. Runs waiting for the user do not " +
                             "count.")
    @JsonProperty("maxRunsPerNode")
    public int getMaxRunsPerNode() {
        return maxRunsPerNode;
    }

    @JsonProperty("compaction")
    public AiCompactionConfig getCompaction() {
        return compaction;
    }

    @JsonPropertyDescription("How long the files in an 'Ask Stroom AI' chat's workspace (attached tables, large " +
                             "tool results and files written by tools) are kept. They hold copies of Stroom data, " +
                             "so they are removed sooner than the chat itself. Set to 0 to keep them as long as " +
                             "the chat.")
    @JsonProperty("workspaceFileRetention")
    public StroomDuration getWorkspaceFileRetention() {
        return workspaceFileRetention;
    }

    @JsonPropertyDescription("How long an 'Ask Stroom AI' chat is kept after it was last changed. Older chats, " +
                             "with their files and memory, are deleted by the 'AI Chat Retention' job. Set to 0 " +
                             "to keep chats until their users delete them.")
    @JsonProperty("chatRetention")
    public StroomDuration getChatRetention() {
        return chatRetention;
    }

    @JsonProperty("workspace")
    public AiWorkspaceConfig getWorkspace() {
        return workspace;
    }

    @JsonPropertyDescription("If true, users may turn on 'approve everything' for a chat, so the agent's tool " +
                             "calls run without asking. Even then it only covers tools on trusted MCP servers that " +
                             "are not marked destructive or open-world, tools set to ASK_ALWAYS or DENY still " +
                             "ask or are never offered, and it never applies while the user is away.")
    @JsonProperty("allowApproveEverything")
    public boolean isAllowApproveEverything() {
        return allowApproveEverything;
    }

    @JsonPropertyDescription("When the MCP tools offered to a model number more than this, they are offered as " +
                             "groups instead, each with a tool that turns the group on, so the model's context is " +
                             "not filled with tool definitions it does not need. 0 offers every tool at once.")
    @JsonProperty("toolGroupThreshold")
    public int getToolGroupThreshold() {
        return toolGroupThreshold;
    }

    @JsonPropertyDescription("How long a form an MCP tool asks the user to fill in (MCP elicitation) waits for an " +
                             "answer before it is cancelled. The tool call, and its thread, wait for it.")
    @JsonProperty("elicitationTimeout")
    public StroomDuration getElicitationTimeout() {
        return elicitationTimeout;
    }

    @JsonPropertyDescription("The most forms from MCP tools (MCP elicitation) that may wait for an answer on a " +
                             "node at once. Each holds a thread, so more are declined.")
    @JsonProperty("maxElicitationsPerNode")
    public int getMaxElicitationsPerNode() {
        return maxElicitationsPerNode;
    }

    @Override
    public String toString() {
        return "AiConfig{" +
               "dbConfig=" + dbConfig +
               ", chatResponseCache=" + chatResponseCache +
               ", maxRunDuration=" + maxRunDuration +
               ", unattendedAfter=" + unattendedAfter +
               ", maxRunsPerUser=" + maxRunsPerUser +
               ", maxRunsPerNode=" + maxRunsPerNode +
               ", compaction=" + compaction +
               ", workspaceFileRetention=" + workspaceFileRetention +
               ", chatRetention=" + chatRetention +
               ", workspace=" + workspace +
               ", allowApproveEverything=" + allowApproveEverything +
               ", toolGroupThreshold=" + toolGroupThreshold +
               ", elicitationTimeout=" + elicitationTimeout +
               ", maxElicitationsPerNode=" + maxElicitationsPerNode +
               ", askReadOnlyToolsAfterUntrustedContent=" + askReadOnlyToolsAfterUntrustedContent +
               ", maxChatTitleWords=" + maxChatTitleWords +
               ", suggestFollowUps=" + suggestFollowUps +
               ", maxToolIterations=" + maxToolIterations +
               ", shareActiveTab=" + shareActiveTab +
               ", maxTabSnapshotBytes=" + maxTabSnapshotBytes +
               ", mcpEndpoint=" + mcpEndpoint +
               ", errorTriage=" + errorTriage +
               ", pipelineStages=" + pipelineStages +
               '}';
    }
}
