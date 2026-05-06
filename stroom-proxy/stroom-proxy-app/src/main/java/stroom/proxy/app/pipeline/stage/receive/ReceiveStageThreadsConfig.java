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

package stroom.proxy.app.pipeline.stage.receive;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

import java.util.Objects;

/**
 * Thread configuration for the receive stage.
 * <p>
 * The receive stage is HTTP-driven rather than queue-driven, so it does not
 * have {@code consumerThreads}. Instead it controls the maximum number of
 * concurrent HTTP receive operations.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class ReceiveStageThreadsConfig extends AbstractConfig implements IsProxyConfig {

    public static final int DEFAULT_MAX_CONCURRENT_RECEIVES = 5;

    private final int maxConcurrentReceives;

    public ReceiveStageThreadsConfig() {
        this(DEFAULT_MAX_CONCURRENT_RECEIVES);
    }

    @JsonCreator
    public ReceiveStageThreadsConfig(
            @JsonProperty("maxConcurrentReceives") final Integer maxConcurrentReceives) {

        this.maxConcurrentReceives = Objects.requireNonNullElse(
                maxConcurrentReceives, DEFAULT_MAX_CONCURRENT_RECEIVES);
    }

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("Maximum concurrent receive requests for the receive stage.")
    public int getMaxConcurrentReceives() {
        return maxConcurrentReceives;
    }
}
