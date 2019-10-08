package stroom.kafka.impl;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.hadoop.hbase.util.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.impl.DocStoreModule;
import stroom.docstore.impl.Persistence;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.security.api.SecurityContext;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class TestKafkaConfigSerialiser {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestKafkaConfigSerialiser.class);

    @Mock
    private SecurityContext securityContext;
    @Mock
    private Persistence persistence;

    @Inject
    private KafkaConfigSerialiser serialiser;

    @BeforeEach
    void setUp() {
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                super.configure();

                bind(SecurityContext.class).toInstance(securityContext);
                bind(Persistence.class).toInstance(persistence);
                install(new DocStoreModule());
            }
        });

        injector.injectMembers(this);
    }

    @Test
    void name() throws IOException {

        KafkaConfigDoc kafkaConfigDoc = new KafkaConfigDoc();
        kafkaConfigDoc.setDescription("My description");

        kafkaConfigDoc.addProperty("Integer", 123);
        kafkaConfigDoc.addProperty("Short", (short)123);
        kafkaConfigDoc.addProperty("Long", 123L);
        kafkaConfigDoc.addProperty("BooleanTrue", true);
        kafkaConfigDoc.addProperty("BooleanFalse", false);
        kafkaConfigDoc.addProperty("Class", Object.class);
        kafkaConfigDoc.addProperty("String", "A string");

        final Map<String, byte[]> data = serialiser.write(kafkaConfigDoc);

        String json = Bytes.toString(data.get("meta"));

        LOGGER.info(json);

        KafkaConfigDoc kafkaConfigDoc2 = serialiser.read(data);

        Assertions.assertThat(kafkaConfigDoc)
                .isEqualTo(kafkaConfigDoc2);
    }
}
