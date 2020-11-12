package stroom.query.api.v2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilterBuilderTest {
    @Test
    void doesBuild() {
        final String excludes = "stuff to exclude **";
        final String includes = "stuff to include &&";

        final Filter filter = new Filter.Builder()
                .excludes(excludes)
                .includes(includes)
                .build();

        assertThat(filter.getExcludes()).isEqualTo(excludes);
        assertThat(filter.getIncludes()).isEqualTo(includes);
    }
}
