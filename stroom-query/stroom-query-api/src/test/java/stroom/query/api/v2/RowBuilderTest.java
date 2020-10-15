package stroom.query.api.v2;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RowBuilderTest {
    @Test
    void doesBuild() {
        final Integer depth = 3;
        final List<String> values = Arrays.asList("qwerty", "asdfg");
        final String groupKey = "someGroup";

        final Row row = new Row.Builder()
                .depth(depth)
                .addValues(values.toArray(new String[2]))
                .groupKey(groupKey)
                .build();

        assertThat(row.getDepth()).isEqualTo(depth);
        assertThat(row.getGroupKey()).isEqualTo(groupKey);
        assertThat(row.getValues()).isEqualTo(values);
    }
}
