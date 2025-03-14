package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.shared.PlanBDoc;

import java.nio.file.Path;

public class PlanBDb {

    public static AbstractDb<?, ?> open(final PlanBDoc doc,
                                        final Path targetPath,
                                        final ByteBufferFactory byteBufferFactory,
                                        final boolean readOnly) {
        switch (doc.getStateType()) {
            case STATE -> {
                return StateDb.create(
                        targetPath,
                        byteBufferFactory,
                        doc,
                        readOnly);
            }
            case TEMPORAL_STATE -> {
                return TemporalStateDb.create(
                        targetPath,
                        byteBufferFactory,
                        doc,
                        readOnly);
            }
            case RANGED_STATE -> {
                return RangedStateDb.create(
                        targetPath,
                        byteBufferFactory,
                        doc,
                        readOnly);
            }
            case TEMPORAL_RANGED_STATE -> {
                return TemporalRangedStateDb.create(
                        targetPath,
                        byteBufferFactory,
                        doc,
                        readOnly);
            }
            case SESSION -> {
                return SessionDb.create(
                        targetPath,
                        byteBufferFactory,
                        doc,
                        readOnly);
            }
            default -> throw new RuntimeException("Unexpected state type: " + doc.getStateType());
        }
    }
}
