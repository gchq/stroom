package stroom.pipeline.destination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.connectors.kafka.StroomKafkaProducer;
import stroom.connectors.kafka.StroomKafkaProducerRecord;
import stroom.util.io.ByteCountOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.function.Consumer;

public class RollingKafkaDestination extends RollingDestination {
    private static final Logger LOGGER = LoggerFactory.getLogger(RollingKafkaDestination.class);

    private final StroomKafkaProducer stroomKafkaProducer;

    private final String topic;
    private final String recordKey;
    private final boolean flushOnSend;

    private ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    private final Consumer<Throwable> logOnlyExceptionHandler;

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
        this.logOnlyExceptionHandler = StroomKafkaProducer.createLogOnlyExceptionHandler(
                LOGGER,
                topic,
                key);

        setOutputStream(new ByteCountOutputStream(new OutputStream() {
            @Override
            public void write(int b) {
                byteArrayOutputStream.write(b);
            }
        }));
    }

    @Override
    void afterRoll(final Consumer<Throwable> exceptionConsumer) {
        byte[] msgValue = byteArrayOutputStream.toByteArray();

        final StroomKafkaProducerRecord<String, byte[]> newRecord =
                new StroomKafkaProducerRecord.Builder<String, byte[]>()
                        .topic(topic)
                        .key(recordKey)
                        .value(msgValue)
                        .build();
        try {
            if (flushOnSend) {
                stroomKafkaProducer.sendSync(Collections.singletonList(newRecord));
            } else {
                stroomKafkaProducer.sendAsync(newRecord, logOnlyExceptionHandler);
            }
        } catch (RuntimeException e) {
            exceptionConsumer.accept(wrapRollException(e));
        }
    }

    private Throwable wrapRollException(final Throwable e) {
        logOnlyExceptionHandler.accept(e);
        LOGGER.debug("Unable to send record to Kafka with topic/key %s/%s", topic, recordKey, e);
        String msg = String.format(
                "Unable to send record to Kafka with topic/key %s/%s, due to: %s (enable DEBUG for full stacktrace)",
                topic,
                recordKey,
                e.getMessage());
        LOGGER.error(msg);
        return new IOException(msg);
    }
}
