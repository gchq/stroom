/*
 * Copyright 2016 Crown Copyright
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

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class AiChatPollRequest {

    @JsonProperty
    private final int lastSeenMessageId;
    @JsonProperty
    private final boolean markRead;

    /**
     * @param lastSeenMessageId The id of the last message the client has.
     * @param markRead          True if the chat is being shown, so its messages count as read.
     */
    @JsonCreator
    public AiChatPollRequest(@JsonProperty("lastSeenMessageId") final Integer lastSeenMessageId,
                             @JsonProperty("markRead") final Boolean markRead) {
        this.lastSeenMessageId = Objects.requireNonNullElse(lastSeenMessageId, 0);
        this.markRead = Objects.requireNonNullElse(markRead, false);
    }

    public AiChatPollRequest(final Integer lastSeenMessageId) {
        this(lastSeenMessageId, false);
    }

    public int getLastSeenMessageId() {
        return lastSeenMessageId;
    }

    /**
     * @return True if the chat is being shown, so its messages count as read.
     */
    public boolean isMarkRead() {
        return markRead;
    }
}
