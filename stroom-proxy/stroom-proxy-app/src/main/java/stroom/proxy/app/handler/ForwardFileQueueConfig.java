/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.proxy.app.handler;

import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Subclass it so we can have different defaults between file/http
 */
@JsonPropertyOrder(alphabetic = true)
public class ForwardFileQueueConfig extends ForwardQueueConfig {

    private static final boolean DEFAULT_QUEUE_AND_RETRY_ENABLED = false;

    public ForwardFileQueueConfig() {
        super(DEFAULT_QUEUE_AND_RETRY_ENABLED,
                DEFAULT_FORWARD_DELAY,
                DEFAULT_RETRY_DELAY,
                DEFAULT_RETRY_GROWTH_FACTOR,
                DEFAULT_MAX_RETRY_DELAY,
                DEFAULT_MAX_RETRY_AGE,
                PathTemplateConfig.DEFAULT,
                DEFAULT_FORWARD_THREAD_COUNT,
                DEFAULT_FORWARD_RETRY_THREAD_COUNT,
                DEFAULT_LIVENESS_CHECK_INTERVAL);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ForwardFileQueueConfig(
            @JsonProperty("queueAndRetryEnabled") final Boolean queueAndRetryEnabled,
            @JsonProperty("forwardDelay") final StroomDuration forwardDelay,
            @JsonProperty("retryDelay") final StroomDuration retryDelay,
            @JsonProperty("retryDelayGrowthFactor") final Double retryDelayGrowthFactor,
            @JsonProperty("maxRetryDelay") final StroomDuration maxRetryDelay,
            @JsonProperty("maxRetryAge") final StroomDuration maxRetryAge,
            @JsonProperty(PROP_NAME_ERROR_SUB_PATH_TEMPLATE) final PathTemplateConfig errorSubPathTemplate,
            @JsonProperty("forwardThreadCount") final Integer forwardThreadCount,
            @JsonProperty("forwardRetryThreadCount") final Integer forwardRetryThreadCount,
            @JsonProperty("livenessCheckInterval") final StroomDuration livenessCheckInterval) {

        // Use a different default to
        super(Objects.requireNonNullElse(queueAndRetryEnabled, DEFAULT_QUEUE_AND_RETRY_ENABLED),
                forwardDelay,
                retryDelay,
                retryDelayGrowthFactor,
                maxRetryDelay,
                maxRetryAge,
                errorSubPathTemplate,
                forwardThreadCount,
                forwardRetryThreadCount,
                livenessCheckInterval);
    }
}
