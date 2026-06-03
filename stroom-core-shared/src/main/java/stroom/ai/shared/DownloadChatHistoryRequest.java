/*
 * Copyright 2016-2026 Crown Copyright
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

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class DownloadChatHistoryRequest {

    @JsonProperty
    private final int chatId;
    @JsonProperty
    private final boolean includeDataContexts;

    @JsonCreator
    public DownloadChatHistoryRequest(@JsonProperty("chatId") final int chatId,
                                      @JsonProperty("includeDataContexts") final boolean includeDataContexts) {
        this.chatId = chatId;
        this.includeDataContexts = includeDataContexts;
    }

    public int getChatId() {
        return chatId;
    }

    /**
     * If true, DASHBOARD_DATA, QUERY_DATA, and TABLE_DATA messages are included
     * in the exported Markdown file in addition to the conversation messages.
     */
    public boolean isIncludeDataContexts() {
        return includeDataContexts;
    }
}
