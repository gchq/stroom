package stroom.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestNextNameGenerator {

    @Test
    void simple() {
        final List<String> names = Arrays.asList("New group", "New group (1)", "New group (2)");
        assertThat(NextNameGenerator.getNextName(names, "New group"))
                .isEqualTo("New group (3)");
    }

    @Test
    void badNumbers() {
        final List<String> names = Arrays.asList("New group",
                "New group (1)",
                "New group (2)",
                "New group (x)",
                "New group (99)");
        assertThat(NextNameGenerator.getNextName(names, "New group"))
                .isEqualTo("New group (100)");
    }

    @Test
    void nothingNew() {
        final List<String> names = Arrays.asList("Some name", "Some other name (343)", "Another name");
        assertThat(NextNameGenerator.getNextName(names, "New group"))
                .isEqualTo("New group (1)");
    }

    @Test
    void empty() {
        final ArrayList<String> names = new ArrayList<String>();
        assertThat(NextNameGenerator.getNextName(names, "New group"))
                .isEqualTo("New group (1)");
    }
}
