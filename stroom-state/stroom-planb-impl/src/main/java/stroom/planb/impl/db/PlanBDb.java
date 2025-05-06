package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.state.StateDb;
import stroom.planb.shared.PlanBDoc;

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
                return RangedStateDb.create(
                        targetPath,
                        byteBuffers,
                        doc,
                        readOnly);
            }
            case TEMPORAL_RANGED_STATE -> {
                return TemporalRangedStateDb.create(
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
            default -> throw new RuntimeException("Unexpected state type: " + doc.getStateType());
        }
    }
}
