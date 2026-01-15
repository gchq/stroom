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

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TableResultBuilderTest {

    @Test
    void doesBuild() {
        // Given
        final List<ErrorMessage> error = Collections.singletonList(
                new ErrorMessage(Severity.ERROR, "Something went wrong"));
        final String componentId = "someTabularComponentId";

        final Long offset = 30L;
        final Long length = 1000L;

        final Integer numberResults = 20;

        // When
        final TableResultBuilder builder = TableResult
                .builder()
                .componentId(componentId)
                .errorMessages(error)
                .resultRange(OffsetRange
                        .builder()
                        .offset(offset)
                        .length(length)
                        .build());

        IntStream.range(0, numberResults).forEach(x ->
                builder.addRow(Row.builder().groupKey(String.format("rowGroup%d", x)).build())
        );

        final TableResult tableResult = builder.build();

        // Then
        assertThat(tableResult.getComponentId()).isEqualTo(componentId);
        assertThat(tableResult.getErrorMessages()).isEqualTo(error);
        assertThat(tableResult.getResultRange().getOffset()).isEqualTo(offset);
        assertThat(tableResult.getResultRange().getLength()).isEqualTo(length);

        final long rowCount = tableResult.getRows().stream().peek(row ->
                assertThat(row.getGroupKey().startsWith("rowGroup")).isTrue()
        ).count();
        assertThat(rowCount).isEqualTo((long) numberResults);
        assertThat(tableResult.getTotalResults().intValue()).isEqualTo(numberResults);
    }
}
