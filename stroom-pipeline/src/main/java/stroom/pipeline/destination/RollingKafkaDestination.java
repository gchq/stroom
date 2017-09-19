package stroom.pipeline.destination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.connectors.kafka.StroomKafkaProducer;
import stroom.connectors.kafka.StroomKafkaProducerRecord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

public class RollingKafkaDestination extends RollingDestination {
    private static final Logger LOGGER = LoggerFactory.getLogger(RollingKafkaDestination.class);

    private final StroomKafkaProducer stroomKafkaProducer;

    private final String topic;
    private final String recordKey;
    private final boolean flushOnSend;

    private ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    public RollingKafkaDestination(final String key,
                                   final long frequency,
                                   final long maxSize,
                                   final long creationTime,
                                   final StroomKafkaProducer stroomKafkaProducer,
                                   final String recordKey,
                                   final String topic,
                                   final boolean flushOnSend) {
        super(key, frequency, maxSize, creationTime);
        this.stroomKafkaProducer = stroomKafkaProducer;
        this.recordKey = recordKey;
        this.topic = topic;
        this.flushOnSend = flushOnSend;

        setOutputStream(new ByteCountOutputStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                byteArrayOutputStream.write(b);
            }
        }));
    }

    @Override
    void onHeaderWritten(final ByteCountOutputStream outputStream,
                         final Consumer<Throwable> exceptionConsumer) {
        // Nothing to do here
    }

    @Override
    void onFooterWritten(final Consumer<Throwable> exceptionConsumer) {
        byte[] msgValue = byteArrayOutputStream.toByteArray();

        final StroomKafkaProducerRecord<String, byte[]> newRecord =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic(topic)
                        .key(recordKey)
                        .value(msgValue)
                        .build();
        try {
            stroomKafkaProducer.send(newRecord, flushOnSend, e ->
                LOGGER.error("Unable to send record to Kafka!", e)
            );
        } catch (RuntimeException e) {
            exceptionConsumer.accept(wrapRollException(e));
        }
    }

    private Throwable wrapRollException(final Throwable e) {
        final StringBuilder sb = new StringBuilder();
        sb.append("Unable to send record to Kafka '");
        sb.append(topic);
        sb.append("' - ");
        sb.append(e.getMessage());

        LOGGER.error(sb.toString(), e);

        return new IOException(sb.toString(), e);
    }
}
