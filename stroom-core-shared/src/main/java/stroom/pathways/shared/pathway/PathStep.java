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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One shape a path takes at a point: a node and the children it ran there in the sequence it ran
 * them, a run of work that did not finish, or several runs that happened at the same time.
 *
 * <p>{@code node} indexes the node list on {@link Paths}, except for these:
 *
 * <ul>
 *   <li>{@link #UNFINISHED} — a run of work one turn of which stopped part way through; its steps are
 *   the one turn that was repeated.
 *   <li>{@link #CONCURRENT} — work that happened at the same time on more than one thread; its steps
 *   are the runs, each a {@link #RUN}. They are held in an order settled by what they are rather than
 *   by which thread got there first, because that is the scheduler's doing and not the path through
 *   the code — two traces that ran the same work on four threads took the same path however the
 *   threads happened to be timed. One run does not mean one thread: runs that came to the same thing
 *   are said once, and turns of a single node taken between several threads are said as one run,
 *   because which thread took which turn is decided by what it reached first.
 *   <li>{@link #RUN} — one of those runs; its steps are what that thread did, in the order it did it.
 * </ul>
 *
 * <p>Work that repeated and did finish is simply said once, with nothing to mark that it happened
 * more than once — how much work there was to do is the workload rather than the path through the
 * code, and a trace that handled one document would otherwise be a different path from one that
 * handled three.
 *
 * <p>Steps index the shape list on {@link Paths}, which holds each distinct shape once, so a subtree
 * that many paths share costs one entry however many of them reach it. A shape with no steps is a
 * node that ran nothing, and that is the only thing it can mean.
 */
@JsonInclude(Include.NON_NULL)
public class PathStep {

    /**
     * The {@code node} of a shape that is a run of work that did not finish, rather than a node the
     * trace reached.
     */
    public static final int UNFINISHED = -1;

    /**
     * The {@code node} of a shape holding runs that happened at the same time as each other.
     */
    public static final int CONCURRENT = -2;

    /**
     * The {@code node} of one run inside a {@link #CONCURRENT} shape.
     */
    public static final int RUN = -3;

    @JsonProperty
    private final int node;

    @JsonProperty
    private final List<Integer> steps;


    @JsonCreator
    public PathStep(@JsonProperty("node") final Integer node,
                     @JsonProperty("steps") final List<Integer> steps) {
        this.node = Objects.requireNonNullElse(node, 0);
        this.steps = steps == null
                ? Collections.emptyList()
                : new ArrayList<>(steps);
    }

    public int getNode() {
        return node;
    }

    /**
     * The children run here, in the order they ran, each a position in the shape list on
     * {@link Paths}.
     */
    public List<Integer> getSteps() {
        return steps;
    }


    /**
     * Whether this is a run that stopped part way through rather than a node the trace reached.
     */
    @JsonIgnore
    public boolean isUnfinished() {
        return node == UNFINISHED;
    }

    /**
     * Whether this holds runs that happened at the same time rather than one after another. Nothing
     * about their order is recorded, so a reader must not follow them as a sequence.
     */
    @JsonIgnore
    public boolean isConcurrent() {
        return node == CONCURRENT;
    }

    /**
     * Whether this is one run inside a {@link #CONCURRENT} shape.
     */
    @JsonIgnore
    public boolean isRun() {
        return node == RUN;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PathStep that = (PathStep) o;
        return node == that.node && Objects.equals(steps, that.steps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node, steps);
    }

    @Override
    public String toString() {
        final String kind = switch (node) {
            case UNFINISHED -> "unfinished";
            case CONCURRENT -> "concurrent";
            case RUN -> "run";
            default -> String.valueOf(node);
        };
        return kind + steps;
    }
}
