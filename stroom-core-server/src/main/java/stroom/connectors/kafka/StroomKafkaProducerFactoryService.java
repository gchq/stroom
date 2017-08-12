package stroom.connectors.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.connectors.ConnectorProperties;
import stroom.connectors.ExternalLibService;
import stroom.connectors.StroomAbstractConnectorFactoryService;
import stroom.connectors.StroomConnector;
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
public class StroomKafkaProducerFactoryService
        extends StroomAbstractConnectorFactoryService<StroomKafkaProducer, StroomKafkaProducerFactory> {

    // Old configs may not have named server/version properties, so use this property name
    private static final String OLD_PROP_BOOTSTRAP_SERVERS = "stroom.kafka.bootstrap.servers";

    // Default kafka client version for old configs
    private static final String KAFKA_CLIENT_VERSION_DEFAULT = "0.10.0.1";

    // Can register server/version pairs for specific roles within the system
    private static final String PROP_PREFIX = "stroom.connectors.kafka.%s.";

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomKafkaProducerFactoryService.class);

    @Inject
    public StroomKafkaProducerFactoryService(final StroomPropertyService propertyService,
                                             final ExternalLibService externalLibService) {
        super(propertyService,
                externalLibService,
                PROP_PREFIX,
                StroomKafkaProducer.class,
                StroomKafkaProducerFactory.class);
    }

    @Override
    protected String setupPropertyDefaults(final ConnectorProperties connectorProperties) {
        connectorProperties.put(StroomKafkaProducer.BOOTSTRAP_SERVERS_CONFIG,
                getPropertyService().getProperty(OLD_PROP_BOOTSTRAP_SERVERS));
        return KAFKA_CLIENT_VERSION_DEFAULT;
    }

    @StroomShutdown
    public void shutdown() {
        LOGGER.info("Shutting Down Stroom Kafka Producer Factory Service");
        super.shutdown();
    }
}
