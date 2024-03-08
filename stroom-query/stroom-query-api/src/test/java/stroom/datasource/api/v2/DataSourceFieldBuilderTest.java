package stroom.datasource.api.v2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceFieldBuilderTest {

    @Test
    void doesBuild() {
        // Given
        final String name = "someField";

        // When
        final TextField field = new TextField(name, ConditionSet.DEFAULT_ID, null, true);

        // Then
        assertThat(field.getName()).isEqualTo(name);
    }
}
