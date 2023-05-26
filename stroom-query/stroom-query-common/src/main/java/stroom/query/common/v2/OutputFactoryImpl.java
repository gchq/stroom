package stroom.query.common.v2;

import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.dashboard.expression.v1.ref.MyByteBufferOutput;
import stroom.dashboard.expression.v1.ref.OutputFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Output;

public class OutputFactoryImpl implements OutputFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OutputFactoryImpl.class);

    private final int maxStringFieldLength;

    public OutputFactoryImpl(final AbstractResultStoreConfig resultStoreConfig) {
        maxStringFieldLength = resultStoreConfig.getMaxStringFieldLength();
    }

    private String truncate(final String value, final ErrorConsumer errorConsumer) {
        if (value.length() > maxStringFieldLength) {
            LOGGER.trace(() -> "Truncating string: " + value);
            final String truncated = value.substring(0, maxStringFieldLength);
            errorConsumer.add(() -> "Truncating string as it is longer than '" +
                    maxStringFieldLength +
                    "' : " +
                    truncated);
            return truncated;
        }
        return value;
    }

    @Override
    public MyByteBufferOutput createOutput(final int bufferSize, final ErrorConsumer errorConsumer) {
        return new MyByteBufferOutput(bufferSize, -1) {
            @Override
            public void writeString(final String value) throws KryoException {
                super.writeString(truncate(value, errorConsumer));
            }
        };
    }

    @Override
    public Output createHashOutput(final int bufferSize, final ErrorConsumer errorConsumer) {
        return new Output(bufferSize, -1) {
            @Override
            public void writeString(final String value) throws KryoException {
                super.writeString(truncate(value, errorConsumer));
            }
        };
    }
}
