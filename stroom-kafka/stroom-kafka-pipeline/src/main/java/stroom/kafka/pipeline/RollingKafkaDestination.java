package stroom.kafka.pipeline;

import org.apache.kafka.clients.producer.ProducerRecord;
import stroom.pipeline.destination.RollingDestination;
import stroom.util.io.ByteCountOutputStream;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.scheduler.SimpleCron;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.function.Consumer;

public class RollingKafkaDestination extends RollingDestination {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RollingKafkaDestination.class);

    private final org.apache.kafka.clients.producer.KafkaProducer kafkaProducer;

    private final String topic;
    private final String recordKey;
    private final boolean flushOnSend;

    private ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    private final Consumer<Throwable> logOnlyExceptionHandler;

    public RollingKafkaDestination(final String key,
                                   final Long frequency,
                                   final SimpleCron schedule,
                                   final long rollSize,
                                   final long creationTime,
                                   final org.apache.kafka.clients.producer.KafkaProducer kafkaProducer,
                                   final String recordKey,
                                   final String topic,
                                   final boolean flushOnSend) {
        super(key, frequency, schedule, rollSize, creationTime);
        this.kafkaProducer = kafkaProducer;
        this.recordKey = recordKey;
        this.topic = topic;
        this.flushOnSend = flushOnSend;
        this.logOnlyExceptionHandler = KafkaProducer.createLogOnlyExceptionHandler(
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
    protected void afterRoll(final Consumer<Throwable> exceptionConsumer) {
        byte[] msgValue = byteArrayOutputStream.toByteArray();

        final ProducerRecord<String, String> record = new ProducerRecord(topic,
                recordKey, msgValue);

        try {
            kafkaProducer.send(record);
            if (flushOnSend) {
                throw new UnsupportedOperationException("Flush on send is not available in this version of Stroom");
            }
        } catch (RuntimeException e) {
            exceptionConsumer.accept(wrapRollException(e));
        }

    }

    private Throwable wrapRollException(final Throwable e) {
        logOnlyExceptionHandler.accept(e);
        final String shortMessage = "Unable to send record to Kafka with topic/key " + topic + "/" + recordKey;
        final String longMessage = shortMessage +
                ", due to: " +
                e.getMessage() +
                " (enable DEBUG for full stacktrace)";
        LOGGER.debug(() -> shortMessage, e);
        LOGGER.error(() -> longMessage);
        return new IOException(longMessage);
    }
}
