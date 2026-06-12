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

package stroom.ui.config.shared;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class NodeMonitoringConfig extends AbstractConfig implements IsStroomConfig {

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("The threshold in milliseconds, above which a node ping response time is " +
            "considered to have a severity of warning and the ping bar will be coloured accordingly. " +
            "Must be lest then or equal to pingMaxThreshold.")
    private final int pingWarnThreshold;

    @Min(1)
    @JsonProperty
    @JsonPropertyDescription("The maximum number of milliseconds that the node monitoring ping bar " +
            "can display. Above this value the ping bar will be displayed in a different colour to " +
            "indicate it has exceeded the maximum threshold. This value should be greater than or equal " +
            "to pingWarnThreshold")
    private final int pingMaxThreshold;

    public NodeMonitoringConfig() {
        pingWarnThreshold = 100;
        pingMaxThreshold = 500;
    }

    @JsonCreator
    public NodeMonitoringConfig(@JsonProperty("pingWarnThreshold") final int pingWarnThreshold,
                                @JsonProperty("pingMaxThreshold") final int pingMaxThreshold) {
        this.pingWarnThreshold = pingWarnThreshold;
        this.pingMaxThreshold = pingMaxThreshold;
    }

    public int getPingWarnThreshold() {
        return pingWarnThreshold;
    }

    public int getPingMaxThreshold() {
        return pingMaxThreshold;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NodeMonitoringConfig that = (NodeMonitoringConfig) o;
        return pingWarnThreshold == that.pingWarnThreshold && pingMaxThreshold == that.pingMaxThreshold;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pingWarnThreshold, pingMaxThreshold);
    }

    @Override
    public String toString() {
        return "NodeMonitoringConfig{" +
                "pingWarnThreshold=" + pingWarnThreshold +
                ", pingMaxThreshold=" + pingMaxThreshold +
                '}';
    }
}
