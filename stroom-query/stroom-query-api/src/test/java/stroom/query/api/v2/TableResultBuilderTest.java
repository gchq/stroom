package stroom.query.api.v2;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TableResultBuilderTest {
    @Test
    void doesBuild() {
        // Given
        final List<String> error = Collections.singletonList("Something went wrong");
        final String componentId = "someTabularComponentId";

        final Long offset = 30L;
        final Long length = 1000L;

        final Integer numberResults = 20;

        // When
        final TableResult.Builder builder = TableResult
                .builder()
                .componentId(componentId)
                .errors(error)
                .resultRange(OffsetRange
                        .builder()
                        .offset(offset)
                        .length(length)
                        .build());

        final List<Row> rows = new ArrayList<>();
        IntStream.range(0, numberResults).forEach(x ->
                rows.add(Row.builder().groupKey(String.format("rowGroup%d", x)).build())
        );
        builder.rows(rows);

        final TableResult tableResult = builder.build();

        // Then
        assertThat(tableResult.getComponentId()).isEqualTo(componentId);
        assertThat(tableResult.getErrors()).isEqualTo(error);
        assertThat(tableResult.getResultRange().getOffset()).isEqualTo(offset);
        assertThat(tableResult.getResultRange().getLength()).isEqualTo(length);

        final long rowCount = tableResult.getRows().stream().peek(row ->
                assertThat(row.getGroupKey().startsWith("rowGroup")).isTrue()
        ).count();
        assertThat(rowCount).isEqualTo((long) numberResults);
        assertThat(tableResult.getTotalResults()).isEqualTo(numberResults);
    }
}
