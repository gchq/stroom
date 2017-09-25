package stroom.connectors.kafka;

import com.google.common.base.Strings;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
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
    private volatile Producer<String, byte[]> producer = null;
    private volatile Instant timeOfLastFailedInitAttempt = Instant.EPOCH;

    public StroomKafkaProducerImpl(final ConnectorProperties properties) {
        this.properties = properties;
        this.bootstrapServers = (properties != null)
                ? properties.getProperty(StroomKafkaProducer.BOOTSTRAP_SERVERS_CONFIG)
                : null;

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

    public void send(final StroomKafkaProducerRecord<String, byte[]> stroomRecord,
                     final boolean flushOnSend,
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
                final ProducerRecord<String, byte[]> record =
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

                if (flushOnSend) {
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
                    Properties props = getProperties();

                    LOGGER.info("Creating Kafka Producer with Props: " + props);

                    final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                    try {
                        // This bizzarness has to be done to force the Kafka config libraries to use the 'Kafka Classloader'
                        // instead of the current thread content class loader.
                        // https://issues.apache.org/jira/browse/KAFKA-3218
                        Thread.currentThread().setContextClassLoader(null);
                        producer = new KafkaProducer<>(props);
                    } catch (Exception e) {
                        LOGGER.error("Error initialising kafka producer for {}", bootstrapServers, e);
                        throw e;
                    } finally {
                        Thread.currentThread().setContextClassLoader(classLoader);
                    }
                    LOGGER.info("Kafka Producer successfully created");
                    LOGGER.info(String.format("With Properties: %s", props));
                }
            }
        }
    }

    private Properties getProperties() {
        Properties props = new Properties();

        //The following props are configurable within stroom
        properties.copyProp(props, ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, null);
        properties.copyProp(props, ProducerConfig.ACKS_CONFIG, "all");
        properties.copyProp(props, ProducerConfig.RETRIES_CONFIG, 0);
        properties.copyProp(props, ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        properties.copyProp(props, ProducerConfig.LINGER_MS_CONFIG, 1);
        properties.copyProp(props, ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

        //serializers are hard coded as we have to specify the types when creating the
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.ByteArraySerializer.class.getName());

        return props;
    }

    /**
     * A check to prevent many process trying to repeatedly init a producer. This means init will only be attempted
     * every 30s to prevent the logs being filled up if kafka is down. It will never try to init if there is no
     * bootstrapServers value
     */
    private boolean isOkToInitNow(final String bootstrapServers) {
        return !Strings.isNullOrEmpty(bootstrapServers) &&
                Instant.now()
                        .isAfter(timeOfLastFailedInitAttempt.plusMillis(TIME_BETWEEN_INIT_ATTEMPS_MS));
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
