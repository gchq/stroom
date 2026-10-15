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

package stroom.openai.shared;

import stroom.docref.HasDisplayValue;

/**
 * What Stroom does when the "Ask Stroom AI" agent wants to call a tool of an
 * {@link McpToolServerConfig MCP tool server}. Set by an admin for tools matching a pattern; tools with no
 * matching rule get a default from the server's trust and the tool's annotations.
 */
public enum McpToolPolicy implements HasDisplayValue {

    /**
     * The tool runs without asking the user, e.g. a read-only tool, or one that asks for confirmation through
     * its own MCP form.
     */
    ALLOW("Allow"),


    /**
     * The user is asked every time. The tool cannot be allowed for the rest of the chat, and is never covered
     * by 'approve everything'.
     */
    ASK_ALWAYS("Ask every time"),

    /**
     * The tool is never offered to the model.
     */
    DENY("Deny");

    private final String displayValue;

    McpToolPolicy(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
