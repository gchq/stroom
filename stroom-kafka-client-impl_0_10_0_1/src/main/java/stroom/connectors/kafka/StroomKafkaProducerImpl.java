package stroom.connectors.kafka;

import com.google.common.base.Strings;
import org.apache.kafka.clients.producer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * A singleton responsible for sending records to Kafka.
 * <p>
 * Requires configuration:
 * <<code>
 * stroom.kafka.bootstrap.servers=<host1>:<port>,<host2>:<port>,etc
 * </code>
 */
class StroomKafkaProducerImpl implements StroomKafkaProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomKafkaProducerImpl.class);

    private static final int TIME_BETWEEN_INIT_ATTEMPS_MS = 30_000;
    private final String bootstrapServers;
    //instance of a kafka producer that will be shared by all threads
    private volatile Producer<String, String> producer = null;
    private volatile Instant timeOfLastFailedInitAttempt = Instant.EPOCH;

    public StroomKafkaProducerImpl(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
        if (Strings.isNullOrEmpty(bootstrapServers)) {
            LOGGER.error("Stroom is not properly configured to connect to Kafka: 'stroom.kafka.bootstrap.servers' is required.");
        }
    }

    public void send(final StroomKafkaProducerRecord<String, String> stroomRecord,
                     final FlushMode flushMode,
                     final Consumer<Exception> exceptionHandler) {

        //kafka may not have been up on startup so ensure we have a producer instance now
        try {
            initProducer();
        } catch (Exception e) {
            LOGGER.error("Error initialising kafka producer to " + bootstrapServers + ", (" + e.getMessage() + ")");
            exceptionHandler.accept(e);
            return;
        }

        if (producer != null) {
            try {
                final ProducerRecord<String, String> record =
                        new ProducerRecord<>(
                                stroomRecord.topic(),
                                stroomRecord.partition(),
                                stroomRecord.timestamp(),
                                stroomRecord.key(),
                                stroomRecord.value());
                Future<RecordMetadata> future = producer.send(record, (recordMetadata, exception) -> {

                    if (exception != null) {
                        exceptionHandler.accept(exception);
                    }
                    LOGGER.trace("Record sent to Kafka");
                });

                if (flushMode.equals(FlushMode.FLUSH_ON_SEND)) {
                    future.get();
                }

            } catch (Exception e) {
                LOGGER.error("Error initialising kafka producer to " + bootstrapServers + ", (" + e.getMessage() + ")");
                exceptionHandler.accept(e);
            }
        } else {
            exceptionHandler.accept(new IOException(String.format("Kafka producer is currently not initialised, " +
                    "it may be down or the connection details incorrect, bootstrapServers: [%s]", bootstrapServers)));
        }
    }

    public void flush() {
        if (producer != null) {
            producer.flush();
        }
    }

    /**
     * Lazy initialisation of the kafka producer
     */
    private void initProducer() {
        if (producer == null && isOkToInitNow(bootstrapServers)) {
            synchronized (this) {
                if (producer == null && isOkToInitNow(bootstrapServers)) {
                    LOGGER.info("Initialising kafka producer for {}", bootstrapServers);
                    Properties props = new Properties();
                    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                    props.put(ProducerConfig.ACKS_CONFIG, "all");
                    props.put(ProducerConfig.RETRIES_CONFIG, 0);
                    props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
                    props.put(ProducerConfig.LINGER_MS_CONFIG, 1);
                    props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

                    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class);
                    // TODO We will probably want to send Avro'd bytes (or something similar) at some point, so the serializer will need to change.
                    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringSerializer.class);

                    try {
                        producer = new KafkaProducer<>(props);
                    } catch (Exception e) {
                        LOGGER.error("Error initialising kafka producer for {}", bootstrapServers, e);
                        throw e;
                    }
                    LOGGER.info("Kafka Producer successfully created");
                }
            }
        }
    }

    /**
     * A check to prevent many process trying to repeatedly init a producer. This means init will only be attempted
     * every 30s to prevent the logs being filled up if kafka is down. It will never try to init if there is no
     * bootstrapServers value
     */
    private boolean isOkToInitNow(final String bootstrapServers) {
        return !Strings.isNullOrEmpty(bootstrapServers) &&
                Instant.now().isAfter(timeOfLastFailedInitAttempt.plusMillis(TIME_BETWEEN_INIT_ATTEMPS_MS));
    }

    public void shutdown() {
        if (producer != null) {
            try {
                LOGGER.info("Closing down Kafka Producer");
                producer.close();
            } catch (Exception e) {
                LOGGER.error("Error closing kafka producer", e);
            }
        }
    }
}
