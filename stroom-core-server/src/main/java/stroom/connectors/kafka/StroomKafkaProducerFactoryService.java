package stroom.connectors.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.util.config.StroomProperties;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomShutdown;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;

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
    private static final String PROP_BOOTSTRAP_SERVERS = "stroom.kafka.bootstrap.servers";

    // Default kafka client version for old configs
    private static final String KAFKA_CLIENT_VERSION_DEFAULT = "0.10.0.1";

    // Can register server/version pairs for specific roles within the system
    private static final String PROP_BOOTSTRAP_SERVERS_NAMED = "stroom.kafka.bootstrap.servers.%s";
    private static final String PROP_KAFKA_CLIENT_VERSION_NAMED = "stroom.kafka.client.version.%s";

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomKafkaProducerFactoryService.class);

    private final ServiceLoader<StroomKafkaProducerFactory> loader;
    private final StroomPropertyService propertyService;

    // Used to keep track of the instances created, a single instance per name will be created and shared.
    private final Map<String, StroomKafkaProducer> producersByName;

    @Inject
    public StroomKafkaProducerFactoryService(final StroomPropertyService propertyService) {
        this.propertyService = propertyService;
        this.loader = ServiceLoader.load(StroomKafkaProducerFactory.class);
        this.producersByName = new HashMap<>();
    }

    /**
     * Users of this service can request a named Kafka connection. This is used to merge in the property names of the
     * bootstrap servers and the kafka client version.
     * @param name The named configuration of Kafka client.
     * @return A {@link StroomKafkaProducer} connecting to the named Kafka server.
     */
    public synchronized StroomKafkaProducer getProducer(final String name) {
        // Retrieve the settings for this named kafka
        String bootstrapServers = getNamedProperty(PROP_BOOTSTRAP_SERVERS_NAMED, name);
        String kafkaVersion = getNamedProperty(PROP_KAFKA_CLIENT_VERSION_NAMED, name);

        // Backwards compatible with old configs
        if (null == bootstrapServers) {
            bootstrapServers = propertyService.getProperty(PROP_BOOTSTRAP_SERVERS);
            kafkaVersion = KAFKA_CLIENT_VERSION_DEFAULT;
        }

        // First try and find a previously created producer by this name
        StroomKafkaProducer producer = producersByName.get(name);

        // If a producer has not already been created for this name, it is time to create one
        if (producer == null) {
            for (StroomKafkaProducerFactory factory : loader) {
                producer = factory.getProducer(kafkaVersion, bootstrapServers);
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

    /**
     * Get value of a property, using the name of the producer to format the base property name.
     *
     * Allows specification of different bootstrap servers and kafka versions for different purposes.
     *
     * i.e. stroom.kafka.bootstrap.servers.stats = 0.0.0.0
     *      stroom.kafka.bootstrap.servers.externalPartner = 1.1.1.1
     *
     * @param propBase The base property (will have a %s format string for the name)
     * @param name The named kafka producer config
     * @return The value of the property for the named instance OR the default instance if the name given is null.
     */
    private String getNamedProperty(final String propBase, final String name) {
        final String nameToUse = (null != name) ? name : DEFAULT_NAME;
        return propertyService.getProperty(String.format(propBase, nameToUse));
    }

    @StroomShutdown
    public void shutdown() {
        LOGGER.info("Shutting Down Stroom Kafka Producer Factory Service");
        producersByName.values().forEach(StroomKafkaProducer::shutdown);
        producersByName.clear();
    }
}
