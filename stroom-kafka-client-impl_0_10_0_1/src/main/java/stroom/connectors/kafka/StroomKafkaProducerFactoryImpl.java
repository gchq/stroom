package stroom.connectors.kafka;

public class StroomKafkaProducerFactoryImpl implements StroomKafkaProducerFactory {
    private static final String VERSION = "0.10.0.1";

    @Override
    public StroomKafkaProducer getProducer(String version, String bootstrapServers) {
        if (VERSION.equals(version)) {
            return new StroomKafkaProducerImpl(bootstrapServers);
        }
        return null;
    }
}
