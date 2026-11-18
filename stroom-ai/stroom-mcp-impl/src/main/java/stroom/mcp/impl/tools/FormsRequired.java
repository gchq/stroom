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
import stroom.util.shared.NullSafe;

/// What a call that changes something is told when the user can't be asked to agree to it in a form.
///
/// A change in Stroom needs the user's agreement in a form: an MCP client's form (MCP elicitation) over `/mcp`, or
/// the chat's own form. The model is never given an id to pass back instead, as a model holding one could approve
/// its own call. So, over `/mcp`, a client that didn't say it can show forms can't make changes at all (its
/// read-only calls still work), and in the chat nothing that needs the user's agreement is done while they are away.
public final class FormsRequired {

    // What the agent tells the user, the same whatever was refused.
    private static final String WHAT_THE_USER_NEEDS = "Tell the user that this needs an MCP client that supports "
                                                      + "elicitation (one that declares the elicitation capability "
                                                      + "when it connects), or Stroom's own Ask Stroom AI chat. "
                                                      + "Don't look for another way round it.";

    private FormsRequired() {
        // Static methods only.
    }

    /// @param toolName The tool called.
    /// @return What an MCP client that can't show forms is told when it calls a tool to change something.
    public static String noForms(final String toolName) {
        return "Not run: " + toolName + " changes something in Stroom, and every change in Stroom needs the user's "
               + "agreement in a form that their MCP client shows (MCP elicitation). This client did not say, when "
               + "it connected, that it can show forms, so nothing in Stroom can be changed from it, and nothing "
               + "was changed. The read-only tools still work. " + WHAT_THE_USER_NEEDS;
    }

    /// @param session The session the call is made in.
    /// @param action  The tool called, e.g. `create_feed`.
    /// @param summary What the user would have been asked to agree to.
    /// @return What the call is told when the user can't be asked a form in its session now.
    public static String cannotAsk(final ToolSession session, final String action, final String summary) {
        final String what = NullSafe.nonBlankStringElse(NullSafe.trim(summary), action);
        if (session != null && session.isChat()) {
            return "Not done: " + what + ". This needs the user's agreement in a form, and they are away from the "
                   + "chat, so they can't be asked now: nothing was done. Carry on with what doesn't need their "
                   + "agreement, and call " + action + " again when they are back: they are asked then.";
        }
        return "Not done: " + what + ". This needs the user's agreement in a form that their MCP client shows (MCP "
               + "elicitation), and this client did not say, when it connected, that it can show forms, so they "
               + "can't be asked, and nothing was done. Calls that need no agreement still work. "
               + WHAT_THE_USER_NEEDS;
    }
}
