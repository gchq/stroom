package stroom.pipeline.refdata;

import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestReferenceDataLmdbConfig {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestReferenceDataLmdbConfig.class);

    @Test
    void testJsonSerialisation() {
        final ReferenceDataLmdbConfig lmdbConfig = new ReferenceDataLmdbConfig();
        final String json = JsonUtil.writeValueAsString(lmdbConfig);
        LOGGER.info("json:\n{}", json);

        Assertions.assertThat(json)
                .contains("localDir");
        Assertions.assertThat(json)
                .contains("readerBlockedByWriter");
    }

    @Test
    void testJsonDeserialisation() {
        final String json = """
                {
                    "localDir":"my_dir",
                    "maxReaders":99,
                    "maxStoreSize":"10G",
                    "readAheadEnabled":true,
                    "readerBlockedByWriter": false
                }
                 """;

        final ReferenceDataLmdbConfig lmdbConfig = JsonUtil.readValue(json, ReferenceDataLmdbConfig.class);

        Assertions.assertThat(lmdbConfig.isReaderBlockedByWriter())
                .isFalse();

        Assertions.assertThat(lmdbConfig.getLocalDir())
                .isEqualTo("my_dir");
    }

//    @Test
//    void testJsonDeserialisation_sparse() throws IOException {
//
//        ObjectMapper objectMapper = new ObjectMapper()
//                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
//
//        final String json = """
//                {
//                    "localDir":"my_dir"
//                }
//                 """;
//
//        final ReferenceDataLmdbConfig lmdbConfig = objectMapper.readValue(json, ReferenceDataLmdbConfig.class);
//
//        Assertions.assertThat(lmdbConfig.getLocalDir())
//                .isEqualTo("my_dir");
//        Assertions.assertThat(lmdbConfig.getMaxReaders())
//                .isEqualTo(ReferenceDataLmdbConfig.DEFAULT_MAX_READERS);
//        Assertions.assertThat(lmdbConfig.getMaxStoreSize())
//                .isEqualTo(ReferenceDataLmdbConfig.DEFAULT_MAX_STORE_SIZE);
//        Assertions.assertThat(lmdbConfig.isReadAheadEnabled())
//                .isEqualTo(ReferenceDataLmdbConfig.DEFAULT_IS_READ_AHEAD_ENABLED);
//        Assertions.assertThat(lmdbConfig.isReaderBlockedByWriter())
//                .isEqualTo(ReferenceDataLmdbConfig.DEFAULT_IS_READER_BLOCKED_BY_WRITER);
//
//    }
}
