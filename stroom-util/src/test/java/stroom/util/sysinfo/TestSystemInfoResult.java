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

package stroom.util.sysinfo;

import stroom.util.json.JsonUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

class TestSystemInfoResult {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSystemInfoResult.class);

    @Test
    void test() {

        final SystemInfoResult systemInfoResult = SystemInfoResult.builder()
                .name("name1")
                .addDetail("key1", "value1")
                .addDetail("key2", "value2")
                .build();

        LOGGER.info("systemInfo {}", systemInfoResult);
    }

    @Test
    void testSerde() throws IOException {
        final SystemInfoResult systemInfoResult = SystemInfoResult.builder()
                .name("name1")
                .addDetail("key1", "value1")
                .addDetail("key2", "value2")
                .addDetail("key3", Map.of(
                        "subKey1", "subVal1",
                        "subKey2", "subVal2"))
                .build();

        final String json = JsonUtil.writeValueAsString(systemInfoResult);
        LOGGER.info("json:\n{}", json);

//        final String json2 = objectMapper.writeValueAsString(systemInfoResult.getInfoMap());
//
//        LOGGER.info("json:\n{}", json2);

        final SystemInfoResult systemInfoResult2 = JsonUtil.readValue(json, SystemInfoResult.class);

        Assertions.assertThat(systemInfoResult2)
                .isEqualTo(systemInfoResult);
    }

    @Test
    void testSerde2() throws IOException {
        final SystemInfoResult systemInfoResult1 = SystemInfoResult.builder()
                .name("name1")
                .addDetail("key1", "value1")
                .addDetail("key2", "value2")
                .addDetail("key3", Map.of(
                        "subKey1", "subVal1",
                        "subKey2", "subVal2"))
                .build();

        final SystemInfoResult systemInfoResult2 = SystemInfoResult.builder()
                .name("name2")
                .addDetail("key1", "value1")
                .addDetail("key2", "value2")
                .addDetail("key3", Map.of(
                        "subKey1", "subVal1",
                        "subKey2", "subVal2"))
                .build();

        final SystemInfoResultList systemInfoResultList = new SystemInfoResultList(List.of(
                systemInfoResult1, systemInfoResult2));

        final String json = JsonUtil.writeValueAsString(systemInfoResultList);

        LOGGER.info("json:\n{}", json);

//        final String json2 = objectMapper.writeValueAsString(systemInfoResult.getInfoMap());
//
//        LOGGER.info("json:\n{}", json2);

        final SystemInfoResultList systemInfoResultList2 = JsonUtil.readValue(json, SystemInfoResultList.class);

        Assertions.assertThat(systemInfoResultList2)
                .isEqualTo(systemInfoResultList);
    }

//    @Test
//    void testSerde_merged() throws IOException {
//        final SystemInfoResult systemInfoResult1 = SystemInfoResult.builder("name1")
//                .withDetail("key1", "value1")
//                .withDetail("key2", "value2")
//                .withDetail("key3", Map.of(
//                        "subKey1", "subVal1",
//                        "subKey2", "subVal2"))
//                .build();
//
//        final SystemInfoResult systemInfoResult2 = SystemInfoResult.builder("name2")
//                .withDetail("key1", "value1")
//                .withDetail("key2", "value2")
//                .withDetail("key3", Map.of(
//                        "subKey1", "subVal1",
//                        "subKey2", "subVal2"))
//                .build();
//
//        final SystemInfoResult mergedSystemInfoResult = SystemInfoResult.merge("all", List.of(
//                systemInfoResult1, systemInfoResult2));
//
//        final ObjectMapper objectMapper = JsonUtil.getMapper();
//        final String json = objectMapper.writeValueAsString(mergedSystemInfoResult);
//
//        LOGGER.info("json:\n{}", json);
//
////        final String json2 = objectMapper.writeValueAsString(systemInfoResult.getInfoMap());
////
////        LOGGER.info("json:\n{}", json2);
//
//        SystemInfoResult mergedSystemInfoResult2 = objectMapper.readValue(json, SystemInfoResult.class);
//
//        Assertions.assertThat(mergedSystemInfoResult2)
//                .isEqualTo(mergedSystemInfoResult);
//    }
}
