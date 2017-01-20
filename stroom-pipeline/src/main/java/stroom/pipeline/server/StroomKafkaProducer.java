package stroom.pipeline.server;

import com.google.common.base.Strings;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import java.util.Properties;

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
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StroomKafkaProducer.class);

    private Producer<String, String> producer = null;
    private static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap.servers";

    public StroomKafkaProducer(
            @Value("#{propertyConfigurer.getProperty('kafka.bootstrap.servers')}") final String bootstrapServers) {
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

    public void send(ProducerRecord<String, String> record, ErrorReceiverProxy errorReceiverProxy) {
        if (producer != null) {

            producer.send(record, (recordMetadata, exception) -> {
                if (exception != null) {
                    String errorMessage = "Unable to send record to Kafka: " + exception.getMessage();
                    LOGGER.error(errorMessage);
                    errorReceiverProxy.log(Severity.ERROR, null, null, errorMessage, exception);
                }
                LOGGER.trace("Record send to Kafka");
            });

            producer.flush();
        } else {
            throw new RuntimeException("Kafka is not properly configured!");
        }
    }

    public void close() {
        producer.close();
    }
}
