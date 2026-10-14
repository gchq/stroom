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
 * Saves a piece of memory the user has written or edited, e.g. in the Memory dialog.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class SaveAiMemoryRequest {

    @JsonProperty
    private final AiChat aiChat;
    @JsonProperty
    private final String path;
    @JsonProperty
    private final String content;
    @JsonProperty
    private final Integer version;

    /**
     * @param aiChat  The chat whose memory it is, or whose user's memory it is.
     * @param path    The memory's path, e.g. "/memories/preferences.md" or "/memories/session/plan.md".
     * @param content The memory's new content.
     * @param version The version the user edited, or null to create the memory.
     */
    @JsonCreator
    public SaveAiMemoryRequest(@JsonProperty("aiChat") final AiChat aiChat,
                               @JsonProperty("path") final String path,
                               @JsonProperty("content") final String content,
                               @JsonProperty("version") final Integer version) {
        this.aiChat = aiChat;
        this.path = path;
        this.content = content;
        this.version = version;
    }

    /**
     * @return The chat whose memory it is, or whose user's memory it is.
     */
    public AiChat getAiChat() {
        return aiChat;
    }

    /**
     * @return The memory's path, e.g. "/memories/preferences.md" or "/memories/session/plan.md".
     */
    public String getPath() {
        return path;
    }

    /**
     * @return The memory's new content.
     */
    public String getContent() {
        return content;
    }

    /**
     * @return The version the user edited, or null to create the memory.
     */
    public Integer getVersion() {
        return version;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SaveAiMemoryRequest that = (SaveAiMemoryRequest) o;
        return Objects.equals(aiChat, that.aiChat)
               && Objects.equals(path, that.path)
               && Objects.equals(content, that.content)
               && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aiChat, path, content, version);
    }
}
