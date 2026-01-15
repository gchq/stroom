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

package stroom.data.zip;


import stroom.meta.api.AttributeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TestPathCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestPathCreator.class);

    private final AttributeMap attributeMap = new AttributeMap();

    @BeforeEach
    void setup() {
        attributeMap.put("feed", "myFeed");
        attributeMap.put("type1", "mytype1");
        attributeMap.put("type2", "mytype2");
    }

    @Test
    void testFindVars() {
        final String[] vars = PathCreator.findVars("/temp/${feed}-FLAT/${pipe}_less-${uuid}/${searchId}");
        assertThat(vars.length).isEqualTo(4);
        assertThat(vars[0]).isEqualTo("feed");
        assertThat(vars[1]).isEqualTo("pipe");
        assertThat(vars[2]).isEqualTo("uuid");
        assertThat(vars[3]).isEqualTo("searchId");
    }

    @Test
    void testReplace() {
        final String template = "someText_${type1}_someText_${feed}_someText_${type2}_someText";

        final String result = PathCreator.replaceAll(template, attributeMap);

        LOGGER.info("result: {}}", result);
        assertThat(result).isEqualTo("someText_mytype1_someText_myFeed_someText_mytype2_someText");
    }

    @Test
    void testReplaceTime() {
        final ZonedDateTime zonedDateTime = ZonedDateTime.of(2018, 8, 20, 13, 17, 22, 2111444, ZoneOffset.UTC);
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("feed", "TEST");

        String path = "${feed}/${year}/${year}-${month}/${year}-${month}-${day}/${pathId}/${id}";

        // Replace pathId variable with path id.
        path = PathCreator.replace(path, "pathId", "1234");
        // Replace id variable with file id.
        path = PathCreator.replace(path, "id", "5678");

        assertThat(path).isEqualTo("${feed}/${year}/${year}-${month}/${year}-${month}-${day}/1234/5678");

        path = PathCreator.replaceTimeVars(path, zonedDateTime);

        assertThat(path).isEqualTo("${feed}/2018/2018-08/2018-08-20/1234/5678");

        path = PathCreator.replaceAll(path, attributeMap);

        assertThat(path).isEqualTo("TEST/2018/2018-08/2018-08-20/1234/5678");
    }
}
