package stroom.query.api.v2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultRequestBuilderTest {
    @Test
    void doesBuild() {
        // Given
        final String componentId = "someComponent";
        final ResultRequest.Fetch fetch = ResultRequest.Fetch.CHANGES;
        final String openGroup0 = "someOpenGroup0";
        final String openGroup1 = "someOpenGroup1";
        final Long rangeLength = 70L;
        final Long rangeOffset = 1000L;
        final String queryId0 = "someQueryId0";
        final String queryId1 = "someQueryId1";
        final String queryId2 = "someQueryId2";

        // When
        final ResultRequest resultRequest = new ResultRequest.Builder()
                .componentId(componentId)
                .fetch(fetch)
                .addOpenGroups(openGroup0, openGroup1)
                .requestedRange(new OffsetRange.Builder()
                        .length(rangeLength)
                        .offset(rangeOffset)
                        .build())
                .addMappings(new TableSettings.Builder()
                        .queryId(queryId0)
                        .build())
                .addMappings(new TableSettings.Builder()
                        .queryId(queryId1)
                        .build())
                .addMappings(new TableSettings.Builder()
                        .queryId(queryId2)
                        .build())
                .build();

        // Then
        assertThat(resultRequest.getComponentId()).isEqualTo(componentId);
        assertThat(resultRequest.getFetch()).isEqualTo(fetch);

        assertThat(resultRequest.getOpenGroups()).hasSize(2);
        assertThat(resultRequest.getOpenGroups().contains(openGroup0)).isTrue();
        assertThat(resultRequest.getOpenGroups().contains(openGroup1)).isTrue();

        assertThat(resultRequest.getRequestedRange()).isNotNull();
        assertThat(resultRequest.getRequestedRange().getLength()).isEqualTo(rangeLength);
        assertThat(resultRequest.getRequestedRange().getOffset()).isEqualTo(rangeOffset);

        assertThat(resultRequest.getMappings()).hasSize(3);
        assertThat(resultRequest.getMappings().get(0).getQueryId()).isEqualTo(queryId0);
        assertThat(resultRequest.getMappings().get(1).getQueryId()).isEqualTo(queryId1);
        assertThat(resultRequest.getMappings().get(2).getQueryId()).isEqualTo(queryId2);
    }
}
