package stroom.query.api.v2;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FlatResultBuilderTest {
    @Test
    void doesBuild() {
        // Given
        final String componentId = "someComponentId";
        final String error = "something went wrong";

        final int numberFields = 3;
        final int numberResultSets = 10;

        // When
        final FlatResult.Builder flatResultBuilder = new FlatResult.Builder()
                .componentId(componentId)
                .error(error);
        IntStream.range(0, numberFields).forEach(x ->
                flatResultBuilder
                        .addField(new Field.Builder()
                                .id(String.format("id%d", x))
                                .name(String.format("field%d", x))
                                .expression("expression")
                                .build())
        );
        IntStream.range(0, numberResultSets).forEach(x -> {
            final List<Object> values = IntStream.range(0, numberFields).mapToObj(y ->
                    String.format("field%d_value%d", y, x)).collect(Collectors.toList());
            flatResultBuilder.addValues(values);
        });
        final FlatResult flatResult = flatResultBuilder.build();

        // Then
        assertThat(flatResult.getComponentId()).isEqualTo(componentId);
        assertThat(flatResult.getError()).isEqualTo(error);
        assertThat(flatResult.getSize()).isEqualTo(Long.valueOf(numberResultSets));

        final long fieldsCount = flatResult.getStructure().stream()
                .peek(field -> assertThat(field.getName().startsWith("field")).isTrue())
                .count();
        assertThat(fieldsCount).isEqualTo(numberFields);

        final long valuesCount = flatResult.getValues().stream().peek(values -> {
            final long vCount = values.stream()
                    .filter(o -> o instanceof String)
                    .map(o -> (String) o)
                    .peek(o -> assertThat(o.startsWith("field")).isTrue())
                    .count();
            assertThat(vCount).isEqualTo(numberFields);
        }).count();

        assertThat(valuesCount).isEqualTo(numberResultSets);
    }
}
