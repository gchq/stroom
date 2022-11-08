package stroom.query.common.v2;

import stroom.util.io.ByteSizeUnit;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;

import java.io.OutputStream;

public class OutputFactory {

    private static final int MIN_KEY_SIZE = (int) ByteSizeUnit.BYTE.longBytes(10);
    private static final int MIN_VALUE_SIZE = (int) ByteSizeUnit.KIBIBYTE.longBytes(1);
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OutputFactory.class);

    private final int minPayloadSize;
    private final int maxStringFieldLength;
    private final ErrorConsumer errorConsumer;

    public OutputFactory(final ResultStoreConfig resultStoreConfig,
                         final ErrorConsumer errorConsumer) {
        this.minPayloadSize = (int) resultStoreConfig.getMinPayloadSize().getBytes();
        maxStringFieldLength = resultStoreConfig.getMaxStringFieldLength();
        this.errorConsumer = errorConsumer;
    }

    private String truncate(final String value) {
        if (value.length() > maxStringFieldLength) {
            LOGGER.debug(() -> "Truncating string: " + value);
            final String truncated = value.substring(0, maxStringFieldLength);
            errorConsumer.add(new RuntimeException("Truncating string as it is longer than '" +
                    maxStringFieldLength +
                    "' : " +
                    truncated));
            return truncated;
        }
        return value;
    }

    public Output createValueOutput() {
        return new Output(MIN_VALUE_SIZE, -1) {
            @Override
            public void writeString(final String value) throws KryoException {
                super.writeString(truncate(value));
            }
        };
    }

    public Output createKeyOutput() {
        return new Output(MIN_KEY_SIZE, -1) {
            @Override
            public void writeString(final String value) throws KryoException {
                super.writeString(truncate(value));
            }
        };
    }

    public PayloadOutput createPayloadOutput() {
        return new PayloadOutput(minPayloadSize);
    }

    public UnsafeByteBufferOutput createByteBufferOutput(final int bufferSize) {
        return new UnsafeByteBufferOutput(bufferSize, -1) {
            @Override
            public void writeString(final String value) throws KryoException {
                super.writeString(truncate(value));
            }
        };
    }
}
