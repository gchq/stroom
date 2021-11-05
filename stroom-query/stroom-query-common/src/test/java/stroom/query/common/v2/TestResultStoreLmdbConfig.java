package stroom.query.common.v2;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class TestResultStoreLmdbConfig {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestResultStoreLmdbConfig.class);

    @Test
    void testJsonSerialisation() throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        ResultStoreLmdbConfig lmdbConfig = new ResultStoreLmdbConfig();

        final String json = objectMapper.writeValueAsString(lmdbConfig);

        LOGGER.info("json:\n{}", json);

        Assertions.assertThat(json)
                .contains("localDir");
        Assertions.assertThat(json)
                .doesNotContain("readerBlockedByWriter");
    }

    @Test
    void testJsonDeserialisation() throws IOException {

        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        final String json = """
                {
                    "localDir":"my_dir",
                    "maxReaders":99,
                    "maxStoreSize":"10G",
                    "readAheadEnabled":true,
                    "readerBlockedByWriter": true}
                 """;

        final ResultStoreLmdbConfig lmdbConfig = objectMapper.readValue(json, ResultStoreLmdbConfig.class);

        // Not a json prop so value above is ignored, but jackson doesn't seem to throw an
        // exception for the prop that should be an ignored one.
        Assertions.assertThat(lmdbConfig.isReaderBlockedByWriter())
                .isFalse();

        Assertions.assertThat(lmdbConfig.getLocalDir())
                .isEqualTo("my_dir");
    }

    @Test
    void testJsonDeserialisation_sparse() throws IOException {

        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        final String json = """
                {
                    "localDir":"my_dir"
                }
                 """;

        final ResultStoreLmdbConfig lmdbConfig = objectMapper.readValue(json, ResultStoreLmdbConfig.class);

        Assertions.assertThat(lmdbConfig.getLocalDir())
                .isEqualTo("my_dir");
        Assertions.assertThat(lmdbConfig.getMaxReaders())
                .isEqualTo(ResultStoreLmdbConfig.DEFAULT_MAX_READERS);
        Assertions.assertThat(lmdbConfig.getMaxStoreSize())
                .isEqualTo(ResultStoreLmdbConfig.DEFAULT_MAX_STORE_SIZE);
        Assertions.assertThat(lmdbConfig.isReadAheadEnabled())
                .isEqualTo(ResultStoreLmdbConfig.DEFAULT_IS_READ_AHEAD_ENABLED);
        Assertions.assertThat(lmdbConfig.isReaderBlockedByWriter())
                .isEqualTo(ResultStoreLmdbConfig.DEFAULT_IS_READER_BLOCKED_BY_WRITER);

    }
}
