package stroom.kafka;

import com.google.common.base.Strings;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomShutdown;

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
@Component
@Scope(StroomScope.SINGLETON)
public class StroomKafkaProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomKafkaProducer.class);
    private static final int TIME_BETWEEN_INIT_ATTEMPS_MS = 30_000;

    public enum FlushMode {
        FLUSH_ON_SEND,
        NO_FLUSH
    }

    private final String bootstrapServers;

    //instance of a kafka producer that will be shared by all threads
    private volatile Producer<String, String> producer = null;
    private volatile Instant timeOfLastFailedInitAttempt = Instant.EPOCH;

    public StroomKafkaProducer(
            @Value("#{propertyConfigurer.getProperty('stroom.kafka.bootstrap.servers')}") final String bootstrapServers) {

        this.bootstrapServers = bootstrapServers;
    }

    public void send(final ProducerRecord<String, String> record,
                     final FlushMode flushMode,
                     final Consumer<Exception> exceptionHandler) {
        //kafka may not have been up on startup so ensure we have a producer instance now
        try {
            intiProducer();

            if (producer != null) {
                try {
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
                    LOGGER.error("Error initialising kafka producer to " + bootstrapServers, e);
                    exceptionHandler.accept(e);
                }
            } else {
                exceptionHandler.accept(new IOException("The kafka producer is currently not initialised, " +
                        "kafka may be down or the connection details incorrect"));
            }
        } catch (Exception e) {
            LOGGER.error("Error initialising kafka producer to " + bootstrapServers, e);
            exceptionHandler.accept(e);
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
    private void intiProducer() {
        if (producer == null) {
            synchronized (this) {
                if (producer == null && isOkToInitNow()) {
                    if (Strings.isNullOrEmpty(bootstrapServers)) {
                        LOGGER.error("Kafka is not properly configured: 'stroom.kafka.bootstrap.servers' is required.");
                    } else {
                        LOGGER.info("Initialising kafka producer for {}", bootstrapServers);
                        Properties props = new Properties();
                        props.put("bootstrap.servers", bootstrapServers);
                        props.put("acks", "all");
                        props.put("retries", 0);
                        props.put("batch.size", 16384);
                        props.put("linger.ms", 1);
                        props.put("buffer.memory", 33554432);
                        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
                        // TODO We will probably want to send Avro'd bytes (or something similar) at some point, so the serializer will need to change.
                        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

                        try {
                            producer = new KafkaProducer<>(props);
                            LOGGER.info("Ready to send records to Kafka.");
                        } catch (Exception e) {
                            LOGGER.error("Error initialising kafka producer for {}", bootstrapServers, e);
                            throw e;
                        }
                    }
                }
            }
        }
    }

    /**
     * A check to prevent many process trying to repeatedly init a producer. This means init will only be attempted
     * every 30s to prevent the logs being filled up if kafka is down
     */
    private boolean isOkToInitNow() {
        return Instant.now().isAfter(timeOfLastFailedInitAttempt.plusMillis(TIME_BETWEEN_INIT_ATTEMPS_MS));
    }

    @StroomShutdown
    public void shutdown() {
        if (producer != null) {
            try {
                producer.close();
            } catch (Exception e) {
                LOGGER.error("Error closing kafka producer", e);
            }
        }
    }
}
