package stroom.connectors.elastic;

import stroom.connectors.ConnectorProperties;

public class StroomElasticProducerFactoryImpl implements StroomElasticProducerFactory {
    private static final String VERSION = "5.6.4";

    @Override
    public StroomElasticProducer getConnector(String version, ConnectorProperties properties) {
        if (VERSION.equals(version)) {
            return new StroomElasticProducerImpl(properties);
        }
        return null;
    }
}
