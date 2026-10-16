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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/// One of an MCP tool server's tools, as listed for an administrator to look through.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class McpToolDescription {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final String documentation;
    @JsonProperty
    private final List<String> classifications;
    @JsonProperty
    private final String group;

    /// @param name            The tool's name, as its server gives it.
    /// @param enabled         True if the server's tool policies let the model use it (false if one denies it).
    /// @param documentation   What the tool is and takes, as Markdown.
    /// @param classifications What its server says it does, e.g. "read-only", or "can modify" and "destructive".
    /// @param group           The group its server puts it in, or null.
    @JsonCreator
    public McpToolDescription(@JsonProperty("name") final String name,
                              @JsonProperty("enabled") final Boolean enabled,
                              @JsonProperty("documentation") final String documentation,
                              @JsonProperty("classifications") final List<String> classifications,
                              @JsonProperty("group") final String group) {
        this.name = name;
        this.enabled = Objects.requireNonNullElse(enabled, true);
        this.documentation = documentation;
        this.classifications = classifications == null
                ? List.of()
                : List.copyOf(classifications);
        this.group = group;
    }

    public String getName() {
        return name;
    }

    /// @return True if the server's tool policies let the model use the tool.
    public boolean isEnabled() {
        return enabled;
    }

    /// @return What the tool is and takes, as Markdown.
    public String getDocumentation() {
        return documentation;
    }

    /// @return What its server says it does, e.g. "read-only", or "can modify" and "destructive".
    public List<String> getClassifications() {
        return classifications;
    }

    /// @return What its server says it does, as text, e.g. "can modify, destructive".
    @JsonIgnore
    public String getType() {
        return String.join(", ", classifications);
    }

    /// @return The group its server puts it in, or null.
    public String getGroup() {
        return group;
    }
}
