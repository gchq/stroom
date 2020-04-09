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
import stroom.kafka.api.KafkaProducerSupplier;
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

        final KafkaProducerSupplier kafkaProducerSupplier = kafkaProducerFactory.getSupplier(docRef);

        // Cache knows nothing of the doc so there cannot be a producer
        assertThat(kafkaProducerSupplier.getKafkaProducer()).isEmpty();
        assertThat(kafkaProducerSupplier.hasKafkaProducer()).isFalse();

        // Check we can still close with an empty supplier
        kafkaProducerSupplier.close();
    }

    @Test
    public void getSupplier_multipleCloseCalls() {

        final KafkaProducerFactory kafkaProducerFactory = new KafkaProducerFactoryImpl(kafkaConfigDocCache);

        final KafkaConfigDoc kafkaConfigDoc = createKafkaConfigDoc("Config1", "v1");

        final DocRef docRef = DocRefUtil.create(kafkaConfigDoc);

        final KafkaProducerSupplier kafkaProducerSupplier = kafkaProducerFactory.getSupplier(docRef);

        // Cache knows nothing of the doc so there cannot be a producer
        assertThat(kafkaProducerSupplier.getKafkaProducer()).isEmpty();
        assertThat(kafkaProducerSupplier.hasKafkaProducer()).isFalse();

        // Check we can still close with an empty supplier
        kafkaProducerSupplier.close();

        // it is closed so closing again will throw
        Assertions.assertThatThrownBy(kafkaProducerSupplier::close)
                .isInstanceOf(RuntimeException.class);

        // it is closed so getting again will throw
        Assertions.assertThatThrownBy(kafkaProducerSupplier::getKafkaProducer)
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

        final KafkaProducerSupplier kafkaProducerSupplier1 = kafkaProducerFactory.getSupplier(docRef1);

        assertThat(kafkaProducerSupplier1.getKafkaProducer()).isPresent();
        assertThat(kafkaProducerSupplier1.hasKafkaProducer()).isTrue();
        assertThat(kafkaProducerSupplier1.getConfigName()).isEqualTo(kafkaConfigDoc1.getName());
        assertThat(kafkaProducerSupplier1.getConfigUuid()).isEqualTo(kafkaConfigDoc1.getUuid());
        assertThat(kafkaProducerSupplier1.getConfigVersion()).isEqualTo(kafkaConfigDoc1.getVersion());

        KafkaProducer<String, byte[]> kafkaProducer1 = kafkaProducerSupplier1.getKafkaProducer().get();

        assertThat(kafkaProducer1).isNotNull();

        // Now get the second one

        final KafkaProducerSupplier kafkaProducerSupplier2 = kafkaProducerFactory.getSupplier(docRef2);

        assertThat(kafkaProducerSupplier2.getKafkaProducer()).isPresent();
        assertThat(kafkaProducerSupplier2.hasKafkaProducer()).isTrue();
        assertThat(kafkaProducerSupplier2.getConfigName()).isEqualTo(kafkaConfigDoc2.getName());
        assertThat(kafkaProducerSupplier2.getConfigUuid()).isEqualTo(kafkaConfigDoc2.getUuid());
        assertThat(kafkaProducerSupplier2.getConfigVersion()).isEqualTo(kafkaConfigDoc2.getVersion());

        KafkaProducer<String, byte[]> kafkaProducer2 = kafkaProducerSupplier2.getKafkaProducer().get();

        assertThat(kafkaProducer2).isNotNull();

        assertThat(kafkaProducer1).isNotSameAs(kafkaProducer2);

        // Now get the first one again to check it is the same KP instance

        final KafkaProducerSupplier kafkaProducerSupplier1b = kafkaProducerFactory.getSupplier(docRef1);

        assertThat(kafkaProducerSupplier1b.getKafkaProducer()).isPresent();
        assertThat(kafkaProducerSupplier1b.hasKafkaProducer()).isTrue();
        assertThat(kafkaProducerSupplier1b.getConfigName()).isEqualTo(kafkaConfigDoc1.getName());
        assertThat(kafkaProducerSupplier1b.getConfigUuid()).isEqualTo(kafkaConfigDoc1.getUuid());
        assertThat(kafkaProducerSupplier1b.getConfigVersion()).isEqualTo(kafkaConfigDoc1.getVersion());

        KafkaProducer<String, byte[]> kafkaProducer1b = kafkaProducerSupplier1b.getKafkaProducer().get();

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

        final KafkaProducerSupplier kafkaProducerSupplier1 = kafkaProducerFactory.getSupplier(docRef1);

        assertThat(kafkaProducerSupplier1.getKafkaProducer()).isPresent();
        assertThat(kafkaProducerSupplier1.hasKafkaProducer()).isTrue();
        assertThat(kafkaProducerSupplier1.getConfigName()).isEqualTo(kafkaConfigDoc1mk1.getName());
        assertThat(kafkaProducerSupplier1.getConfigUuid()).isEqualTo(kafkaConfigDoc1mk1.getUuid());
        assertThat(kafkaProducerSupplier1.getConfigVersion()).isEqualTo(kafkaConfigDoc1mk1.getVersion());

        final KafkaProducer<String, byte[]> kafkaProducer1 = kafkaProducerSupplier1.getKafkaProducer().get();

        assertThat(kafkaProducer1).isNotNull();

        // create an updated version of the doc with the same uuid
        final KafkaConfigDoc kafkaConfigDoc1mk2 = createKafkaConfigDoc("Config1", "v2");
        kafkaConfigDoc1mk2.setUuid(kafkaConfigDoc1mk1.getUuid());

        Mockito.when(kafkaConfigDocCache.get(Mockito.eq(docRef1)))
                .thenReturn(Optional.of(kafkaConfigDoc1mk2));

        final KafkaProducerSupplier kafkaProducerSupplier1b = kafkaProducerFactory.getSupplier(docRef1);

        assertThat(kafkaProducerSupplier1b.getKafkaProducer()).isPresent();
        assertThat(kafkaProducerSupplier1b.hasKafkaProducer()).isTrue();
        assertThat(kafkaProducerSupplier1b.getConfigName()).isEqualTo(kafkaConfigDoc1mk1.getName());
        assertThat(kafkaProducerSupplier1b.getConfigUuid()).isEqualTo(kafkaConfigDoc1mk1.getUuid());
        assertThat(kafkaProducerSupplier1b.getConfigVersion()).isEqualTo(kafkaConfigDoc1mk2.getVersion());

        final KafkaProducer<String, byte[]> kafkaProducer1b = kafkaProducerSupplier1b.getKafkaProducer().get();

        assertThat(kafkaProducer1b).isNotNull();

        assertThat(kafkaProducer1b).isNotSameAs(kafkaProducer1);

        // Now get it again to check it is the same KP instance

        final KafkaProducerSupplier kafkaProducerSupplier1c = kafkaProducerFactory.getSupplier(docRef1);

        assertThat(kafkaProducerSupplier1c.getKafkaProducer()).isPresent();
        assertThat(kafkaProducerSupplier1c.hasKafkaProducer()).isTrue();
        assertThat(kafkaProducerSupplier1c.getConfigName()).isEqualTo(kafkaConfigDoc1mk1.getName());
        assertThat(kafkaProducerSupplier1c.getConfigUuid()).isEqualTo(kafkaConfigDoc1mk1.getUuid());
        assertThat(kafkaProducerSupplier1c.getConfigVersion()).isEqualTo(kafkaConfigDoc1mk2.getVersion());

        KafkaProducer<String, byte[]> kafkaProducer1c = kafkaProducerSupplier1c.getKafkaProducer().get();

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
        try (final KafkaProducerSupplier kafkaProducerSupplier1 = kafkaProducerFactory.getSupplier(docRef1)) {
            try (final KafkaProducerSupplier kafkaProducerSupplier2 = kafkaProducerFactory.getSupplier(docRef2)) {

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