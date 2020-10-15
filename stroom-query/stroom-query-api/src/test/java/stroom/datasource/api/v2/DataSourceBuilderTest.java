package stroom.datasource.api.v2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceBuilderTest {
    @Test
    void doeBuild() {
        // Given
        final String field0 = "field0";
        final String field1 = "field1";
        final String field2 = "field2";

        // When
        final DataSource dataSource = new DataSource.Builder()
                .addFields(new TextField(field0),
                        new TextField(field1),
                        new TextField(field2))
                .build();

        // Then
        assertThat(dataSource.getFields()).hasSize(3);
        assertThat(dataSource.getFields().get(0).getName()).isEqualTo(field0);
        assertThat(dataSource.getFields().get(1).getName()).isEqualTo(field1);
        assertThat(dataSource.getFields().get(2).getName()).isEqualTo(field2);
    }
}
