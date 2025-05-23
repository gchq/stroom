package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.rangestate.RangeStateDb;
import stroom.planb.impl.db.session.SessionDb;
import stroom.planb.impl.db.state.StateDb;
import stroom.planb.impl.db.temporalrangestate.TemporalRangeStateDb;
import stroom.planb.impl.db.temporalstate.TemporalStateDb;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RangeStateSettings;
import stroom.planb.shared.SessionSettings;
import stroom.planb.shared.StateSettings;
import stroom.planb.shared.TemporalRangeStateSettings;
import stroom.planb.shared.TemporalStateSettings;
import stroom.util.shared.NullSafe;

import java.nio.file.Path;

public class PlanBDb {

    public static Db<?, ?> open(final PlanBDoc doc,
                                final Path targetPath,
                                final ByteBuffers byteBuffers,
                                final boolean readOnly) {
        switch (doc.getStateType()) {
            case STATE -> {
                return StateDb.create(
                        targetPath,
                        byteBuffers,
                        NullSafe.getOrElse(doc,
                                d -> (StateSettings) doc.getSettings(),
                                new StateSettings.Builder().build()),
                        readOnly);
            }
            case TEMPORAL_STATE -> {
                return TemporalStateDb.create(
                        targetPath,
                        byteBuffers,
                        NullSafe.getOrElse(doc,
                                d -> (TemporalStateSettings) doc.getSettings(),
                                new TemporalStateSettings.Builder().build()),
                        readOnly);
            }
            case RANGE_STATE -> {
                return RangeStateDb.create(
                        targetPath,
                        byteBuffers,
                        NullSafe.getOrElse(doc,
                                d -> (RangeStateSettings) doc.getSettings(),
                                new RangeStateSettings.Builder().build()),
                        readOnly);
            }
            case TEMPORAL_RANGE_STATE -> {
                return TemporalRangeStateDb.create(
                        targetPath,
                        byteBuffers,
                        NullSafe.getOrElse(doc,
                                d -> (TemporalRangeStateSettings) doc.getSettings(),
                                new TemporalRangeStateSettings.Builder().build()),
                        readOnly);
            }
            case SESSION -> {
                return SessionDb.create(
                        targetPath,
                        byteBuffers,
                        NullSafe.getOrElse(doc,
                                d -> (SessionSettings) doc.getSettings(),
                                new SessionSettings.Builder().build()),
                        readOnly);
            }
            default -> throw new RuntimeException("Unexpected state type: " + doc.getStateType());
        }
    }
}
