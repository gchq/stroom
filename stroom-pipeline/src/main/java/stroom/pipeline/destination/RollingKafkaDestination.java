package stroom.pipeline.destination;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.kafka.StroomKafkaProducer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

public class RollingKafkaDestination extends RollingDestination {
    private static final Logger LOGGER = LoggerFactory.getLogger(RollingKafkaDestination.class);

    private final StroomKafkaProducer stroomKafkaProducer;

    private final String topic;
    private final String partition;

    private StringBuilder data = new StringBuilder();

    public RollingKafkaDestination(final String key,
                                   final long frequency,
                                   final long maxSize,
                                   final long creationTime,
                                   final StroomKafkaProducer stroomKafkaProducer,
                                   final String partition,
                                   final String topic) {
        super(key, frequency, maxSize, creationTime);
        this.stroomKafkaProducer = stroomKafkaProducer;
        this.partition = partition;
        this.topic = topic;

        setOutputStream(new ByteCountOutputStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                data.append((char)b);
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
        final ProducerRecord<String, String> newRecord = new ProducerRecord<>(topic, partition, data.toString());
        try {
            stroomKafkaProducer.send(newRecord, StroomKafkaProducer.FlushMode.FLUSH_ON_SEND, e -> {
                LOGGER.error("Unable to send record to Kafka!", e);
            });
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
