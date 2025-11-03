package stroom.query.api;

import stroom.docref.DocRef;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRequestFactoryTest {

    @Test
    void doesBuild() {
        // Given
        final DateTimeSettings dateTimeSettings = DateTimeSettings
                .builder()
                .build();
        final boolean incremental = true;
        final String queryKeyUUID = UUID.randomUUID().toString();
        final String resultRequestComponentId0 = "someResultComponentId0";
        final String resultRequestComponentId1 = "someResultComponentId1";
        final String dataSourceUuid = UUID.randomUUID().toString();

        // When
        final SearchRequest searchRequest = SearchRequest
                .builder()
                .query(Query
                        .builder()
                        .dataSource(DocRef
                                .builder()
                                .type("Index")
                                .uuid(dataSourceUuid)
                                .build())
                        .build())
                .dateTimeSettings(dateTimeSettings)
                .incremental(incremental)
                .key(queryKeyUUID)
                .addResultRequests(ResultRequest
                        .builder()
                        .componentId(resultRequestComponentId0)
                        .build())
                .addResultRequests(ResultRequest
                        .builder()
                        .componentId(resultRequestComponentId1)
                        .build())
                .build();

        // Then
        assertThat(searchRequest.getDateTimeSettings()).isEqualTo(dateTimeSettings);
        assertThat(searchRequest.getIncremental()).isEqualTo(incremental);
        assertThat(searchRequest.getKey()).isNotNull();
        assertThat(searchRequest.getKey().getUuid()).isEqualTo(queryKeyUUID);
        assertThat(searchRequest.getResultRequests()).hasSize(2);
        assertThat(searchRequest.getResultRequests().get(0).getComponentId()).isEqualTo(resultRequestComponentId0);
        assertThat(searchRequest.getResultRequests().get(1).getComponentId()).isEqualTo(resultRequestComponentId1);
        assertThat(searchRequest.getQuery()).isNotNull();
        assertThat(searchRequest.getQuery().getDataSource()).isNotNull();
        assertThat(searchRequest.getQuery().getDataSource().getUuid()).isEqualTo(dataSourceUuid);
    }
}
