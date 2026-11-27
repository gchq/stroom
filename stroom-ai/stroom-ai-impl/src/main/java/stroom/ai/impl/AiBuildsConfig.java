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
/// (the documents the agent makes, until they are promoted into place), and how many tasks process a build's streams
/// at once. The tools write nowhere else.
@JsonPropertyOrder(alphabetic = true)
public class AiBuildsConfig extends AbstractConfig implements IsStroomConfig {

    /// The workspace folder's name unless configured.
    public static final String DEFAULT_WORKSPACE_FOLDER = "MCP Workspace";
    /// The most tasks processing a build's streams at once unless configured.
    public static final int DEFAULT_MAX_PROCESSING_TASKS = 4;

    private final String workspaceFolder;
    private final int maxProcessingTasks;

    public AiBuildsConfig() {
        workspaceFolder = DEFAULT_WORKSPACE_FOLDER;
        maxProcessingTasks = DEFAULT_MAX_PROCESSING_TASKS;
    }

    @JsonCreator
    public AiBuildsConfig(@JsonProperty("workspaceFolder") final String workspaceFolder,
                          @JsonProperty("maxProcessingTasks") final Integer maxProcessingTasks) {
        this.workspaceFolder = NullSafe.nonBlankStringElse(NullSafe.trim(workspaceFolder),
                DEFAULT_WORKSPACE_FOLDER);
        this.maxProcessingTasks = maxProcessingTasks == null || maxProcessingTasks < 1
                ? DEFAULT_MAX_PROCESSING_TASKS
                : maxProcessingTasks;
    }

    /// @return The name of the folder, directly below System, that holds a folder for each build.
    @JsonPropertyDescription("The name of the folder, directly below System in the explorer tree, in which the "
                             + "agent's tools make a folder for each build (the documents they create, until a "
                             + "build is promoted into place). The tools change only documents they created in "
                             + "this folder. Renaming it leaves builds already in the old folder behind: rename the "
                             + "folder in the explorer tree to match.")
    @JsonProperty("workspaceFolder")
    public String getWorkspaceFolder() {
        return workspaceFolder;
    }

    /// @return The most tasks a processor filter the tools make for a build's own streams (its sample, or streams
    /// it reprocesses) runs at once: one a stream, up to this many.
    @JsonPropertyDescription("The most tasks that process a build's own streams at once: the processor filters the "
                             + "agent's tools make to process a build's sample streams, or to reprocess streams, "
                             + "run up to this many tasks, one a stream, so the sample is processed side by side "
                             + "rather than one stream after another. Lower it to leave more of the nodes' task "
                             + "capacity to other processing. At least 1. Filters made when a build is promoted, "
                             + "over a whole feed, are not affected.")
    @JsonProperty("maxProcessingTasks")
    public int getMaxProcessingTasks() {
        return maxProcessingTasks;
    }

    @Override
    public String toString() {
        return "AiBuildsConfig{" +
               "workspaceFolder='" + workspaceFolder + '\'' +
               ", maxProcessingTasks=" + maxProcessingTasks +
               '}';
    }
}
