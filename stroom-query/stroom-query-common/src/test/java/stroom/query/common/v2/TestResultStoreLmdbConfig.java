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

package stroom.query.common.v2;

import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestResultStoreLmdbConfig {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestResultStoreLmdbConfig.class);

    @Test
    void testJsonSerialisation() {
        final ResultStoreLmdbConfig lmdbConfig = new ResultStoreLmdbConfig();
        final String json = JsonUtil.writeValueAsString(lmdbConfig);
        LOGGER.info("json:\n{}", json);

        Assertions.assertThat(json)
                .contains("localDir");
        Assertions.assertThat(json)
                .doesNotContain("readerBlockedByWriter");
    }

    @Test
    void testJsonDeserialisation() {
        final String json = """
                {
                    "localDir":"my_dir",
                    "maxReaders":99,
                    "maxStoreSize":"10G",
                    "readAheadEnabled":true,
                    "readerBlockedByWriter": true
                }
                 """;

        final ResultStoreLmdbConfig lmdbConfig = JsonUtil.readValue(json, ResultStoreLmdbConfig.class);

        // Not a json prop so value above is ignored, but jackson doesn't seem to throw an
        // exception for the prop that should be an ignored one.
        Assertions.assertThat(lmdbConfig.isReaderBlockedByWriter())
                .isFalse();

        Assertions.assertThat(lmdbConfig.getLocalDir())
                .isEqualTo("my_dir");
    }
}
