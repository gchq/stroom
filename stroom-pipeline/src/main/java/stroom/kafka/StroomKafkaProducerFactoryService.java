package stroom.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.connectors.ExternalLibService;
import stroom.connectors.StroomAbstractConnectorFactoryService;
import stroom.connectors.kafka.StroomKafkaProducer;
import stroom.connectors.kafka.StroomKafkaProducerFactory;
import stroom.properties.api.StroomPropertyService;
import stroom.util.lifecycle.StroomShutdown;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ServiceLoader;

/**
 * This service can be used to create instances of StroomKafkaProducers.
 * <p>
 * The client requests a producer, giving the service a 'named' instance to find.
 * The name is used to select properties from the stroom.conf file that indicate the
 * bootstrapServers and the kafka client version.
 * It uses {@link ServiceLoader} to iterate through all {@link StroomKafkaProducerFactory} and find one
 * that can create a {@link StroomKafkaProducer} for the requested version.
 */
@Singleton
public class StroomKafkaProducerFactoryService extends StroomAbstractConnectorFactoryService<StroomKafkaProducer, StroomKafkaProducerFactory> {

    // Can register server/version pairs for specific roles within the system
    private static final String PROP_PREFIX = "stroom.connectors.kafka.%s.";

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomKafkaProducerFactoryService.class);

    @Inject
    StroomKafkaProducerFactoryService(final StroomPropertyService propertyService,
                                      final ExternalLibService externalLibService) {
        super(propertyService,
                externalLibService,
                PROP_PREFIX,
                StroomKafkaProducer.class,
                StroomKafkaProducerFactory.class);
    }


    @StroomShutdown
    public void shutdown() {
        LOGGER.info("Shutting Down Stroom Kafka Producer Factory Service");
        super.shutdown();
    }
}
