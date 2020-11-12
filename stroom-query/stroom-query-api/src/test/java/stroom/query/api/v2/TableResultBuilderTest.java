package stroom.query.api.v2;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TableResultBuilderTest {
    @Test
    void doesBuild() {
        // Given
        final String error = "Something went wrong";
        final String componentId = "someTabularComponentId";

        final Long offset = 30L;
        final Long length = 1000L;

        final Integer numberResults = 20;

        // When
        final TableResult.Builder builder = new TableResult.Builder()
                .componentId(componentId)
                .error(error)
                .resultRange(new OffsetRange.Builder()
                        .offset(offset)
                        .length(length)
                        .build());

        IntStream.range(0, numberResults).forEach(x ->
                builder.addRows(new Row.Builder().groupKey(String.format("rowGroup%d", x)).build())
        );

        final TableResult tableResult = builder.build();

        // Then
        assertThat(tableResult.getComponentId()).isEqualTo(componentId);
        assertThat(tableResult.getError()).isEqualTo(error);
        assertThat(tableResult.getResultRange().getOffset()).isEqualTo(offset);
        assertThat(tableResult.getResultRange().getLength()).isEqualTo(length);

        final long rowCount = tableResult.getRows().stream().peek(row ->
                assertThat(row.getGroupKey().startsWith("rowGroup")).isTrue()
        ).count();
        assertThat(rowCount).isEqualTo((long) numberResults);
        assertThat(tableResult.getTotalResults()).isEqualTo(numberResults);
    }
}
