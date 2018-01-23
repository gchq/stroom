package stroom.connectors.elastic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.connectors.ConnectorProperties;

public class StroomElasticProducerFactoryImpl implements StroomElasticProducerFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomElasticProducerFactoryImpl.class);
    private static final String VERSION = "5.6.4";

    @Override
    public StroomElasticProducer createConnector(String version, ConnectorProperties properties) {
        if (VERSION.equals(version)) {
            return new StroomElasticProducerImpl(properties);
        } else {
            LOGGER.debug("Requested version [{}] doesn't match my version [{}]", version, VERSION);
        }
        return null;
    }
}
