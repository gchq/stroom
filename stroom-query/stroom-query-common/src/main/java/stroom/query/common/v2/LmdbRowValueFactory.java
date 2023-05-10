package stroom.query.common.v2;

import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.dashboard.expression.v1.ref.OutputFactory;
import stroom.dashboard.expression.v1.ref.StoredValues;
import stroom.dashboard.expression.v1.ref.ValueReferenceIndex;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;

public class LmdbRowValueFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbRowValueFactory.class);

    private final KeyFactory keyFactory;
    private final ValueReferenceIndex valueReferenceIndex;
    private final OutputFactory outputFactory;
    private final ErrorConsumer errorConsumer;

    public LmdbRowValueFactory(final KeyFactory keyFactory,
                               final ValueReferenceIndex valueReferenceIndex,
                               final OutputFactory outputFactory,
                               final ErrorConsumer errorConsumer) {
        this.keyFactory = keyFactory;
        this.valueReferenceIndex = valueReferenceIndex;
        this.outputFactory = outputFactory;
        this.errorConsumer = errorConsumer;
    }

    public LmdbRowValue create(final Key key,
                               final StoredValues storedValues) {
        final byte[] keyBytes = keyFactory
                .keyToBytes(key, errorConsumer);
        final byte[] storedValueBytes = valueReferenceIndex
                .getBytes(outputFactory, storedValues, errorConsumer);
        final ByteBuffer byteBuffer =
                new LmdbValueBytes(keyBytes, storedValueBytes).toByteBuffer(outputFactory, errorConsumer);
        return new LmdbRowValue(byteBuffer);
    }
}
