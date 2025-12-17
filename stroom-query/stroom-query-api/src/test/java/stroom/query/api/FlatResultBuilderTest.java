/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.api;

import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FlatResultBuilderTest {

    @Test
    void doesBuild() {
        // Given
        final String componentId = "someComponentId";
        final List<ErrorMessage> errorMessages = Collections.singletonList(
                new ErrorMessage(Severity.ERROR, "something went wrong"));

        final int numberFields = 3;
        final int numberResultSets = 10;

        // When
        final FlatResultBuilder flatResultBuilder = FlatResult
                .builder()
                .componentId(componentId)
                .errorMessages(errorMessages);
        final List<Column> columns = new ArrayList<>();
        IntStream.range(0, numberFields).forEach(x ->
                columns
                        .add(Column
                                .builder()
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

        flatResultBuilder.structure(columns);
        final FlatResult flatResult = flatResultBuilder.build();

        // Then
        assertThat(flatResult.getComponentId()).isEqualTo(componentId);
        assertThat(flatResult.getErrorMessages()).isEqualTo(errorMessages);
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
