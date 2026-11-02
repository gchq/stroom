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

import stroom.ai.shared.AiToolCategory;

import java.util.Set;

/// Stand-ins for Stroom's own tools, with only the flags that decide whether a call needs the user's approval, for
/// consent tests made from outside this package, where [BuiltInTools]' constructor is not visible.
public final class StandInTools {

    private StandInTools() {
        // Static only.
    }

    /// @param tools The tools.
    /// @return The tools, registered as Stroom registers its own tools.
    public static BuiltInTools builtInTools(final StroomTool... tools) {
        return new BuiltInTools(Set.of(tools), ToolCalls.chatContexts());
    }

    /// @param name The tool's name.
    /// @return A tool whose every call changes something in Stroom (e.g. save_xslt), and which doesn't ask the user
    /// itself.
    public static StroomTool writeTool(final String name) {
        return new StandIn(name, Calls.CHANGE, false, true, true);
    }

    /// @param name The tool's name.
    /// @return A tool whose every call changes something in Stroom, and which asks the user itself, through a form,
    /// before each change (e.g. create_feed).
    public static StroomTool selfConfirmingWriteTool(final String name) {
        return new StandIn(name, Calls.CHANGE, true, true, true);
    }

    /// @param name The tool's name.
    /// @return A tool whose calls only read.
    public static StroomTool readOnlyTool(final String name) {
        return new StandIn(name, Calls.READ, false, true, true);
    }

    /// @param name The tool's name.
    /// @return A tool whose calls only read, and whose results hold only trusted content (e.g. get_current_time).
    public static StroomTool trustedReadOnlyTool(final String name) {
        return new StandIn(name, Calls.READ, false, true, false);
    }

    /// @param name The tool's name.
    /// @return A tool whose calls with `"command":"read"` only read, and whose other calls change something.
    public static StroomTool mixedTool(final String name) {
        return new StandIn(name, Calls.MIXED, false, true, true);
    }

    /// @param name The tool's name.
    /// @return A tool whose calls change only the chat's own things (e.g. write_file).
    public static StroomTool chatOnlyWriteTool(final String name) {
        return new StandIn(name, Calls.CHANGE, false, false, true);
    }

    // What the tool's calls do.
    private enum Calls {
        READ,
        CHANGE,
        MIXED
    }

    private static final class StandIn implements StroomTool {

        private final String name;
        private final Calls calls;
        private final boolean confirms;
        private final boolean changesNeedApproval;
        private final boolean readsUntrusted;

        private StandIn(final String name,
                        final Calls calls,
                        final boolean confirms,
                        final boolean changesNeedApproval,
                        final boolean readsUntrusted) {
            this.name = name;
            this.calls = calls;
            this.confirms = confirms;
            this.changesNeedApproval = changesNeedApproval;
            this.readsUntrusted = readsUntrusted;
        }

        @Override
        public ToolDefinition definition() {
            return ToolDefinition.builder()
                    .name(name)
                    .description("Stands in for " + name + ".")
                    .build();
        }

        @Override
        public AiToolCategory category() {
            return AiToolCategory.CONTENT;
        }

        @Override
        public boolean isReadOnly() {
            return calls == Calls.READ;
        }

        @Override
        public boolean isReadOnly(final ToolCall call) {
            return calls == Calls.READ
                   || (calls == Calls.MIXED
                       && call.arguments() != null
                       && call.arguments().contains("\"command\":\"read\""));
        }

        @Override
        public boolean hasReadOnlyCalls() {
            return calls != Calls.CHANGE;
        }

        @Override
        public boolean confirmsItself() {
            return confirms;
        }

        @Override
        public boolean changesNeedApprovalAfterUntrustedContent() {
            return changesNeedApproval;
        }

        @Override
        public boolean readsUntrustedContent() {
            return readsUntrusted;
        }

        @Override
        public ToolResult execute(final ToolCall request, final ToolCallContext context) {
            return ToolResult.text(name + " ran.");
        }
    }
}
