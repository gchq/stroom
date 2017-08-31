package stroom.connectors.kafka;

import com.google.common.base.Strings;
import org.apache.kafka.clients.producer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.connectors.ConnectorProperties;

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
    private final ConnectorProperties properties;
    private final String bootstrapServers;
    //instance of a kafka producer that will be shared by all threads
    private volatile Producer<String, String> producer = null;
    private volatile Instant timeOfLastFailedInitAttempt = Instant.EPOCH;

    public StroomKafkaProducerImpl(final ConnectorProperties properties) {
        this.properties = properties;
        this.bootstrapServers = (properties != null) ? properties.getProperty(StroomKafkaProducer.BOOTSTRAP_SERVERS_CONFIG) : null;

        if (this.properties != null) {
            if (Strings.isNullOrEmpty(bootstrapServers)) {
                final String msg = String.format(
                        "Stroom is not properly configured to connect to Kafka: %s is required.",
                        StroomKafkaProducer.BOOTSTRAP_SERVERS_CONFIG);
                LOGGER.error(msg);
            }
        } else {
            final String msg = String.format(
                    "Stroom is not properly configured to connect to Kafka: properties containing at least %s are required.",
                    StroomKafkaProducer.BOOTSTRAP_SERVERS_CONFIG);
            LOGGER.error(msg);
        }
    }

    public void send(final StroomKafkaProducerRecord<String, String> stroomRecord,
                     final FlushMode flushMode,
                     final Consumer<Exception> exceptionHandler) {

        //kafka may not have been up on startup so ensure we have a producer instance now
        try {
            initProducer();
        } catch (Exception e) {
            final String msg = String.format("Error initialising kafka producer to %s, (%s)",
                    this.bootstrapServers,
                    e.getMessage());
            LOGGER.error(msg);
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
                final String msg = String.format("Error initialising kafka producer to %s, (%s)",
                        this.bootstrapServers,
                        e.getMessage());
                LOGGER.error(msg);
                exceptionHandler.accept(e);
            }
        } else {
            final String msg = String.format("Kafka producer is currently not initialised, " +
                    "it may be down or the connection details incorrect, bootstrapServers: [%s]",
                    this.bootstrapServers);
            exceptionHandler.accept(new IOException(msg));
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

                    properties.copyProp(props, ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, null);
                    properties.copyProp(props, ProducerConfig.ACKS_CONFIG, "all");

                    properties.copyProp(props, ProducerConfig.RETRIES_CONFIG, 0);
                    properties.copyProp(props, ProducerConfig.BATCH_SIZE_CONFIG, 16384);
                    properties.copyProp(props, ProducerConfig.LINGER_MS_CONFIG, 1);
                    properties.copyProp(props, ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

                    properties.copyProp(props, ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                            org.apache.kafka.common.serialization.StringSerializer.class);
                    // TODO We will probably want to send Avro'd bytes (or something similar) at some point, so the serializer will need to change.
                    properties.copyProp(props, ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                            org.apache.kafka.common.serialization.StringSerializer.class);

                    try {
                        producer = new KafkaProducer<>(props);
                    } catch (Exception e) {
                        LOGGER.error("Error initialising kafka producer for {}", bootstrapServers, e);
                        throw e;
                    }
                    LOGGER.info("Kafka Producer successfully created");
                    LOGGER.info(String.format("With Properties: %s", props));
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
