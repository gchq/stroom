package stroom.connectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.connectors.kafka.StroomKafkaProducer;
import stroom.node.server.StroomPropertyService;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public abstract class StroomAbstractProducerFactoryService<
        P extends StroomConnectorProducer,
        F extends StroomConnectorProducerFactory<P>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomAbstractProducerFactoryService.class);

    // If a class requests a producer, but gives no name, use this value as the name
    private static final String DEFAULT_NAME = "default";

    // This property should exist with the given property prefix.
    private static final String PROP_CONNECTOR_VERSION = "connector.version";

    private final String propertyPrefix;
    private final StroomPropertyService propertyService;

    private final ServiceLoader<F> loader;

    // Used to keep track of the instances created, a single instance per name will be created and shared.
    private final Map<String, P> producersByName;

    protected StroomAbstractProducerFactoryService(final StroomPropertyService propertyService,
                                                   final ExternalLibService externalLibService,
                                                   final String propertyPrefix,
                                                   final Class<F> factoryClass) {
        this.propertyService = propertyService;
        this.propertyPrefix = propertyPrefix;
        this.loader = externalLibService.load(factoryClass);
        this.producersByName = new HashMap<>();
    }

    /**
     * Overloaded version of getProducer that uses the default name.
     *
     * @return A {@link P} configured accordingly.
     */
    public synchronized P getProducer() {
        return getProducer(DEFAULT_NAME);
    }

    /**
     * Users of this service can request a named Kafka connection. This is used to merge in the property names of the
     * bootstrap servers and the kafka client version.
     *
     * @param name The named configuration of producer.
     * @return A {@link P} configured accordingly.
     */
    public synchronized P getProducer(final String name) {
        LOGGER.info("Retrieving the Connector Producer for " + name);

        final ConnectorProperties connectorProperties = new ConnectorPropertiesPrefixImpl(String.format(propertyPrefix, name), this.propertyService);

        // First try and find a previously created producer by this name
        P producer = producersByName.get(name);

        // Retrieve the settings for this named kafka
        String connectorVersion = connectorProperties.getProperty(PROP_CONNECTOR_VERSION);

        // If the named properties are empty, this is an old config
        if (null == connectorVersion) {
            connectorVersion = setupPropertyDefaults(connectorProperties);
        }

        // If a producer has not already been created for this name, it is time to create one
        if (producer == null) {
            for (F factory : loader) {
                producer = factory.getProducer(connectorVersion, connectorProperties);
                if (producer != null) {
                    break;
                }
            }
        }

        // Either store the producer, or throw an exception as one could not be found
        if (producer != null) {
            producersByName.put(name, producer);
        } else {
            throw new RuntimeException(String.format("Could not find Stroom Kafka Producer Factory for version: %s", connectorVersion));
        }

        return producer;
    }

    public void shutdown() {
        LOGGER.info("Shutting Down Stroom Connector Producer Factory Service");
        producersByName.values().forEach(StroomConnectorProducer::shutdown);
        producersByName.clear();
    }

    protected StroomPropertyService getPropertyService() {
        return propertyService;
    }

    protected abstract String setupPropertyDefaults(final ConnectorProperties connectorProperties);
}
