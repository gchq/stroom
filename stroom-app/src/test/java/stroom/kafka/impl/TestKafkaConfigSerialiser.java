/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.kafka.impl;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TestKafkaConfigSerialiser {

//    private static final Logger LOGGER = LoggerFactory.getLogger(TestKafkaConfigSerialiser.class);
//
//    @Mock
//    private SecurityContext securityContext;
//    @Mock
//    private Persistence persistence;
//
//    @Inject
//    private KafkaConfigSerialiser serialiser;
//
//    @BeforeEach
//    void setUp() {
//        Injector injector = Guice.createInjector(new AbstractModule() {
//            @Override
//            protected void configure() {
//                super.configure();
//
//                bind(SecurityContext.class).toInstance(securityContext);
//                bind(Persistence.class).toInstance(persistence);
//                install(new DocStoreModule());
//            }
//        });
//
//        injector.injectMembers(this);
//    }
//
//    @Test
//    void testSerialiseDeserialise() throws IOException {
//
//        KafkaConfigDoc kafkaConfigDoc = new KafkaConfigDoc();
//        kafkaConfigDoc.setDescription("My description");
//
//
//        final Map<String, byte[]> data = serialiser.write(kafkaConfigDoc);
//
//        String json = Bytes.toString(data.get("meta"));
//
//        LOGGER.info(json);
//
//        KafkaConfigDoc kafkaConfigDoc2 = serialiser.read(data);
//
//        Assertions.assertThat(kafkaConfigDoc)
//                .isEqualTo(kafkaConfigDoc2);
//
//        Properties props2 = KafkaProducerImpl.getProperties(kafkaConfigDoc2);
//        Assertions.assertThat(props2.get("Class2"))
//                .isInstanceOf(Class.class);
//        Assertions.assertThat(((Class)props2.get("Class2")).getName())
//                .isEqualTo(GoodClass.class.getName());
//    }
//
//    @Test
//    void testGoodType() throws IOException, ClassNotFoundException {
//        String json = "{\n" +
//                "  \"description\" : \"My description\",\n" +
//                "  \"properties\" : {\n" +
//                "    \"aStringValue\" : [ \"stringType\", \"abcdefg\" ],\n" +
//                "    \"aGoodClass\" : [ \"classType\", \"" +
//                "stroom.kafka.impl.TestKafkaConfigSerialiser$GoodClass\" ]\n" +
//                "  },\n" +
//                "  \"kafkaVersion\" : \"2.2.1\"\n" +
//                "}";
//
//        LOGGER.info("\n{}", json);
//
//        final Map<String, byte[]> data = Map.of("meta", Bytes.toBytes(json));
//        final KafkaConfigDoc kafkaConfigDoc = serialiser.read(data);
//
//        Properties props = KafkaProducerImpl.getProperties(kafkaConfigDoc);
//        Assertions.assertThat(props.get("aGoodClass"))
//                .isInstanceOf(Class.class);
//        Assertions.assertThat(((Class)props.get("aGoodClass")).getName())
//                .isEqualTo(GoodClass.class.getName());
//    }
//
//    @Test
//    void testBadType() throws IOException {
//        String json = "{\n" +
//                "  \"description\" : \"My description\",\n" +
//                "  \"properties\" : {\n" +
//                "    \"aStringValue\" : [ \"stringType\", \"abcdefg\" ],\n" +
//                // want to make sure jackson won't try to instantiate some unknown class
//                "    \"Bad\" : [ \"stroom.kafka.impl.TestKafkaConfigSerialiser.BadClass\", \"some value\" ],\n" +
//                "  },\n" +
//                "  \"kafkaVersion\" : \"2.2.1\"\n" +
//                "}";
//
//        final Map<String, byte[]> data = Map.of("meta", Bytes.toBytes(json));
//
//        Assertions.assertThatExceptionOfType(InvalidTypeIdException.class)
//                .isThrownBy(() -> {
//                    serialiser.read(data);
//                });
//    }
//
//    static class BadClass {
//        private static final Logger LOGGER = LoggerFactory.getLogger(BadClass.class);
//
//        BadClass() {
//            LOGGER.info("Ctor called for BadClass");
//        }
//    }
//
//    public static class GoodClass {
//        private static final Logger LOGGER = LoggerFactory.getLogger(GoodClass.class);
//
//        public GoodClass() {
//            LOGGER.info("Ctor called for GoodClass");
//        }
//    }

}
