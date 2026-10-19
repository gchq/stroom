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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Asks for what the user might ask about what they have just attached to a chat, given the conversation so far.
@JsonInclude(Include.NON_NULL)
public class SuggestAiPromptsRequest {

    @JsonProperty
    private final AiChat aiChat;
    @JsonProperty
    private final AskStroomAiConfig config;
    @JsonProperty
    private final List<Integer> attachmentIds;
    @JsonProperty
    private final AiTabSnapshot tab;

    /// @param aiChat        The chat.
    /// @param config        The chat's settings, for its model.
    /// @param attachmentIds The chat's attachments (documents and tables) not yet sent with a question, most
    ///                      recently attached first.
    /// @param tab           The tab attached to the chat, without its content (only what names it and the tables
    ///                      it shows), or null if none is.
    @JsonCreator
    public SuggestAiPromptsRequest(@JsonProperty("aiChat") final AiChat aiChat,
                                   @JsonProperty("config") final AskStroomAiConfig config,
                                   @JsonProperty("attachmentIds") final List<Integer> attachmentIds,
                                   @JsonProperty("tab") final AiTabSnapshot tab) {
        this.aiChat = aiChat;
        this.config = config;
        this.attachmentIds = attachmentIds == null
                ? Collections.emptyList()
                : new ArrayList<>(attachmentIds);
        this.tab = tab;
    }

    public AiChat getAiChat() {
        return aiChat;
    }

    public AskStroomAiConfig getConfig() {
        return config;
    }

    public List<Integer> getAttachmentIds() {
        return attachmentIds;
    }

    public AiTabSnapshot getTab() {
        return tab;
    }
}
