package stroom.util;

import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.sysinfo.SystemInfoResultList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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

        SystemInfoResult systemInfoResult = SystemInfoResult.builder("name1")
                .withDetail("key1", "value1")
                .withDetail("key2", "value2")
                .build();

        LOGGER.info("systemInfo {}", systemInfoResult);
    }

    @Test
    void testSerde() throws IOException {
        SystemInfoResult systemInfoResult = SystemInfoResult.builder("name1")
                .withDetail("key1", "value1")
                .withDetail("key2", "value2")
                .withDetail("key3", Map.of(
                        "subKey1", "subVal1",
                        "subKey2", "subVal2"))
                .build();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        final String json = objectMapper.writeValueAsString(systemInfoResult);


        LOGGER.info("json:\n{}", json);

//        final String json2 = objectMapper.writeValueAsString(systemInfoResult.getInfoMap());
//
//        LOGGER.info("json:\n{}", json2);

        SystemInfoResult systemInfoResult2 = objectMapper.readValue(json, SystemInfoResult.class);

        Assertions.assertThat(systemInfoResult2)
                .isEqualTo(systemInfoResult);
    }

    @Test
    void testSerde2() throws IOException {
        final SystemInfoResult systemInfoResult1 = SystemInfoResult.builder("name1")
                .withDetail("key1", "value1")
                .withDetail("key2", "value2")
                .withDetail("key3", Map.of(
                        "subKey1", "subVal1",
                        "subKey2", "subVal2"))
                .build();

        final SystemInfoResult systemInfoResult2 = SystemInfoResult.builder("name2")
                .withDetail("key1", "value1")
                .withDetail("key2", "value2")
                .withDetail("key3", Map.of(
                        "subKey1", "subVal1",
                        "subKey2", "subVal2"))
                .build();

        final SystemInfoResultList systemInfoResultList = new SystemInfoResultList(List.of(
                systemInfoResult1, systemInfoResult2));

        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        final String json = objectMapper.writeValueAsString(systemInfoResultList);

        LOGGER.info("json:\n{}", json);

//        final String json2 = objectMapper.writeValueAsString(systemInfoResult.getInfoMap());
//
//        LOGGER.info("json:\n{}", json2);

        SystemInfoResultList systemInfoResultList2 = objectMapper.readValue(json, SystemInfoResultList.class);

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
//        final ObjectMapper objectMapper = new ObjectMapper();
//        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
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