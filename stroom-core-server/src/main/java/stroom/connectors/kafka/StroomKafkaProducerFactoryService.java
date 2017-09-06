package stroom.connectors.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.connectors.ConnectorProperties;
import stroom.connectors.ConnectorPropertiesPrefixImpl;
import stroom.connectors.ExternalLibService;
import stroom.node.server.StroomPropertyService;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomShutdown;

import javax.inject.Inject;
import java.util.*;

/**
 * This service can be used to create instances of StroomKafkaProducers.
 *
 * The client requests a producer, giving the service a 'named' instance to find.
 * The name is used to select properties from the stroom.conf file that indicate the
 * bootstrapServers and the kafka client version.
 * It uses {@link ServiceLoader} to iterate through all {@link StroomKafkaProducerFactory} and find one
 * that can create a {@link StroomKafkaProducer} for the requested version.
 */
@Component
@Scope(StroomScope.SINGLETON)
public class StroomKafkaProducerFactoryService {
    // If a class requests a producer, but gives no name, use this value as the name
    private static final String DEFAULT_NAME = "default";

    // Old configs may not have named server/version properties, so use this property name
    private static final String OLD_PROP_BOOTSTRAP_SERVERS = "stroom.kafka.bootstrap.servers";

    // Default kafka client version for old configs
    private static final String KAFKA_CLIENT_VERSION_DEFAULT = "0.10.0.1";

    // Can register server/version pairs for specific roles within the system
    private static final String PROP_PREFIX = "stroom.connectors.kafka.%s.";
    private static final String PROP_KAFKA_CLIENT_VERSION = "client.version";

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomKafkaProducerFactoryService.class);

    private final ServiceLoader<StroomKafkaProducerFactory> loader;
    private final StroomPropertyService propertyService;

    // Used to keep track of the instances created, a single instance per name will be created and shared.
    private final Map<String, StroomKafkaProducer> producersByName;

    @Inject
    public StroomKafkaProducerFactoryService(final StroomPropertyService propertyService,
                                             final ExternalLibService externalLibService) {
        this.propertyService = propertyService;
        this.loader = externalLibService.load(StroomKafkaProducerFactory.class);
        this.producersByName = new HashMap<>();
    }

    /**
     * Overloaded version of getProducer that uses the default name.
     *
     * @return A {@link StroomKafkaProducer} connecting to the named Kafka server.
     */
    public synchronized StroomKafkaProducer getProducer() {
        return getProducer(DEFAULT_NAME);
    }

    /**
     * Users of this service can request a named Kafka connection. This is used to merge in the property names of the
     * bootstrap servers and the kafka client version.
     *
     * @param name The named configuration of Kafka client.
     * @return A {@link StroomKafkaProducer} connecting to the named Kafka server.
     */
    public synchronized StroomKafkaProducer getProducer(final String name) {
        LOGGER.info("Retrieving the Kafka Producer for " + name);

        final ConnectorProperties connectorProperties = new ConnectorPropertiesPrefixImpl(String.format(PROP_PREFIX, name), this.propertyService);

        // Retrieve the settings for this named kafka
        String kafkaVersion = connectorProperties.getProperty(PROP_KAFKA_CLIENT_VERSION);

        // If the named properties are empty, this is an old config
        if (null == kafkaVersion) {
            connectorProperties.put(StroomKafkaProducer.BOOTSTRAP_SERVERS_CONFIG, propertyService.getProperty(OLD_PROP_BOOTSTRAP_SERVERS));
            kafkaVersion = KAFKA_CLIENT_VERSION_DEFAULT;
        }

        // First try and find a previously created producer by this name
        StroomKafkaProducer producer = producersByName.get(name);

        // If a producer has not already been created for this name, it is time to create one
        if (producer == null) {
            for (StroomKafkaProducerFactory factory : loader) {
                producer = factory.getProducer(kafkaVersion, connectorProperties);
                if (producer != null) {
                    break;
                }
            }
        }

        // Either store the producer, or throw an exception as one could not be found
        if (producer != null) {
            producersByName.put(name, producer);
        } else {
            throw new RuntimeException(String.format("Could not find Stroom Kafka Producer Factory for version: %s", kafkaVersion));
        }

        return producer;
    }

    @StroomShutdown
    public void shutdown() {
        LOGGER.info("Shutting Down Stroom Kafka Producer Factory Service");
        producersByName.values().forEach(StroomKafkaProducer::shutdown);
        producersByName.clear();
    }
}
