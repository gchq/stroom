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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Puts the tools of an MCP tool server whose names match a pattern into a group, which the agent turns on when it
 * needs it, so its tools' definitions are only sent to the model then.
 */
@JsonPropertyOrder({"toolPattern", "group"})
@JsonInclude(Include.NON_NULL)
public class McpToolGroupRule {

    /**
     * The group name that means the matching tools are never grouped: they are always offered.
     */
    public static final String ALWAYS_OFFERED = "-";

    @JsonPropertyDescription("The tool names the rule applies to. '*' matches any characters. Matched against " +
                             "the tool's own name, ignoring case.")
    @JsonProperty
    private final String toolPattern;

    @JsonPropertyDescription("The group the matching tools go in, or '-' to always offer them.")
    @JsonProperty
    private final String group;

    @JsonCreator
    public McpToolGroupRule(@JsonProperty("toolPattern") final String toolPattern,
                            @JsonProperty("group") final String group) {
        this.toolPattern = toolPattern;
        this.group = group;
    }

    public String getToolPattern() {
        return toolPattern;
    }

    /**
     * @return The group, or {@link #ALWAYS_OFFERED}.
     */
    public String getGroup() {
        return group;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final McpToolGroupRule that = (McpToolGroupRule) o;
        return Objects.equals(toolPattern, that.toolPattern) && Objects.equals(group, that.group);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolPattern, group);
    }

    @Override
    public String toString() {
        return toolPattern + " = " + group;
    }
}
