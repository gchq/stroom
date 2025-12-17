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

package stroom.config.app;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class SessionConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_MAX_INACTIVE_INTERVAL = "maxInactiveInterval";

    public static final StroomDuration DEFAULT_MAX_INACTIVE_INTERVAL = StroomDuration.ofDays(7);

    private final StroomDuration maxInactiveInterval;

    public SessionConfig() {
        this.maxInactiveInterval = DEFAULT_MAX_INACTIVE_INTERVAL;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public SessionConfig(
            @JsonProperty(PROP_NAME_MAX_INACTIVE_INTERVAL) final StroomDuration maxInactiveInterval) {
        this.maxInactiveInterval = maxInactiveInterval;
    }

    @RequiresRestart(RestartScope.UI)
    @JsonProperty(PROP_NAME_MAX_INACTIVE_INTERVAL)
    @JsonPropertyDescription("The maximum time interval between the last access of a HTTP session and " +
                             "it being considered expired. Set to null for sessions that never expire, " +
                             "however this will causes sessions to be held and build up in memory indefinitely, " +
                             "so is best avoided.")
    public StroomDuration getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    @Override
    public String toString() {
        return "SessionConfig{" +
               "maxInactiveInterval=" + maxInactiveInterval +
               '}';
    }
}
