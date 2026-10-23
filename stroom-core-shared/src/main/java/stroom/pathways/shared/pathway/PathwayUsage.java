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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * How much every node of a pathway had been used at one point in its history.
 *
 * <p>How often a node has been used, and when it last was, are what the model holds now — nothing in
 * the stored changes says either, because a trace that teaches the model nothing still uses it. So a
 * reading is taken and kept whenever a trace does change the model, which is the only point a replay
 * can be wound back to.
 *
 * <p>One of these per trace that changed the model, not one per change: a trace that moved nine
 * constraints leaves nine changes and a single reading.
 */
@JsonInclude(Include.NON_NULL)
public class PathwayUsage {

    @JsonProperty
    private final long sequence;
    @JsonProperty
    private final List<NodeUsage> nodes;

    @JsonCreator
    public PathwayUsage(@JsonProperty("sequence") final Long sequence,
                        @JsonProperty("nodes") final List<NodeUsage> nodes) {
        this.sequence = Objects.requireNonNullElse(sequence, 0L);
        this.nodes = nodes;
    }

    /**
     * The last change made by the trace this was taken for, so a replay wound back to any change can
     * use the newest reading at or before it.
     */
    public long getSequence() {
        return sequence;
    }

    public List<NodeUsage> getNodes() {
        return nodes;
    }

    @Override
    public String toString() {
        return "PathwayUsage{at " + sequence + ", " + (nodes == null
                ? 0
                : nodes.size()) + " nodes}";
    }
}
