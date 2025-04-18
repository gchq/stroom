package stroom.query.api.datasource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceFieldBuilderTest {

    @Test
    void doesBuild() {
        // Given
        final String name = "someField";

        // When
        final QueryField field = QueryField.createId(name);

        // Then
        assertThat(field.getFldName()).isEqualTo(name);
    }
}
