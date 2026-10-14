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
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// Limits on the files in 'Ask Stroom AI' chat workspaces, and on the tools that work on them.
@JsonPropertyOrder(alphabetic = true)
public class AiWorkspaceConfig extends AbstractConfig implements IsStroomConfig {

    static final long DEFAULT_MAX_FILE_BYTES = 20L * 1024 * 1024;
    static final long DEFAULT_MAX_CHAT_BYTES = 100L * 1024 * 1024;
    static final long DEFAULT_MAX_USER_BYTES = 500L * 1024 * 1024;
    static final int DEFAULT_MAX_LINES_PER_READ = 500;
    static final int DEFAULT_MAX_CHARS_PER_READ = 20_000;
    static final StroomDuration DEFAULT_TOOL_TIME_LIMIT = StroomDuration.ofSeconds(30);

    private final long maxFileBytes;
    private final long maxChatBytes;
    private final long maxUserBytes;
    private final int maxLinesPerRead;
    private final int maxCharsPerRead;
    private final StroomDuration toolTimeLimit;

    public AiWorkspaceConfig() {
        maxFileBytes = DEFAULT_MAX_FILE_BYTES;
        maxChatBytes = DEFAULT_MAX_CHAT_BYTES;
        maxUserBytes = DEFAULT_MAX_USER_BYTES;
        maxLinesPerRead = DEFAULT_MAX_LINES_PER_READ;
        maxCharsPerRead = DEFAULT_MAX_CHARS_PER_READ;
        toolTimeLimit = DEFAULT_TOOL_TIME_LIMIT;
    }

    @JsonCreator
    public AiWorkspaceConfig(@JsonProperty("maxFileBytes") final Long maxFileBytes,
                             @JsonProperty("maxChatBytes") final Long maxChatBytes,
                             @JsonProperty("maxUserBytes") final Long maxUserBytes,
                             @JsonProperty("maxLinesPerRead") final Integer maxLinesPerRead,
                             @JsonProperty("maxCharsPerRead") final Integer maxCharsPerRead,
                             @JsonProperty("toolTimeLimit") final StroomDuration toolTimeLimit) {
        this.maxFileBytes = Objects.requireNonNullElse(maxFileBytes, DEFAULT_MAX_FILE_BYTES);
        this.maxChatBytes = Objects.requireNonNullElse(maxChatBytes, DEFAULT_MAX_CHAT_BYTES);
        this.maxUserBytes = Objects.requireNonNullElse(maxUserBytes, DEFAULT_MAX_USER_BYTES);
        this.maxLinesPerRead = Objects.requireNonNullElse(maxLinesPerRead, DEFAULT_MAX_LINES_PER_READ);
        this.maxCharsPerRead = Objects.requireNonNullElse(maxCharsPerRead, DEFAULT_MAX_CHARS_PER_READ);
        this.toolTimeLimit = Objects.requireNonNullElse(toolTimeLimit, DEFAULT_TOOL_TIME_LIMIT);
    }

    @JsonPropertyDescription("The largest a single workspace file may be, in bytes.")
    @JsonProperty("maxFileBytes")
    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    @JsonPropertyDescription("The most a chat's workspace files may hold in all, in bytes.")
    @JsonProperty("maxChatBytes")
    public long getMaxChatBytes() {
        return maxChatBytes;
    }

    @JsonPropertyDescription("The most all of a user's chat workspaces may hold in all, in bytes.")
    @JsonProperty("maxUserBytes")
    public long getMaxUserBytes() {
        return maxUserBytes;
    }

    @JsonPropertyDescription("The most lines of a file a tool returns to the model in one call.")
    @JsonProperty("maxLinesPerRead")
    public int getMaxLinesPerRead() {
        return maxLinesPerRead;
    }

    @JsonPropertyDescription("The most characters a tool returns to the model in one call.")
    @JsonProperty("maxCharsPerRead")
    public int getMaxCharsPerRead() {
        return maxCharsPerRead;
    }

    @JsonPropertyDescription("The longest a single workspace tool call (e.g. a search, query or table step) may " +
                             "run before it is stopped.")
    @JsonProperty("toolTimeLimit")
    public StroomDuration getToolTimeLimit() {
        return toolTimeLimit;
    }

    @Override
    public String toString() {
        return "AiWorkspaceConfig{" +
               "maxFileBytes=" + maxFileBytes +
               ", maxChatBytes=" + maxChatBytes +
               ", maxUserBytes=" + maxUserBytes +
               ", maxLinesPerRead=" + maxLinesPerRead +
               ", maxCharsPerRead=" + maxCharsPerRead +
               ", toolTimeLimit=" + toolTimeLimit +
               '}';
    }
}
