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
