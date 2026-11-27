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

package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/**
 * The statuses of a user's chats, or nothing if none has changed since the version the client last had.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiChatStatusResponse {

    @JsonProperty
    private final long version;
    @JsonProperty
    private final List<AiChatStatus> statuses;

    /**
     * @param version  The version of the statuses, to send back next time.
     * @param statuses The statuses, most recently changed first, or null if unchanged.
     */
    @JsonCreator
    public AiChatStatusResponse(@JsonProperty("version") final Long version,
                                @JsonProperty("statuses") final List<AiChatStatus> statuses) {
        this.version = Objects.requireNonNullElse(version, 0L);
        this.statuses = statuses;
    }

    public long getVersion() {
        return version;
    }

    /**
     * @return The statuses, most recently changed first, or null if none has changed.
     */
    public List<AiChatStatus> getStatuses() {
        return statuses;
    }
}
