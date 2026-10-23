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

package stroom.pathways.shared.pathway;

import stroom.pathways.shared.otel.trace.NanoTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * How much one node had been used at a point in a pathway's history.
 */
@JsonInclude(Include.NON_NULL)
public class NodeUsage {

    @JsonProperty
    private final String nodeUuid;
    @JsonProperty
    private final long timesUsed;
    @JsonProperty
    private final NanoTime lastUsedTime;

    @JsonCreator
    public NodeUsage(@JsonProperty("nodeUuid") final String nodeUuid,
                     @JsonProperty("timesUsed") final Long timesUsed,
                     @JsonProperty("lastUsedTime") final NanoTime lastUsedTime) {
        this.nodeUuid = nodeUuid;
        this.timesUsed = Objects.requireNonNullElse(timesUsed, 0L);
        this.lastUsedTime = lastUsedTime;
    }

    public String getNodeUuid() {
        return nodeUuid;
    }

    public long getTimesUsed() {
        return timesUsed;
    }

    public NanoTime getLastUsedTime() {
        return lastUsedTime;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NodeUsage that = (NodeUsage) o;
        return timesUsed == that.timesUsed
               && Objects.equals(nodeUuid, that.nodeUuid)
               && Objects.equals(lastUsedTime, that.lastUsedTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeUuid, timesUsed, lastUsedTime);
    }

    @Override
    public String toString() {
        return "NodeUsage{" + nodeUuid + " used " + timesUsed + " times, last " + lastUsedTime + '}';
    }
}
