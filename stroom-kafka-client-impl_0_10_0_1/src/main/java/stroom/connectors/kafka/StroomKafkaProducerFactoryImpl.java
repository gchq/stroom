package stroom.connectors.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.connectors.ConnectorProperties;

public class StroomKafkaProducerFactoryImpl implements StroomKafkaProducerFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomKafkaProducerFactoryImpl.class);
    private static final String VERSION = "0.10.0.1";

    @Override
    public StroomKafkaProducer createConnector(final String version, final ConnectorProperties properties) {

        if (VERSION.equals(version)) {
            return new StroomKafkaProducerImpl(properties);
        } else {
            LOGGER.debug("Requested version [{}] doesn't match my version [{}]", version, VERSION);
        }
        return null;
    }
}
