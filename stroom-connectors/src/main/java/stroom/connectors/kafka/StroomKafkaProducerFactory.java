package stroom.connectors.kafka;

/**
 * An interface for creating {@link StroomKafkaProducer} instances.
 * A given factory is expected to be able to create instances for a specific version of the Kafka client.
 * We are leaving the door open to multiple versions of Kafka integating into a given instance (maybe...)
 */
public interface StroomKafkaProducerFactory {
    /**
     * Given a Kafka client version and the bootstrapServers of the Kafka instance, attempts to create
     * a {@link StroomKafkaProducer}. Will only succeed if the factory is of the correct version.
     *
     * @param version The version of the Kafka client library
     * @param bootstrapServers The servers where the Kafka instance can be found
     * @return Either a connected {@link StroomKafkaProducer} or null if the version was a mismatch.
     */
    StroomKafkaProducer getProducer(final String version, String bootstrapServers);
}
