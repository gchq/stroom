/*
 * Copyright 2025 Crown Copyright
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

package stroom.langchain.api;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ChatMemoryConfig extends AbstractConfig implements IsStroomConfig {

    private static final int DEFAULT_CHAT_MEMORY_TOKEN_LIMIT = 1024;
    private static final StroomDuration DEFAULT_CHAT_MEMORY_TTL = StroomDuration.ofHours(1);

    private final int tokenLimit;
    private final StroomDuration timeToLive;

    public ChatMemoryConfig() {
        tokenLimit = DEFAULT_CHAT_MEMORY_TOKEN_LIMIT;
        timeToLive = DEFAULT_CHAT_MEMORY_TTL;
    }

    @JsonCreator
    public ChatMemoryConfig(@JsonProperty("tokenLimit") final int tokenLimit,
                            @JsonProperty("timeToLive") final StroomDuration timeToLive) {
        this.tokenLimit = tokenLimit;
        this.timeToLive = timeToLive;
    }

    @JsonPropertyDescription("Number of tokens to keep in each chat memory store")
    public int getTokenLimit() {
        return tokenLimit;
    }

    @JsonPropertyDescription("How long a chat memory entry should exist before being expired")
    public StroomDuration getTimeToLive() {
        return timeToLive;
    }

    @Override
    public String toString() {
        return "OpenAIModelConfig{" +
               ", tokenLimit=" + tokenLimit +
               ", timeToLive=" + timeToLive +
               '}';
    }
}
