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
 * The memory a chat can see, and the changes the agent has made to memory in it.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiMemoryListResponse {

    @JsonProperty
    private final List<AiMemory> memories;
    @JsonProperty
    private final List<AiChatMessage> changes;

    /**
     * @param memories The user's memory, then the chat's.
     * @param changes  The chat's MEMORY_UPDATE messages, latest first.
     */
    @JsonCreator
    public AiMemoryListResponse(@JsonProperty("memories") final List<AiMemory> memories,
                                @JsonProperty("changes") final List<AiChatMessage> changes) {
        this.memories = memories;
        this.changes = changes;
    }

    /**
     * @return The user's memory, then the chat's.
     */
    public List<AiMemory> getMemories() {
        return memories;
    }

    /**
     * @return The chat's MEMORY_UPDATE messages, latest first.
     */
    public List<AiChatMessage> getChanges() {
        return changes;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiMemoryListResponse that = (AiMemoryListResponse) o;
        return Objects.equals(memories, that.memories)
               && Objects.equals(changes, that.changes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memories, changes);
    }
}
