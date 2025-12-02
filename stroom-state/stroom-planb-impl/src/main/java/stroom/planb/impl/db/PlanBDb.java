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
import stroom.planb.shared.PlanBDoc;

import java.nio.file.Path;

public class PlanBDb {

    public static Db<?, ?> open(final PlanBDoc doc,
                                final Path targetPath,
                                final ByteBuffers byteBuffers,
                                final ByteBufferFactory byteBufferFactory,
                                final boolean readOnly) {
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
                        readOnly);
            }

            default -> throw new RuntimeException("Unexpected Plan B store type: " + doc.getStateType());
        }
    }
}
