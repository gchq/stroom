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

package stroom.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

class TestHealthCheckUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestHealthCheckUtils.class);

    @Test
    void maskPasswords() {
        final Map<String, Object> root = new HashMap<>();
        final Map<String, Object> subMap = new HashMap<>();

        root.put("xxx", 1);
        root.put("yyy", "abc");
        root.put("abcPassword", "1234");
        root.put("passwordDEF", "1234");
        root.put("aToken", "1234");
        root.put("aLongerToken", "1234567890");
        root.put("aApiKey", "1234");
        root.put("aLongerApiKey", "1234567890");

        subMap.put("xxx", 1);
        subMap.put("yyy", "abc");
        subMap.put("abcPassword", "1234");
        subMap.put("passwordDEF", "1234");
        root.put("submap", subMap);

        HealthCheckUtils.maskPasswords(root);

        Assertions.assertThat(root.get("abcPassword")).isEqualTo("****");
        Assertions.assertThat(root.get("passwordDEF")).isEqualTo("****");
        Assertions.assertThat(root.get("aToken")).isEqualTo("****");
        Assertions.assertThat(root.get("aLongerToken")).isEqualTo("1234****7890");
        Assertions.assertThat(root.get("aApiKey")).isEqualTo("****");
        Assertions.assertThat(root.get("aLongerApiKey")).isEqualTo("1234****7890");

        Assertions.assertThat(((Map<?, ?>) root.get("submap")).get("abcPassword")).isEqualTo("****");
        Assertions.assertThat(((Map<?, ?>) root.get("submap")).get("passwordDEF")).isEqualTo("****");

        LOGGER.info("root map: {}", root);
        System.out.println(root.toString());
    }
}
