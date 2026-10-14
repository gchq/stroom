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

import java.util.Objects;

/**
 * Deletes a piece of memory, or all memory with a scope.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class DeleteAiMemoryRequest {

    @JsonProperty
    private final AiChat aiChat;
    @JsonProperty
    private final String path;
    @JsonProperty
    private final Integer version;

    /**
     * @param aiChat  The chat whose memory it is, or whose user's memory it is.
     * @param path    The memory's path, or a folder ("/memories/" or "/memories/session/") to delete all in it.
     * @param version The version the user last saw, or null when deleting a folder.
     */
    @JsonCreator
    public DeleteAiMemoryRequest(@JsonProperty("aiChat") final AiChat aiChat,
                                 @JsonProperty("path") final String path,
                                 @JsonProperty("version") final Integer version) {
        this.aiChat = aiChat;
        this.path = path;
        this.version = version;
    }

    /**
     * @return The chat whose memory it is, or whose user's memory it is.
     */
    public AiChat getAiChat() {
        return aiChat;
    }

    /**
     * @return The memory's path, or a folder ("/memories/" or "/memories/session/") to delete all in it.
     */
    public String getPath() {
        return path;
    }

    /**
     * @return The version the user last saw, or null when deleting a folder.
     */
    public Integer getVersion() {
        return version;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DeleteAiMemoryRequest that = (DeleteAiMemoryRequest) o;
        return Objects.equals(aiChat, that.aiChat)
               && Objects.equals(path, that.path)
               && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aiChat, path, version);
    }
}
