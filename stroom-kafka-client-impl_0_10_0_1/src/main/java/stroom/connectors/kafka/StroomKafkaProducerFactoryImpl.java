package stroom.connectors.kafka;

import stroom.connectors.ConnectorProperties;

public class StroomKafkaProducerFactoryImpl implements StroomKafkaProducerFactory {
    private static final String VERSION = "0.10.0.1";

    @Override
    public StroomKafkaProducer createConnector(final String version, final ConnectorProperties properties) {

        if (VERSION.equals(version)) {
            return new StroomKafkaProducerImpl(properties);
        }
        return null;
    }
}
