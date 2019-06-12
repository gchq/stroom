package stroom.util;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class TestHealthCheckUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestHealthCheckUtils.class);

    @Test
    public void maskPasswords() {

        Map<String, Object> root = new HashMap<>();
        Map<String, Object> subMap = new HashMap<>();


        root.put("xxx", 1);
        root.put("yyy", "abc");
        root.put("abcPassword", "1234");
        root.put("passwordDEF", "1234");

        subMap.put("xxx", 1);
        subMap.put("yyy", "abc");
        subMap.put("abcPassword", "1234");
        subMap.put("passwordDEF", "1234");
        root.put("submap", subMap);

        HealthCheckUtils.maskPasswords(root);

        Assertions.assertThat(root.get("abcPassword")).isEqualTo("***");
        Assertions.assertThat(root.get("passwordDEF")).isEqualTo("***");

        Assertions.assertThat(((Map)root.get("submap")).get("abcPassword")).isEqualTo("***");
        Assertions.assertThat(((Map)root.get("submap")).get("passwordDEF")).isEqualTo("***");

        LOGGER.info("root map: {}", root);
        System.out.println(root.toString());

    }
}