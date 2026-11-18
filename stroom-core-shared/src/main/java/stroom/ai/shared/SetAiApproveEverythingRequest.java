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
 * Turns 'approve everything' on or off for a chat. Only allowed if an administrator has enabled it, and even then
 * it only covers tools on trusted servers that are not destructive or open-world, and never while the user is
 * away.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class SetAiApproveEverythingRequest {

    @JsonProperty
    private final AiChat aiChat;
    @JsonProperty
    private final boolean enabled;

    @JsonCreator
    public SetAiApproveEverythingRequest(@JsonProperty("aiChat") final AiChat aiChat,
                                         @JsonProperty("enabled") final Boolean enabled) {
        this.aiChat = aiChat;
        this.enabled = Objects.requireNonNullElse(enabled, false);
    }

    public AiChat getAiChat() {
        return aiChat;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SetAiApproveEverythingRequest that = (SetAiApproveEverythingRequest) o;
        return enabled == that.enabled && Objects.equals(aiChat, that.aiChat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aiChat, enabled);
    }
}
