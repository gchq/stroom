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

import stroom.ai.impl.AiToolApprovalDao.Approval;
import stroom.ai.impl.tools.BuiltInTools;
import stroom.ai.shared.AiToolApprovalDecision;
import stroom.openai.shared.McpToolPolicy;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Decides whether a tool call the model asks for runs straight away, needs the user's approval first, or is not
/// run:
///
/// - built-in tools run, while the user is away too (they only reach the chat's own workspace and memory, which
///   have their own controls);
/// - an MCP tool with an ALLOW policy runs, ASK_ALWAYS always asks, DENY tools are never offered;
/// - a trusted server's tool marked `_meta.confirms` runs, even while the user is away, as it asks the user
///   itself through a form (unless its policy is ASK_ALWAYS);
/// - otherwise a tool runs if allowed for the chat (and unchanged since), if its server's read-only tools are
///   allowed for the chat and it is read-only, if its server is trusted and it is marked read-only, or if
///   'approve everything' is on for the chat (only if an administrator allows it, and only for trusted servers'
///   tools that are not destructive or open-world) (S4);
/// - while the user is away (an unattended run), only read-only tools on trusted servers run; anything else asks,
///   whatever the policies or approvals say (S3);
/// - if an administrator sets `askReadOnlyToolsAfterUntrustedContent`, read-only tools on trusted servers ask too
///   once the run has read data that could hold planted instructions, as their arguments go to the server. A tool
///   the user has allowed for the chat still runs.
///
/// Annotations are only hints from the server, so they only count on trusted servers, except that the user may
/// choose to allow a server's tools that say they are read-only.
public class ToolConsent {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ToolConsent.class);

    // The classification of an untrusted server's tools, which the chat shows as a warning beside the server.
    static final String UNTRUSTED_SERVER = "untrusted server";
    static final String DATA_WAS_READ_REASON = "This tool only reads, but this chat has read data from tools or "
                                               + "attached tables, which could tell the model to put that data in "
                                               + "this call, and the tool's server would receive it.";

    private final AiToolApprovalDao approvalDao;
    private final AiDao aiDao;
    private final Provider<AiConfig> aiConfigProvider;
    private final Provider<AiChatRunManager> runManagerProvider;

    @Inject
    ToolConsent(final AiToolApprovalDao approvalDao,
                final AiDao aiDao,
                final Provider<AiConfig> aiConfigProvider,
                final Provider<AiChatRunManager> runManagerProvider) {
        this.runManagerProvider = runManagerProvider;
        this.approvalDao = approvalDao;
        this.aiDao = aiDao;
        this.aiConfigProvider = aiConfigProvider;
    }

    /// @param chatId       The chat.
    /// @param toolName     The tool, as the model named it.
    /// @param builtInTools The built-in tools.
    /// @param toolset      The MCP tools.
    /// @param unattended   True if the user is away.
    /// @return What to do.
    public Verdict decide(final int chatId,
                          final String toolName,
                          final BuiltInTools builtInTools,
                          final McpToolset toolset,
                          final boolean unattended) {
        if (builtInTools.toolMap().keySet().stream().anyMatch(spec -> spec.name().equals(toolName))) {
            // Even those that change things only reach the chat's own workspace and memory, which have their own
            // controls (S6, S7), so they run while the user is away too.
            return Verdict.RUN;
        }
        final McpToolInfo info = toolset.toolInfo(toolName).orElse(null);
        if (info == null) {
            // The resource tools, which only read.
            return toolset.isReadOnly(toolName)
                    ? Verdict.RUN
                    : Verdict.ask("This tool is not known to be safe.", false, false, List.of());
        }
        final List<String> risks = risks(info);
        // A trusted server's tool that asks the user itself, through a form, is not asked about twice. Its form
        // waits for the user, so this holds while they are away too.
        if (info.trusted() && info.confirms() && info.policy() != McpToolPolicy.ASK_ALWAYS) {
            return Verdict.RUN;
        }
        // Read-only tools on trusted servers normally run without asking. An administrator can have them ask
        // once the run has read data that could hold planted instructions, as their arguments go to the server.
        final boolean readOnlyMayRun = info.trusted() && info.claimsReadOnly() && !askAsDataWasRead(chatId);
        if (unattended) {
            return readOnlyMayRun
                    ? Verdict.RUN
                    : Verdict.ask(awayReason(), false, false, risks);
        }
        if (info.policy() == McpToolPolicy.ALLOW) {
            return Verdict.RUN;
        }
        final boolean askAlways = info.policy() == McpToolPolicy.ASK_ALWAYS;
        if (!askAlways) {
            final Optional<Approval> approval = current(chatId, info.serverKey(), info.toolName(), info.hash());
            if (approval.isPresent()) {
                return approval.get().decision() == AiToolApprovalDecision.ALLOW
                        ? Verdict.RUN
                        : Verdict.deny("You chose not to allow this tool in this chat.");
            }
            if (info.claimsReadOnly() && !askAsDataWasRead(chatId) && current(chatId, info.serverKey(),
                    AiToolApprovalDao.ALL_READ_ONLY_TOOLS, info.serverHash())
                    .filter(a -> a.decision() == AiToolApprovalDecision.ALLOW).isPresent()) {
                return Verdict.RUN;
            }
            if (readOnlyMayRun) {
                return Verdict.RUN;
            }
            if (info.trusted() && info.claimsReadOnly()) {
                return Verdict.ask(DATA_WAS_READ_REASON, true, false, List.of("after reading data"));
            }
            // A trusted server's tool that can modify data, but is not destructive and stays within Stroom, runs
            // without asking, unless the run has read an untrusted server's output, which could hold instructions
            // planted to misuse it.
            if (info.trusted() && !info.isRisky() && !runManagerProvider.get().hasReadUntrustedServer(chatId)) {
                return Verdict.RUN;
            }
            if (info.trusted() && !info.isRisky() && approveEverything(chatId)) {
                return Verdict.RUN;
            }
        }
        return Verdict.ask(reason(info), cardReason(info), !askAlways, !askAlways && info.claimsReadOnly(),
                risks);
    }

    // True if read-only tools are to ask because the run has read data that could hold planted instructions.
    private boolean askAsDataWasRead(final int chatId) {
        return NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::isAskReadOnlyToolsAfterUntrustedContent, false)
               && runManagerProvider.get().isTainted(chatId);
    }

    private boolean approveEverything(final int chatId) {
        return NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::isAllowApproveEverything, false)
               && aiDao.isApproveEverything(chatId);
    }

    /// @return The decision held for the tool, if it is for the tool as it is now. One made for an earlier
    /// version of the tool (or its server) is removed, so the user is asked again.
    private Optional<Approval> current(final int chatId,
                                       final String serverKey,
                                       final String toolName,
                                       final String hash) {
        final Optional<Approval> approval = approvalDao.find(chatId, serverKey, toolName);
        if (approval.isPresent() && !approval.get().toolHash().equals(hash)) {
            LOGGER.info(() -> "Tool " + serverKey + "/" + toolName + " has changed, so its approval in chat " + chatId
                              + " has been removed");
            approvalDao.delete(approval.get().id());
            return Optional.empty();
        }
        return approval;
    }

    static List<String> risks(final McpToolInfo info) {
        final List<String> risks = new ArrayList<>();
        if (!info.trusted()) {
            risks.add(UNTRUSTED_SERVER);
        }
        // What the tool does, as its server says.
        risks.add(Boolean.TRUE.equals(info.readOnlyHint())
                ? "read-only"
                : "can modify");
        if (Boolean.TRUE.equals(info.destructiveHint())) {
            risks.add("destructive");
        }
        if (Boolean.TRUE.equals(info.openWorldHint())) {
            risks.add("reaches outside Stroom");
        }
        return risks;
    }

    /// @return What the approval card says about why the call needs approval, or null if the card's
    /// classifications (e.g. "can modify", "destructive") and the trust icon beside the server already say it.
    private static String cardReason(final McpToolInfo info) {
        if (info.trusted() && (Boolean.TRUE.equals(info.destructiveHint())
                               || Boolean.TRUE.equals(info.openWorldHint()))) {
            return null;
        }
        if (info.policy() == McpToolPolicy.ASK_ALWAYS || (info.trusted() && !info.isRisky())) {
            // Set by an administrator, or because the chat has read data: not shown otherwise.
            return reason(info);
        }
        return null;
    }

    private static String reason(final McpToolInfo info) {
        if (!info.trusted()) {
            return "Its server, " + info.serverKey() + ", is not trusted, so anything it says about the tool cannot "
                   + "be relied on.";
        } else if (Boolean.TRUE.equals(info.destructiveHint())) {
            return "The tool can delete or overwrite things.";
        } else if (Boolean.TRUE.equals(info.openWorldHint())) {
            return "The tool reaches outside Stroom.";
        } else if (info.policy() == McpToolPolicy.ASK_ALWAYS) {
            return "An administrator has set this tool to ask every time.";
        }
        return info.trusted() && !info.isRisky()
                ? "This chat has read the output of an untrusted MCP server, which could tell the model to misuse "
                  + "this tool, and it can modify data."
                : "This tool can modify data.";
    }

    private static String awayReason() {
        return "You were away when the agent wanted to do this, and the tool can modify data.";
    }

    /// What to do about a tool call.
    ///
    /// @param kind                Run it, ask the user, or do not run it.
    /// @param reason              Why it needs approval, or why it is not run.
    /// @param allowForChat        True if the user may allow the tool for the rest of the chat.
    /// @param allowServerReadOnly True if the user may allow the server's read-only tools for the chat.
    /// @param risks               Why the call might matter.
    public record Verdict(Kind kind,
                          String reason,
                          String cardReason,
                          boolean allowForChat,
                          boolean allowServerReadOnly,
                          List<String> risks) {

        public static final Verdict RUN = new Verdict(Kind.RUN, null, null, false, false, List.of());

        static Verdict ask(final String reason,
                           final boolean allowForChat,
                           final boolean allowServerReadOnly,
                           final List<String> risks) {
            return ask(reason, reason, allowForChat, allowServerReadOnly, risks);
        }

        /// @param reason     Why the call needs approval, e.g. for the log.
        /// @param cardReason What the approval card says, or null if its classifications and trust icon already
        ///                   say it.
        static Verdict ask(final String reason,
                           final String cardReason,
                           final boolean allowForChat,
                           final boolean allowServerReadOnly,
                           final List<String> risks) {
            return new Verdict(Kind.ASK, reason, cardReason, allowForChat, allowServerReadOnly, risks);
        }

        static Verdict deny(final String reason) {
            return new Verdict(Kind.DENY, reason, reason, false, false, List.of());
        }
    }

    /// What to do about a tool call.
    public enum Kind {
        RUN,
        ASK,
        DENY
    }
}
