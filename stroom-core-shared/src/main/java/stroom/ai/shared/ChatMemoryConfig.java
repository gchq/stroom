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

package stroom.ai.shared;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class ChatMemoryConfig extends AbstractConfig implements IsStroomConfig {

    public static final int DEFAULT_CHAT_MEMORY_TOKEN_LIMIT = 30000;
    public static final SimpleDuration DEFAULT_CHAT_MEMORY_TTL = SimpleDuration
            .builder()
            .time(1)
            .timeUnit(TimeUnit.HOURS)
            .build();


    public static final String PROP_NAME_TOKEN_LIMIT = "tokenLimit";
    public static final String PROP_NAME_TIME_TO_LIVE = "timeToLive";

    @JsonProperty(PROP_NAME_TOKEN_LIMIT)
    private final int tokenLimit;
    @JsonProperty(PROP_NAME_TIME_TO_LIVE)
    private final SimpleDuration timeToLive;

    public ChatMemoryConfig() {
        tokenLimit = DEFAULT_CHAT_MEMORY_TOKEN_LIMIT;
        timeToLive = DEFAULT_CHAT_MEMORY_TTL;
    }

    @JsonCreator
    public ChatMemoryConfig(@JsonProperty(PROP_NAME_TOKEN_LIMIT) final int tokenLimit,
                            @JsonProperty(PROP_NAME_TIME_TO_LIVE) final SimpleDuration timeToLive) {
        this.tokenLimit = tokenLimit;
        this.timeToLive = timeToLive;
    }

    @JsonPropertyDescription("Number of tokens to keep in each chat memory store")
    public int getTokenLimit() {
        return tokenLimit;
    }

    @JsonPropertyDescription("How long a chat memory entry should exist before being expired")
    public SimpleDuration getTimeToLive() {
        return timeToLive;
    }

    @Override
    public String toString() {
        return "OpenAIModelConfig{" +
               ", tokenLimit=" + tokenLimit +
               ", timeToLive=" + timeToLive +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<ChatMemoryConfig, Builder> {

        private int tokenLimit = DEFAULT_CHAT_MEMORY_TOKEN_LIMIT;
        private SimpleDuration timeToLive = DEFAULT_CHAT_MEMORY_TTL;

        private Builder() {
        }

        private Builder(final ChatMemoryConfig chatMemoryConfig) {
            tokenLimit = chatMemoryConfig.tokenLimit;
            timeToLive = chatMemoryConfig.timeToLive;
        }

        public Builder tokenLimit(final int tokenLimit) {
            this.tokenLimit = tokenLimit;
            return self();
        }

        public Builder timeToLive(final SimpleDuration timeToLive) {
            this.timeToLive = timeToLive;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public ChatMemoryConfig build() {
            return new ChatMemoryConfig(tokenLimit, timeToLive);
        }
    }
}
