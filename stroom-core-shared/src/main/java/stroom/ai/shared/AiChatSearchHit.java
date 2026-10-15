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

/// A chat found by searching the user's chats: its title or one of the user's questions in it matched.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiChatSearchHit {

    @JsonProperty
    private final int chatId;
    @JsonProperty
    private final String title;
    @JsonProperty
    private final long updateTimeMs;
    @JsonProperty
    private final String snippet;

    /// @param chatId       The chat.
    /// @param title        Its title.
    /// @param updateTimeMs When it was last active.
    /// @param snippet      Part of the latest of the user's questions in it that matched, around the match, or
    ///                     null if only the title matched.
    @JsonCreator
    public AiChatSearchHit(@JsonProperty("chatId") final Integer chatId,
                           @JsonProperty("title") final String title,
                           @JsonProperty("updateTimeMs") final Long updateTimeMs,
                           @JsonProperty("snippet") final String snippet) {
        this.chatId = Objects.requireNonNullElse(chatId, 0);
        this.title = title;
        this.updateTimeMs = Objects.requireNonNullElse(updateTimeMs, 0L);
        this.snippet = snippet;
    }

    public int getChatId() {
        return chatId;
    }

    public String getTitle() {
        return title;
    }

    public long getUpdateTimeMs() {
        return updateTimeMs;
    }

    /// @return Part of the latest matching question, around the match, or null if only the title matched.
    public String getSnippet() {
        return snippet;
    }
}
