package stroom.kafka.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.kafka.api.KafkaProducerFactory;
import stroom.kafka.api.KafkaProducerSupplier;
import stroom.kafka.shared.KafkaConfigDoc;

import java.util.UUID;


@ExtendWith(MockitoExtension.class)
public class TestKafkaProducerFactoryImpl {

//    private EmbeddedKafkaCluster cluster;
//
//    @BeforeEach
//    public void setupKafka() {
//        cluster = provisionWith(useDefaults());
//        cluster.start();
//    }
//
//    @AfterEach
//    public void tearDownKafka() {
//        cluster.stop();
//    }

    @Mock
    KafkaConfigDocCache kafkaConfigDocCache;

    @Test
    public void getSupplier() {

        final KafkaProducerFactory kafkaProducerFactory = new KafkaProducerFactoryImpl(kafkaConfigDocCache);

        final KafkaConfigDoc kafkaConfigDoc = new KafkaConfigDoc(
                KafkaConfigDoc.DOCUMENT_TYPE,
                UUID.randomUUID().toString(),
                "MyDoc");

        final DocRef docRef = DocRefUtil.create(kafkaConfigDoc);

        final KafkaProducerSupplier kafkaProducerSupplier = kafkaProducerFactory.getSupplier(docRef);

    }
}