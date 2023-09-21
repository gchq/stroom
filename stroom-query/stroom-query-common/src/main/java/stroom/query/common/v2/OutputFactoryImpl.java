package stroom.query.common.v2;

import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.query.language.functions.ref.MyByteBufferOutput;
import stroom.query.language.functions.ref.OutputFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Output;

public class OutputFactoryImpl implements OutputFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OutputFactoryImpl.class);

    private static final int SAMPLE_SIZE = 100;

    private final int maxStringFieldLength;

    public OutputFactoryImpl(final AbstractResultStoreConfig resultStoreConfig) {
        maxStringFieldLength = resultStoreConfig.getMaxStringFieldLength();
    }

    private String truncate(final String value, final ErrorConsumer errorConsumer) {
        if (value.length() > maxStringFieldLength) {
            LOGGER.trace(() -> "Truncating string: " + value);
            final String truncated = value.substring(0, maxStringFieldLength);
            errorConsumer.add(() -> {
                String sample = truncated;
                if (sample.length() > SAMPLE_SIZE) {
                    sample = sample.substring(0, SAMPLE_SIZE);
                }

                return "Truncating string to " +
                        maxStringFieldLength +
                        " characters: " +
                        sample;
            });
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
