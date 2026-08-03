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

package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.histogram.HistogramDb;
import stroom.planb.impl.db.metric.MetricDb;
import stroom.planb.impl.db.rangestate.RangeStateDb;
import stroom.planb.impl.db.session.SessionDb;
import stroom.planb.impl.db.state.StateDb;
import stroom.planb.impl.db.temporalrangestate.TemporalRangeStateDb;
import stroom.planb.impl.db.temporalstate.TemporalStateDb;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.shared.PlanBDocument;

import java.nio.file.Path;

public class PlanBDb {

    /** Opens a queryable store. See the six-arg overload for stores that are only written and merged. */
    public static Db<?, ?> open(final PlanBDocument doc,
                                final Path targetPath,
                                final ByteBuffers byteBuffers,
                                final ByteBufferFactory byteBufferFactory,
                                final boolean readOnly) {
        return open(doc, targetPath, byteBuffers, byteBufferFactory, readOnly, true);
    }

    /**
     * @param queryable whether this env needs the structures that only sorted/filtered queries use.
     *                  Currently only {@code TRACE} stores have any (their secondary sort indexes);
     *                  every other type ignores it. Must be consistent for every open of a given env —
     *                  see {@link stroom.planb.impl.db.trace.TraceDb#create}.
     */
    public static Db<?, ?> open(final PlanBDocument doc,
                                final Path targetPath,
                                final ByteBuffers byteBuffers,
                                final ByteBufferFactory byteBufferFactory,
                                final boolean readOnly,
                                final boolean queryable) {
        switch (doc.getStateType()) {
            case STATE -> {
                return StateDb.create(
                        targetPath,
                        byteBuffers,
                        doc,
                        readOnly);
            }
            case TEMPORAL_STATE -> {
                return TemporalStateDb.create(
                        targetPath,
                        byteBuffers,
                        doc,
                        readOnly);
            }
            case RANGED_STATE -> {
                return RangeStateDb.create(
                        targetPath,
                        byteBuffers,
                        doc,
                        readOnly);
            }
            case TEMPORAL_RANGED_STATE -> {
                return TemporalRangeStateDb.create(
                        targetPath,
                        byteBuffers,
                        doc,
                        readOnly);
            }
            case SESSION -> {
                return SessionDb.create(
                        targetPath,
                        byteBuffers,
                        doc,
                        readOnly);
            }
            case HISTOGRAM -> {
                return HistogramDb.create(
                        targetPath,
                        byteBuffers,
                        doc,
                        readOnly);
            }
            case METRIC -> {
                return MetricDb.create(
                        targetPath,
                        byteBuffers,
                        doc,
                        readOnly);
            }
            case TRACE -> {
                return TraceDb.create(
                        targetPath,
                        byteBuffers,
                        byteBufferFactory,
                        doc,
                        readOnly,
                        queryable);
            }

            default -> throw new RuntimeException("Unexpected Plan B store type: " + doc.getStateType());
        }
    }
}
