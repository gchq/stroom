package stroom.connectors.kafka;

import stroom.connectors.ConnectorProperties;

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
     * @param properties Should contain all the properties for the named kafka, with the prefix removed so they can be passed to kafka.
     * @return Either a connected {@link StroomKafkaProducer} or null if the version was a mismatch.
     */
    StroomKafkaProducer getProducer(final String version, ConnectorProperties properties);
}
