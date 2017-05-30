package stroom.kafka;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.util.spring.StroomScope;

import java.util.Properties;
import java.util.function.Consumer;

/**
 * A singleton responsible for sending records to Kafka.
 * <p>
 * Requires configuration:
 * <<code>
 * kafka.bootstrap.servers=<host>:<port>
 * </code>
 */
@Component
@Scope(StroomScope.SINGLETON)
public class StroomKafkaProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomKafkaProducer.class);

    private final String bootstrapServers;
    private volatile Producer<String, String> producer = null;

    public StroomKafkaProducer(
            @Value("#{propertyConfigurer.getProperty('kafka.bootstrap.servers')}") final String bootstrapServers) {

        this.bootstrapServers = bootstrapServers;

        try {
            intiProducer();
        } catch (Exception e) {
            //Log and swallow the exception to prevent creation of the bean
            LOGGER.error("Error initialising kafka producer to " + bootstrapServers, e);
        }
    }

    public void send(final ProducerRecord<String, String> record, final Consumer<Exception> exceptionConsumer) {
        //kafka may not have been up on startup so ensure we have a producer instance now
        try {
            intiProducer();

            Preconditions.checkNotNull(producer).send(record, (recordMetadata, exception) -> {

                if (exception != null) {
                    exceptionConsumer.accept(exception);
                }
                LOGGER.trace("Record sent to Kafka");
            });

            producer.flush();
        } catch (Exception e) {
            LOGGER.error("Error initialising kafka producer to " + bootstrapServers, e);
            exceptionConsumer.accept(e);
        }
    }

    private void intiProducer() {
        if (producer == null) {
            synchronized (this) {
                if (producer == null) {
                    if (Strings.isNullOrEmpty(bootstrapServers)) {
                        LOGGER.error("Kafka is not properly configured: 'kafka.bootstrap.servers' is required.");
                    } else {
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

                        producer = new KafkaProducer<>(props);
                        LOGGER.info("Ready to send records to Kafka.");
                    }
                }
            }
        }
    }

    public void close() {
        producer.close();
    }
}
