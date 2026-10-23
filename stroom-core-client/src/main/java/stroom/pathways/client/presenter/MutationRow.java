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

package stroom.pathways.client.presenter;

import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.PathwayMutation;
import stroom.util.shared.Expander;
import stroom.util.shared.NullSafe;
import stroom.util.shared.TreeRow;

import java.util.Objects;

/**
 * One line of the mutation list: either a trace, or one change that trace made.
 *
 * <p>A single trace can teach a model fifty things at once, all stamped with the same time, which
 * buries everything around it. So the list holds a row per trace that opens to show what it changed.
 *
 * <p>This is a view of the history rather than part of it — {@link PathwayMutation} is shared with the
 * server and says nothing about being drawn.
 */
class MutationRow implements TreeRow {

    private final PathwayMutation mutation;
    private final String traceId;
    private final String group;
    private final NanoTime time;
    private final long sequence;
    private final Expander expander;

    private MutationRow(final PathwayMutation mutation,
                        final String traceId,
                        final String group,
                        final NanoTime time,
                        final long sequence,
                        final Expander expander) {
        this.mutation = mutation;
        this.traceId = traceId;
        this.group = group;
        this.time = time;
        this.sequence = sequence;
        this.expander = expander;
    }

    /**
     * @param sequence the last change the trace made, so selecting the trace shows the model as the
     *                 trace left it rather than as it stood partway through.
     */
    static MutationRow trace(final PathwayMutation last,
                             final NanoTime time,
                             final boolean expanded) {
        return new MutationRow(null, last.getTraceId(), groupOf(last), time, last.getSequence(),
                new Expander(0, expanded, false));
    }

    static MutationRow change(final PathwayMutation mutation) {
        return new MutationRow(mutation, mutation.getTraceId(), groupOf(mutation), mutation.getTime(),
                mutation.getSequence(), new Expander(1, false, true));
    }

    /**
     * What ties a change to the row it sits under.
     *
     * <p>A trace writes everything it taught the model in one go, so its id names the group. The
     * narrowing has no trace to name, and naming none would put every change it has ever made under
     * one row: opening one run would open them all, and picking one out would pick out the lot. Every
     * change a single run makes to a pathway is stamped with the same instant, so that names it
     * instead.
     */
    static String groupOf(final PathwayMutation mutation) {
        return mutation.getTraceId() != null
                ? mutation.getTraceId()
                : "narrowed:" + NullSafe.get(mutation.getTime(), NanoTime::toEpochNanos);
    }

    /**
     * The change this row shows, or null where the row is the trace that made them.
     */
    PathwayMutation getMutation() {
        return mutation;
    }

    boolean isTrace() {
        return mutation == null;
    }

    String getTraceId() {
        return traceId;
    }

    String getGroup() {
        return group;
    }

    NanoTime getTime() {
        return time;
    }

    /**
     * The point in the history this row winds the model back to.
     */
    long getSequence() {
        return sequence;
    }

    @Override
    public Expander getExpander() {
        return expander;
    }

    // Rows are rebuilt every time the list is drawn, so what marks two of them as the same row is what
    // they stand for, not the object. Without this a trace would close again as soon as it was opened.
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MutationRow row = (MutationRow) o;
        return sequence == row.sequence && isTrace() == row.isTrace();
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequence, isTrace());
    }
}
