package stroom.util.string;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestStringIdUtil {

    @Test
    void test() {
        assertThat(StringIdUtil.idToString(0)).isEqualTo("000");
        assertThat(StringIdUtil.idToString(1)).isEqualTo("001");
        assertThat(StringIdUtil.idToString(999)).isEqualTo("999");
        assertThat(StringIdUtil.idToString(1000)).isEqualTo("001000");
        assertThat(StringIdUtil.idToString(999999)).isEqualTo("999999");
        assertThat(StringIdUtil.idToString(1000000)).isEqualTo("001000000");
    }
}
