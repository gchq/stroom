package stroom.query.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilterBuilderTest {
    @Test
    void doesBuild() {
        final String excludes = "stuff to exclude **";
        final String includes = "stuff to include &&";

        final IncludeExcludeFilter filter = IncludeExcludeFilter
                .builder()
                .excludes(excludes)
                .includes(includes)
                .build();

        assertThat(filter.getExcludes()).isEqualTo(excludes);
        assertThat(filter.getIncludes()).isEqualTo(includes);
    }
}
