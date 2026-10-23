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

package stroom.pathways.shared;

import stroom.pathways.shared.otel.trace.NanoTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * One row of the pathway list: what a learnt pathway is called and when it was last touched, without
 * the model it holds.
 *
 * <p>A pathway keeps every path it has ever seen an operation take, so one can run to megabytes. The
 * list shows a handful of fields, and reading the tree to render them is what made the screen unopenable —
 * so the read stops before it, and {@link #getSizeBytes()} says how much was not read.
 */
@JsonInclude(Include.NON_NULL)
public class PathwaySummary {

    // The names the grid sorts by. Shared so the column the user clicks and the field the reader
    // orders on cannot drift apart.
    public static final String FIELD_NAME = "Root";
    public static final String FIELD_CREATE_TIME = "Create Time";
    public static final String FIELD_UPDATE_TIME = "Update Time";
    public static final String FIELD_LAST_USED_TIME = "Last Used";
    public static final String FIELD_TIMES_USED = "Traces";
    public static final String FIELD_TIMES_UPDATED = "Updates";
    public static final String FIELD_NODES = "Nodes";
    public static final String FIELD_PATHS = "Paths";
    public static final String FIELD_SIZE = "Size";

    @JsonProperty
    private final String name;
    @JsonProperty
    private final NanoTime createTime;
    @JsonProperty
    private final NanoTime updateTime;
    @JsonProperty
    private final NanoTime lastUsedTime;
    @JsonProperty
    private final long timesUsed;
    @JsonProperty
    private final long timesUpdated;
    @JsonProperty
    private final int nodes;
    @JsonProperty
    private final int paths;
    @JsonProperty
    private final long sizeBytes;

    @JsonCreator
    public PathwaySummary(@JsonProperty("name") final String name,
                          @JsonProperty("createTime") final NanoTime createTime,
                          @JsonProperty("updateTime") final NanoTime updateTime,
                          @JsonProperty("lastUsedTime") final NanoTime lastUsedTime,
                          @JsonProperty("timesUsed") final Long timesUsed,
                          @JsonProperty("timesUpdated") final Long timesUpdated,
                          @JsonProperty("nodes") final Integer nodes,
                          @JsonProperty("paths") final Integer paths,
                          @JsonProperty("sizeBytes") final Long sizeBytes) {
        this.name = name;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.lastUsedTime = lastUsedTime;
        this.timesUsed = Objects.requireNonNullElse(timesUsed, 0L);
        this.timesUpdated = Objects.requireNonNullElse(timesUpdated, 0L);
        this.nodes = Objects.requireNonNullElse(nodes, 0);
        this.paths = Objects.requireNonNullElse(paths, 0);
        this.sizeBytes = Objects.requireNonNullElse(sizeBytes, 0L);
    }

    public String getName() {
        return name;
    }

    public NanoTime getCreateTime() {
        return createTime;
    }

    public NanoTime getUpdateTime() {
        return updateTime;
    }

    public NanoTime getLastUsedTime() {
        return lastUsedTime;
    }

    /**
     * How many traces have taken this path since the pathway was first learnt.
     */
    public long getTimesUsed() {
        return timesUsed;
    }

    /**
     * How many traces have taught the model something, not counting the one that created it.
     */
    public long getTimesUpdated() {
        return timesUpdated;
    }

    /**
     * How many nodes the model holds, counting the root. How wide the thing is, against the size,
     * which says how heavy it is — a small model taking a lot of room is carrying large constraint
     * values rather than much structure.
     */
    public int getNodes() {
        return nodes;
    }

    /**
     * How many distinct paths traces have taken. Against the trace count it says whether the ways
     * through this pathway have settled or are still being found.
     */
    public int getPaths() {
        return paths;
    }

    /**
     * How large this pathway is where it is stored, which is also roughly what fetching it costs.
     */
    public long getSizeBytes() {
        return sizeBytes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PathwaySummary that = (PathwaySummary) o;
        return sizeBytes == that.sizeBytes
               && nodes == that.nodes
               && paths == that.paths
               && timesUsed == that.timesUsed
               && timesUpdated == that.timesUpdated
               && Objects.equals(name, that.name)
               && Objects.equals(createTime, that.createTime)
               && Objects.equals(updateTime, that.updateTime)
               && Objects.equals(lastUsedTime, that.lastUsedTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, createTime, updateTime, lastUsedTime, timesUsed, timesUpdated,
                nodes, paths, sizeBytes);
    }

    @Override
    public String toString() {
        return "PathwaySummary{name='" + name + "', sizeBytes=" + sizeBytes + '}';
    }
}
