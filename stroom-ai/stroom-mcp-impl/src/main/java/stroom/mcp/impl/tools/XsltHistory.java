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

import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Clock;
import java.time.Instant;

/// The version history kept in the description of each XSLT the tools save ([XsltVersionHistory]), as the Stroom
/// MCP server's `tools/translation.py` `_pending` and `_previewed` keep it: who made each save, with what, and the
/// build's changes previewed as Unreleased rows.
class XsltHistory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(XsltHistory.class);

    private final SecurityContext securityContext;
    private final Provider<BuildInfo> buildInfoProvider;
    private final Clock clock;

    /// @param securityContext   Says who the user is: the change's author.
    /// @param buildInfoProvider Gives Stroom's version, for what made the change.
    @Inject
    XsltHistory(final SecurityContext securityContext, final Provider<BuildInfo> buildInfoProvider) {
        this(securityContext, buildInfoProvider, Clock.systemUTC());
    }

    /// As the injected constructor, with the clock given, for tests.
    ///
    /// @param securityContext   Says who the user is.
    /// @param buildInfoProvider Gives Stroom's version.
    /// @param clock             The time now.
    XsltHistory(final SecurityContext securityContext,
                final Provider<BuildInfo> buildInfoProvider,
                final Clock clock) {
        this.securityContext = securityContext;
        this.buildInfoProvider = buildInfoProvider;
        this.clock = clock;
    }

    /// @return What a version history row says made an agent change, up to the caller: `Stroom <version>`.
    String madeBy() {
        String version = null;
        try {
            version = NullSafe.get(buildInfoProvider.get(), BuildInfo::getBuildVersion);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No build version: {}", e.getMessage()));
        }
        return "Stroom " + NullSafe.nonBlankStringElse(version, "unknown");
    }

    /// Who made an agent change: Stroom's version and the session's caller (the chat, or the client that
    /// connected over MCP). The Stroom MCP server's `agent_line` also named the model as the client gave it; the
    /// caller is what Stroom knows here.
    ///
    /// @param session The session of the call.
    /// @return The `by` of the change.
    String by(final ToolSession session) {
        final String caller = session == null
                ? null
                : session.caller().name();
        return NullSafe.isNonBlankString(caller)
                ? madeBy() + ", " + caller
                : madeBy();
    }

    /// The description with this change pending: the build's changes become one line of the XSLT's version history
    /// when the build is promoted, not a line a save. The saved code's digest is kept: a later difference from what
    /// its mapping generates is then a hand edit only when the code changed since; otherwise the generator did
    /// (build_status said "edited by hand" after an upgrade).
    ///
    /// @param session     The session of the call.
    /// @param description The XSLT's description, or null.
    /// @param previous    The XSLT as it was before this save, or null for a new one.
    /// @param change      What changed.
    /// @param code        The code saved.
    /// @return The description with the change pending.
    String pending(final ToolSession session,
                   final String description,
                   final XsltVersionHistory.Previous previous,
                   final String change,
                   final String code) {
        return XsltVersionHistory.withPending(description, previous, author(), change, by(session), now(), code);
    }

    /// Who made a documentation change, as a doc's version control row names them (the Stroom MCP server's
    /// `tools/builds.py` `_with_change`): the user, and through what.
    ///
    /// @param session The session of the call.
    /// @return E.g. `jbloggs (agent: Stroom 7.13, Ask Stroom AI)`.
    String changedBy(final ToolSession session) {
        return author() + " (agent: " + by(session) + ")";
    }

    /// @return The time now, as this history dates its rows.
    Instant now() {
        return clock.instant();
    }

    /// @param description The XSLT's description.
    /// @param code        Its code.
    /// @return The description with the build's changes so far previewed in its version history, marked
    /// Unreleased.
    String previewed(final String description, final String code) {
        return XsltVersionHistory.preview(description, NullSafe.string(code), now());
    }

    // The user, as Stroom records them as a document's updater.
    private String author() {
        try {
            return securityContext.getUserIdentityForAudit();
        } catch (final RuntimeException e) {
            return "-";
        }
    }
}
