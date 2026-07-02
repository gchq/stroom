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

import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class GetAttachmentDataRequest {

    @JsonProperty
    private final int chatId;
    @JsonProperty
    private final int attachmentId;
    @JsonProperty
    private final PageRequest pageRequest;

    @JsonCreator
    public GetAttachmentDataRequest(@JsonProperty("chatId") final int chatId,
                                    @JsonProperty("attachmentId") final int attachmentId,
                                    @JsonProperty("pageRequest") final PageRequest pageRequest) {
        this.chatId = chatId;
        this.attachmentId = attachmentId;
        this.pageRequest = pageRequest;
    }

    public int getChatId() {
        return chatId;
    }

    public int getAttachmentId() {
        return attachmentId;
    }

    public PageRequest getPageRequest() {
        return pageRequest;
    }
}
