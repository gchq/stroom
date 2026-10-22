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
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/// Where the agent's tools build things: the workspace folder, below System, that holds a folder for each build
/// (the documents the agent makes, until they are promoted into place). The tools write nowhere else.
@JsonPropertyOrder(alphabetic = true)
public class AiBuildsConfig extends AbstractConfig implements IsStroomConfig {

    /// The workspace folder's name unless configured, as the Stroom MCP server named it, so builds it started are
    /// carried on in Stroom.
    public static final String DEFAULT_WORKSPACE_FOLDER = "MCP Workspace";

    private final String workspaceFolder;

    public AiBuildsConfig() {
        workspaceFolder = DEFAULT_WORKSPACE_FOLDER;
    }

    @JsonCreator
    public AiBuildsConfig(@JsonProperty("workspaceFolder") final String workspaceFolder) {
        this.workspaceFolder = NullSafe.nonBlankStringElse(NullSafe.trim(workspaceFolder),
                DEFAULT_WORKSPACE_FOLDER);
    }

    /// @return The name of the folder, directly below System, that holds a folder for each build.
    @JsonPropertyDescription("The name of the folder, directly below System in the explorer tree, in which the "
                             + "agent's tools make a folder for each build (the documents they create, until a "
                             + "build is promoted into place). The tools change only documents they created in "
                             + "this folder. The Stroom MCP server's own default is 'MCP Workspace': keep the "
                             + "name it used to carry on builds it started.")
    @JsonProperty("workspaceFolder")
    public String getWorkspaceFolder() {
        return workspaceFolder;
    }

    @Override
    public String toString() {
        return "AiBuildsConfig{" +
               "workspaceFolder='" + workspaceFolder + '\'' +
               '}';
    }
}
