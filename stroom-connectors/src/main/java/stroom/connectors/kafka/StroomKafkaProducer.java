package stroom.connectors.kafka;

import java.util.function.Consumer;

/**
 * A Stroom abstraction over the Kafka Producer library.
 * Allows us to only bind to Kafka libraries at runtime. This is proving desirable because those libraries
 * come with a fair bit of heft in transitive dependencies.
 */
public interface StroomKafkaProducer {
    /**
     * This is the one config item that must be present, everything else can be default
     */
    String BOOTSTRAP_SERVERS_CONFIG = "bootstrap.servers";

    /**
     * Given a Record, sends to the Kafka broker.
     * @param stroomRecord The record, contains the data and partition information.
     * @param flushMode Flush the producer on send
     * @param exceptionHandler A handler function if the exceptions are thrown. Allows custom exceptions (Runtime only)
     */
    void send(StroomKafkaProducerRecord<String, String> stroomRecord,
              FlushMode flushMode,
              Consumer<Exception> exceptionHandler);

    /**
     * Allow manual flushing of the producer by the client.
     */
    void flush();

    /**
     * Should be called when the producer is no longer required.
     */
    void shutdown();
}
