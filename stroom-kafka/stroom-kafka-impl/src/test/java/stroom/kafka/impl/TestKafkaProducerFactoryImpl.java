package stroom.kafka.impl;

import com.codahale.metrics.health.HealthCheck;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.kafka.api.KafkaProducerFactory;
import stroom.kafka.api.SharedKafkaProducer;
import stroom.kafka.shared.KafkaConfigDoc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class TestKafkaProducerFactoryImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestKafkaProducerFactoryImpl.class);

    @Mock
    KafkaConfigDocCache kafkaConfigDocCache;

    @Test
    public void getSupplier_empty() {

        final KafkaProducerFactory kafkaProducerFactory = new KafkaProducerFactoryImpl(kafkaConfigDocCache);

        final KafkaConfigDoc kafkaConfigDoc = createKafkaConfigDoc("Config1", "v1");

        final DocRef docRef = DocRefUtil.create(kafkaConfigDoc);

        final SharedKafkaProducer sharedKafkaProducer = kafkaProducerFactory.getSharedProducer(docRef);

        // Cache knows nothing of the doc so there cannot be a producer
        assertThat(sharedKafkaProducer.getKafkaProducer()).isEmpty();
        assertThat(sharedKafkaProducer.hasKafkaProducer()).isFalse();

        // Check we can still close with an empty supplier
        sharedKafkaProducer.close();
    }

    @Test
    public void getSupplier_multipleCloseCalls() {

        final KafkaProducerFactory kafkaProducerFactory = new KafkaProducerFactoryImpl(kafkaConfigDocCache);

        final KafkaConfigDoc kafkaConfigDoc = createKafkaConfigDoc("Config1", "v1");

        final DocRef docRef = DocRefUtil.create(kafkaConfigDoc);

        final SharedKafkaProducer sharedKafkaProducer = kafkaProducerFactory.getSharedProducer(docRef);

        // Cache knows nothing of the doc so there cannot be a producer
        assertThat(sharedKafkaProducer.getKafkaProducer()).isEmpty();
        assertThat(sharedKafkaProducer.hasKafkaProducer()).isFalse();

        // Check we can still close with an empty supplier
        sharedKafkaProducer.close();

        // it is closed so closing again will throw
        Assertions.assertThatThrownBy(sharedKafkaProducer::close)
                .isInstanceOf(RuntimeException.class);

        // it is closed so getting again will throw
        Assertions.assertThatThrownBy(sharedKafkaProducer::getKafkaProducer)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void getSupplier_twoConfigs() {

        final KafkaProducerFactory kafkaProducerFactory = new KafkaProducerFactoryImpl(kafkaConfigDocCache);

        final KafkaConfigDoc kafkaConfigDoc1 = createKafkaConfigDoc("Config1", "v1");
        final KafkaConfigDoc kafkaConfigDoc2 = createKafkaConfigDoc("Config2", "v1");

        final DocRef docRef1 = DocRefUtil.create(kafkaConfigDoc1);
        final DocRef docRef2 = DocRefUtil.create(kafkaConfigDoc2);

        Mockito.when(kafkaConfigDocCache.get(Mockito.eq(docRef1)))
                .thenReturn(Optional.of(kafkaConfigDoc1));
        Mockito.when(kafkaConfigDocCache.get(Mockito.eq(docRef2)))
                .thenReturn(Optional.of(kafkaConfigDoc2));

        final SharedKafkaProducer sharedKafkaProducer1 = kafkaProducerFactory.getSharedProducer(docRef1);

        assertThat(sharedKafkaProducer1.getKafkaProducer()).isPresent();
        assertThat(sharedKafkaProducer1.hasKafkaProducer()).isTrue();
        assertThat(sharedKafkaProducer1.getConfigName()).isEqualTo(kafkaConfigDoc1.getName());
        assertThat(sharedKafkaProducer1.getConfigUuid()).isEqualTo(kafkaConfigDoc1.getUuid());
        assertThat(sharedKafkaProducer1.getConfigVersion()).isEqualTo(kafkaConfigDoc1.getVersion());

        KafkaProducer<String, byte[]> kafkaProducer1 = sharedKafkaProducer1.getKafkaProducer().get();

        assertThat(kafkaProducer1).isNotNull();

        // Now get the second one

        final SharedKafkaProducer sharedKafkaProducer2 = kafkaProducerFactory.getSharedProducer(docRef2);

        assertThat(sharedKafkaProducer2.getKafkaProducer()).isPresent();
        assertThat(sharedKafkaProducer2.hasKafkaProducer()).isTrue();
        assertThat(sharedKafkaProducer2.getConfigName()).isEqualTo(kafkaConfigDoc2.getName());
        assertThat(sharedKafkaProducer2.getConfigUuid()).isEqualTo(kafkaConfigDoc2.getUuid());
        assertThat(sharedKafkaProducer2.getConfigVersion()).isEqualTo(kafkaConfigDoc2.getVersion());

        KafkaProducer<String, byte[]> kafkaProducer2 = sharedKafkaProducer2.getKafkaProducer().get();

        assertThat(kafkaProducer2).isNotNull();

        assertThat(kafkaProducer1).isNotSameAs(kafkaProducer2);

        // Now get the first one again to check it is the same KP instance

        final SharedKafkaProducer sharedKafkaProducer1B = kafkaProducerFactory.getSharedProducer(docRef1);

        assertThat(sharedKafkaProducer1B.getKafkaProducer()).isPresent();
        assertThat(sharedKafkaProducer1B.hasKafkaProducer()).isTrue();
        assertThat(sharedKafkaProducer1B.getConfigName()).isEqualTo(kafkaConfigDoc1.getName());
        assertThat(sharedKafkaProducer1B.getConfigUuid()).isEqualTo(kafkaConfigDoc1.getUuid());
        assertThat(sharedKafkaProducer1B.getConfigVersion()).isEqualTo(kafkaConfigDoc1.getVersion());

        KafkaProducer<String, byte[]> kafkaProducer1b = sharedKafkaProducer1B.getKafkaProducer().get();

        assertThat(kafkaProducer1b).isNotNull();

        assertThat(kafkaProducer1b).isNotSameAs(kafkaProducer2);
        assertThat(kafkaProducer1b).isSameAs(kafkaProducer1);

        kafkaProducer1.close();
        kafkaProducer2.close();
        kafkaProducer1b.close();
    }

    @Test
    public void getSupplier_updatedConfig() {

        final KafkaProducerFactory kafkaProducerFactory = new KafkaProducerFactoryImpl(kafkaConfigDocCache);

        final KafkaConfigDoc kafkaConfigDoc1mk1 = createKafkaConfigDoc("Config1", "v1");

        final DocRef docRef1 = DocRefUtil.create(kafkaConfigDoc1mk1);

        Mockito.when(kafkaConfigDocCache.get(Mockito.eq(docRef1)))
                .thenReturn(Optional.of(kafkaConfigDoc1mk1));

        final SharedKafkaProducer sharedKafkaProducer1 = kafkaProducerFactory.getSharedProducer(docRef1);

        assertThat(sharedKafkaProducer1.getKafkaProducer()).isPresent();
        assertThat(sharedKafkaProducer1.hasKafkaProducer()).isTrue();
        assertThat(sharedKafkaProducer1.getConfigName()).isEqualTo(kafkaConfigDoc1mk1.getName());
        assertThat(sharedKafkaProducer1.getConfigUuid()).isEqualTo(kafkaConfigDoc1mk1.getUuid());
        assertThat(sharedKafkaProducer1.getConfigVersion()).isEqualTo(kafkaConfigDoc1mk1.getVersion());

        final KafkaProducer<String, byte[]> kafkaProducer1 = sharedKafkaProducer1.getKafkaProducer().get();

        assertThat(kafkaProducer1).isNotNull();

        // create an updated version of the doc with the same uuid
        final KafkaConfigDoc kafkaConfigDoc1mk2 = createKafkaConfigDoc("Config1", "v2");
        kafkaConfigDoc1mk2.setUuid(kafkaConfigDoc1mk1.getUuid());

        Mockito.when(kafkaConfigDocCache.get(Mockito.eq(docRef1)))
                .thenReturn(Optional.of(kafkaConfigDoc1mk2));

        final SharedKafkaProducer sharedKafkaProducer1B = kafkaProducerFactory.getSharedProducer(docRef1);

        assertThat(sharedKafkaProducer1B.getKafkaProducer()).isPresent();
        assertThat(sharedKafkaProducer1B.hasKafkaProducer()).isTrue();
        assertThat(sharedKafkaProducer1B.getConfigName()).isEqualTo(kafkaConfigDoc1mk1.getName());
        assertThat(sharedKafkaProducer1B.getConfigUuid()).isEqualTo(kafkaConfigDoc1mk1.getUuid());
        assertThat(sharedKafkaProducer1B.getConfigVersion()).isEqualTo(kafkaConfigDoc1mk2.getVersion());

        final KafkaProducer<String, byte[]> kafkaProducer1b = sharedKafkaProducer1B.getKafkaProducer().get();

        assertThat(kafkaProducer1b).isNotNull();

        assertThat(kafkaProducer1b).isNotSameAs(kafkaProducer1);

        // Now get it again to check it is the same KP instance

        final SharedKafkaProducer sharedKafkaProducer1C = kafkaProducerFactory.getSharedProducer(docRef1);

        assertThat(sharedKafkaProducer1C.getKafkaProducer()).isPresent();
        assertThat(sharedKafkaProducer1C.hasKafkaProducer()).isTrue();
        assertThat(sharedKafkaProducer1C.getConfigName()).isEqualTo(kafkaConfigDoc1mk1.getName());
        assertThat(sharedKafkaProducer1C.getConfigUuid()).isEqualTo(kafkaConfigDoc1mk1.getUuid());
        assertThat(sharedKafkaProducer1C.getConfigVersion()).isEqualTo(kafkaConfigDoc1mk2.getVersion());

        KafkaProducer<String, byte[]> kafkaProducer1c = sharedKafkaProducer1C.getKafkaProducer().get();

        assertThat(kafkaProducer1c).isNotNull();

        assertThat(kafkaProducer1c).isNotSameAs(kafkaProducer1);
        assertThat(kafkaProducer1c).isSameAs(kafkaProducer1b);

        kafkaProducer1.close();
        kafkaProducer1b.close();
        kafkaProducer1c.close();
    }

    @Test
    void testHealthCheck() {

        final KafkaProducerFactoryImpl kafkaProducerFactory = new KafkaProducerFactoryImpl(kafkaConfigDocCache);

        final KafkaConfigDoc kafkaConfigDoc1 = createKafkaConfigDoc("Config1", "v1");
        final KafkaConfigDoc kafkaConfigDoc2 = createKafkaConfigDoc("Config2", "v1");

        final DocRef docRef1 = DocRefUtil.create(kafkaConfigDoc1);
        final DocRef docRef2 = DocRefUtil.create(kafkaConfigDoc2);

        Mockito.when(kafkaConfigDocCache.get(Mockito.eq(docRef1)))
                .thenReturn(Optional.of(kafkaConfigDoc1));
        Mockito.when(kafkaConfigDocCache.get(Mockito.eq(docRef2)))
                .thenReturn(Optional.of(kafkaConfigDoc2));

        final HealthCheck.Result health = kafkaProducerFactory.getHealth();
        LOGGER.info(health.toString());
        assertThat((List<?>) ( health.getDetails().get("producers") )).isEmpty();


        // Now get both so the factory should be holding both
        try (final SharedKafkaProducer sharedKafkaProducer1 = kafkaProducerFactory.getSharedProducer(docRef1)) {
            try (final SharedKafkaProducer sharedKafkaProducer2 = kafkaProducerFactory.getSharedProducer(docRef2)) {

                final HealthCheck.Result health2 = kafkaProducerFactory.getHealth();
                LOGGER.info(health2.toString());
                assertThat((List<?>)( health2.getDetails().get("producers") )).hasSize(2);
            }
        }

        // having released the KPSs they should still be in the factory
        final HealthCheck.Result health3 = kafkaProducerFactory.getHealth();
        LOGGER.info(health3.toString());
        assertThat((List<?>)( health3.getDetails().get("producers") )).hasSize(2);
    }

    @NotNull
    private KafkaConfigDoc createKafkaConfigDoc(final String name, final String version) {
        final KafkaConfigDoc kafkaConfigDoc = new KafkaConfigDoc(
                KafkaConfigDoc.DOCUMENT_TYPE,
                UUID.randomUUID().toString(),
                name);
        kafkaConfigDoc.setVersion(version);
        kafkaConfigDoc.setData("bootstrap.servers=localhost:9092");
        return kafkaConfigDoc;
    }
}