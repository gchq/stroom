package stroom.meta.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestCIStringHashMap {

    @Test
    void testKey() {
        final CIStringHashMap map = new CIStringHashMap();
        map.put("Test", "Test");
        assertThat(map.get("test")).isEqualTo("Test");
        assertThat(map.getKey("test")).isEqualTo("Test");
    }
}
