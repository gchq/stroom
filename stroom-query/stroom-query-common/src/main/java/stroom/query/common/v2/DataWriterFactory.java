package stroom.query.common.v2;

import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.query.language.functions.ref.KryoDataWriter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import com.esotericsoftware.kryo.io.Output;

public class DataWriterFactory {

    private final ErrorConsumer errorConsumer;
    private final int maxStringFieldLength;


    public DataWriterFactory(final ErrorConsumer errorConsumer,
                             final int maxStringFieldLength) {
        this.errorConsumer = errorConsumer;
        this.maxStringFieldLength = maxStringFieldLength;
    }

    public KryoDataWriter create(final Output output) {
        return new StringTruncatingKryoDataWriter(output, maxStringFieldLength, errorConsumer);
    }

    private static class StringTruncatingKryoDataWriter extends KryoDataWriter {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(KryoDataWriter.class);

        private static final int SAMPLE_SIZE = 100;
        private final int maxStringFieldLength;
        private final ErrorConsumer errorConsumer;

        public StringTruncatingKryoDataWriter(final Output output,
                                              final int maxStringFieldLength,
                                              final ErrorConsumer errorConsumer) {
            super(output);
            this.maxStringFieldLength = maxStringFieldLength;
            this.errorConsumer = errorConsumer;
        }

        @Override
        public void writeString(final String value) {
            super.writeString(truncate(value));
        }

        private String truncate(final String value) {
            if (value.length() > maxStringFieldLength) {
                LOGGER.trace(() -> "Truncating string: " + value);
                final String truncated = value.substring(0, maxStringFieldLength);
                errorConsumer.add(Severity.WARNING, () -> {
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
    }
}
