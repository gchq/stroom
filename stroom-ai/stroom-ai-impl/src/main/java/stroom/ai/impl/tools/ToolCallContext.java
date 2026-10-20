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

import stroom.ai.impl.workspace.SessionWorkspace;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/// What a [StroomTool] is given with each call, in place of the chat it used to be given: the session it is made
/// in (and so who for), the session's workspace if it has one, how to ask the user, where to report progress, the
/// session's limits (including the time limit), how to link to Stroom objects for the caller, and the agent run the
/// call is part of, if any.
///
/// A chat's contexts are made by [ChatToolCallContexts]. Nothing here is specific to the chat, so the same tools
/// can be called from other sessions (e.g. over MCP) with other contexts.
public final class ToolCallContext {

    private final ToolSession session;
    private final SessionWorkspace workspace;
    private final UserQuestions userQuestions;
    private final ToolProgress progress;
    private final Duration timeLimit;
    private final SessionLimits limits;
    private final ToolRunContext runContext;
    private final StroomLinks links;

    private ToolCallContext(final Builder builder) {
        this.session = Objects.requireNonNull(builder.session, "session");
        this.workspace = builder.workspace;
        this.userQuestions = Objects.requireNonNullElse(builder.userQuestions, UserQuestions.NONE);
        this.progress = Objects.requireNonNullElse(builder.progress, ToolProgress.NONE);
        this.timeLimit = builder.timeLimit;
        this.limits = builder.limits;
        this.runContext = builder.runContext;
        this.links = Objects.requireNonNullElse(builder.links, StroomLinks.CHAT);
    }

    /// @param session The session the call is made in.
    /// @return A builder for a context.
    public static Builder builder(final ToolSession session) {
        return new Builder(session);
    }

    /// @return The session the call is made in.
    public ToolSession session() {
        return session;
    }

    /// @return Who the call is made for.
    public ToolCaller caller() {
        return session.caller();
    }

    /// @return The session's workspace, or empty if it has none. A chat has one.
    public Optional<SessionWorkspace> workspace() {
        return Optional.ofNullable(workspace);
    }

    /// @param toolName The tool that needs the workspace, to say so if there is none.
    /// @return The session's workspace.
    /// @throws ToolSessionException if the session has none.
    public SessionWorkspace requireWorkspace(final String toolName) {
        if (workspace == null) {
            throw new ToolSessionException(toolName + " can only be used in a chat.");
        }
        return workspace;
    }

    /// @return How to ask the user questions in the session.
    public UserQuestions userQuestions() {
        return userQuestions;
    }

    /// @return Where to report how a long call is getting on.
    public ToolProgress progress() {
        return progress;
    }

    /// @return The time limit of a call in the session: the one given to the builder, else the session's tool time
    /// limit ([SessionLimits#toolTimeLimit()]), or empty if neither is set. Tools that need longer (e.g. to run a
    /// query) take theirs from [#limits()].
    public Optional<Duration> timeLimit() {
        if (timeLimit != null) {
            return Optional.of(timeLimit);
        }
        return limits == null
                ? Optional.empty()
                : limits.toolTimeLimit();
    }

    /// @return The limits that apply to the session's calls (how much a call may return, how long it may take,
    /// and so on), or [SessionLimits#DEFAULTS] if the session sets none.
    public SessionLimits limits() {
        return Objects.requireNonNullElse(limits, SessionLimits.DEFAULTS);
    }

    /// Records that something a tool tried in the session failed, for tools that fall back to something else once
    /// a thing has failed a few times (e.g. the table tool). It is counted by the agent run the call is part of, as
    /// a chat's counts start afresh with each run.
    ///
    /// @param key What failed, e.g. "table:/attachments/1.md".
    /// @return How many times it has now failed, or 0 if the call isn't part of a run, so nothing is counted.
    public int recordFailure(final String key) {
        return runContext()
                .map(run -> run.recordFailure(key))
                .orElse(0);
    }

    /// @return The agent run the call is part of (its model, whether it has been stopped, and what it has
    /// counted), or empty if it isn't part of one.
    public Optional<ToolRunContext> runContext() {
        return Optional.ofNullable(runContext);
    }

    /// @return How to make links to Stroom objects (documents, streams and events) in what the call returns, in the
    /// form its caller can open.
    public StroomLinks links() {
        return links;
    }

    @Override
    public String toString() {
        return "ToolCallContext{session=" + session.kind() + " " + session.id() + ", caller=" + caller().name()
               + "}";
    }

    /// Builds a [ToolCallContext].
    public static final class Builder {

        private final ToolSession session;
        private SessionWorkspace workspace;
        private UserQuestions userQuestions;
        private ToolProgress progress;
        private Duration timeLimit;
        private SessionLimits limits;
        private ToolRunContext runContext;
        private StroomLinks links;

        private Builder(final ToolSession session) {
            this.session = session;
        }

        /// @param workspace The session's workspace, or null if it has none.
        /// @return This builder.
        public Builder workspace(final SessionWorkspace workspace) {
            this.workspace = workspace;
            return this;
        }

        /// @param userQuestions How to ask the user questions; [UserQuestions#NONE] if not given.
        /// @return This builder.
        public Builder userQuestions(final UserQuestions userQuestions) {
            this.userQuestions = userQuestions;
            return this;
        }

        /// @param progress Where to report progress; [ToolProgress#NONE] if not given.
        /// @return This builder.
        public Builder progress(final ToolProgress progress) {
            this.progress = progress;
            return this;
        }

        /// @param timeLimit The time limit of a call, or null for none.
        /// @return This builder.
        public Builder timeLimit(final Duration timeLimit) {
            this.timeLimit = timeLimit;
            return this;
        }

        /// @param limits The limits that apply to the session's calls, or null for [SessionLimits#DEFAULTS] (and no
        ///               time limit, unless one is given).
        /// @return This builder.
        public Builder limits(final SessionLimits limits) {
            this.limits = limits;
            return this;
        }

        /// @param runContext The agent run the call is part of, or null.
        /// @return This builder.
        public Builder runContext(final ToolRunContext runContext) {
            this.runContext = runContext;
            return this;
        }

        /// @param links How to make links to Stroom objects for the caller; [StroomLinks#CHAT] if not given.
        /// @return This builder.
        public Builder links(final StroomLinks links) {
            this.links = links;
            return this;
        }

        /// @return The context.
        public ToolCallContext build() {
            return new ToolCallContext(this);
        }
    }
}
