package stroom.connectors.elastic;

import org.junit.Ignore;
import org.junit.Test;
import stroom.connectors.ConnectorProperties;
import stroom.connectors.ConnectorPropertiesEmptyImpl;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class TestStroomElasticProducer {
    private static final String ELASTIC_VERSION = "5.6.4";

    public static final Consumer<Exception> DEFAULT_CALLBACK = ex -> {
        throw new RuntimeException(String.format("Exception during send"), ex);
    };

    @Test
    @Ignore("You may use this to test the local instance of Elastic.")
    public void testManualSend() {
        // Given
        final String recordId = UUID.randomUUID().toString();
        StroomElasticProducerFactoryImpl stroomElasticProducerFactory = new StroomElasticProducerFactoryImpl();
        ConnectorProperties elasticProps = new ConnectorPropertiesEmptyImpl();
        elasticProps.put(StroomElasticProducer.ELASTIC_HTTP_URL, "http://localhost:9200");
        elasticProps.put(StroomElasticProducer.TRANSPORT_HOSTS, "localhost:9300");
        elasticProps.put(StroomElasticProducer.CLUSTER_NAME, "docker-cluster");
        StroomElasticProducer stroomElasticProducer = stroomElasticProducerFactory.getConnector(ELASTIC_VERSION, elasticProps);

        final String STAT_NAME = "stat-name";
        final String STAT_TYPE = "stat-type";
        final String STAT_TIME = "stat-time";

        final Map<String, String> record = new HashMap<>();
        record.put(STAT_NAME, UUID.randomUUID().toString());
        record.put(STAT_TYPE, "test");
        record.put(STAT_TIME, new Date().toString());

        // When
        stroomElasticProducer.send(recordId, "statistics", "line", record, DEFAULT_CALLBACK);

        // Then: manually check your Elastic instances 'statistics' index for 'test'
    }
}
