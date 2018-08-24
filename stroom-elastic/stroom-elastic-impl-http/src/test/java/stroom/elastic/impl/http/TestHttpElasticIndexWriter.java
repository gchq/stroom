package stroom.elastic.impl.http;

import org.junit.Ignore;
import org.junit.Test;
import stroom.elastic.impl.ElasticIndexConfigDoc;
import stroom.elastic.api.ElasticIndexWriter;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class TestHttpElasticIndexWriter {
    public static final Consumer<Exception> DEFAULT_CALLBACK = ex -> {
        throw new RuntimeException(String.format("Exception during write"), ex);
    };

    @Test
    @Ignore("You may use this to test the local instance of Elastic.")
    public void testManualSend() {
        // Given
        final String recordId = UUID.randomUUID().toString();

        final ElasticIndexConfigDoc elasticIndexConfigDoc = new ElasticIndexConfigDoc();
        elasticIndexConfigDoc.setElasticHttpUrl("http://localhost:9200");
//        elasticIndexConfigDoc.setTransportHosts("localhost:9300");
//        elasticIndexConfigDoc.setClusterName("docker-cluster");

        final ElasticIndexWriter stroomElasticProducer = new HttpElasticIndexWriter(elasticIndexConfigDoc);

        final String STAT_NAME = "stat-name";
        final String STAT_TYPE = "stat-type";
        final String STAT_TIME = "stat-time";

        final Map<String, String> record = new HashMap<>();
        record.put(STAT_NAME, UUID.randomUUID().toString());
        record.put(STAT_TYPE, "test");
        record.put(STAT_TIME, new Date().toString());

        // When
        stroomElasticProducer.write(recordId, "statistics", "line", record, DEFAULT_CALLBACK);

        // Then: manually check your Elastic instances 'statistics' index for 'test'
    }
}
