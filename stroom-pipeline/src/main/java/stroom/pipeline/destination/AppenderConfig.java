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

package stroom.pipeline.destination;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class AppenderConfig extends AbstractConfig implements IsStroomConfig {

    private static final int DEFAULT_MAX_ACTIVE_DESTINATIONS = 100;

    private final int maxActiveDestinations;

    public AppenderConfig() {
        maxActiveDestinations = DEFAULT_MAX_ACTIVE_DESTINATIONS;
    }

    @JsonCreator
    public AppenderConfig(@JsonProperty("maxActiveDestinations") final int maxActiveDestinations) {
        this.maxActiveDestinations = maxActiveDestinations;
    }

    @JsonPropertyDescription("The maximum number active destinations that Stroom will allow rolling appenders to be " +
            "writing to at any one time.")
    public int getMaxActiveDestinations() {
        return maxActiveDestinations;
    }

    @Override
    public String toString() {
        return "AppenderConfig{" +
                "maxActiveDestinations=" + maxActiveDestinations +
                '}';
    }
}
