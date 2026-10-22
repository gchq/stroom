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

/// Sample files sent from the user's machine (the tools' `upload_sample files=`): a command per file, for the
/// user's terminal, posts the file to `/mcp/upload` as the user, into the feed the tool named. As the Stroom MCP
/// server's `upload_ticket_minutes` and `max_upload_mb`.
@JsonPropertyOrder(alphabetic = true)
public class AiSampleUploadsConfig extends AbstractConfig implements IsStroomConfig {

    static final int DEFAULT_MAX_UPLOAD_MB = 100;
    static final StroomDuration DEFAULT_COMMANDS_LAST = StroomDuration.ofMinutes(15);
    static final int DEFAULT_MAX_PENDING_PER_USER = 20;

    private final int maxUploadMb;
    private final StroomDuration commandsLast;
    private final int maxPendingPerUser;

    public AiSampleUploadsConfig() {
        maxUploadMb = DEFAULT_MAX_UPLOAD_MB;
        commandsLast = DEFAULT_COMMANDS_LAST;
        maxPendingPerUser = DEFAULT_MAX_PENDING_PER_USER;
    }

    @JsonCreator
    public AiSampleUploadsConfig(@JsonProperty("maxUploadMb") final Integer maxUploadMb,
                                 @JsonProperty("commandsLast") final StroomDuration commandsLast,
                                 @JsonProperty("maxPendingPerUser") final Integer maxPendingPerUser) {
        this.maxUploadMb = Math.max(1, Objects.requireNonNullElse(maxUploadMb, DEFAULT_MAX_UPLOAD_MB));
        this.commandsLast = Objects.requireNonNullElse(commandsLast, DEFAULT_COMMANDS_LAST);
        this.maxPendingPerUser = Math.max(1, Objects.requireNonNullElse(maxPendingPerUser,
                DEFAULT_MAX_PENDING_PER_USER));
    }

    /// @return The largest sample file, in MB, that may be posted to `/mcp/upload`.
    @JsonPropertyDescription("The largest sample file, in MB, that the user's terminal may post to /mcp/upload "
                             + "(upload_sample with files=). A larger one is refused: the user sends a part of it, "
                             + "or uploads it in Stroom.")
    @JsonProperty("maxUploadMb")
    public int getMaxUploadMb() {
        return maxUploadMb;
    }

    /// @return How long the commands upload_sample gives for sample files can be run.
    @JsonPropertyDescription("How long the commands that upload_sample gives for sample files (files=) can be run: "
                             + "each posts one file, once, to the feed the tool named, as the user. After that the "
                             + "agent asks for fresh commands.")
    @JsonProperty("commandsLast")
    public StroomDuration getCommandsLast() {
        return commandsLast;
    }

    /// @return The most sets of upload commands one user may have waiting at once.
    @JsonPropertyDescription("The most sets of upload commands (upload_sample with files=) one user may have "
                             + "waiting to be run on a node at once. More are refused until some have been run or "
                             + "have run out (commandsLast).")
    @JsonProperty("maxPendingPerUser")
    public int getMaxPendingPerUser() {
        return maxPendingPerUser;
    }

    @Override
    public String toString() {
        return "AiSampleUploadsConfig{" +
               "maxUploadMb=" + maxUploadMb +
               ", commandsLast=" + commandsLast +
               ", maxPendingPerUser=" + maxPendingPerUser +
               '}';
    }
}
