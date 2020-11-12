package stroom.query.api.v2;

import org.junit.jupiter.api.Test;
import stroom.docref.DocRef;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SearchRequestBuilderTest {
    @Test
    void doesBuild() {
        // Given
        final String dateTimeLocale = "en-gb";
        final boolean incremental = true;
        final String queryKeyUUID = UUID.randomUUID().toString();
        final String resultRequestComponentId0 = "someResultComponentId0";
        final String resultRequestComponentId1 = "someResultComponentId1";
        final String dataSourceUuid = UUID.randomUUID().toString();

        // When
        final SearchRequest searchRequest = new SearchRequest.Builder()
                .query(new Query.Builder()
                        .dataSource(new DocRef.Builder()
                                .uuid(dataSourceUuid)
                                .build())
                        .build())
                .dateTimeLocale(dateTimeLocale)
                .incremental(incremental)
                .key(queryKeyUUID)
                .addResultRequests(new ResultRequest.Builder()
                        .componentId(resultRequestComponentId0)
                        .build())
                .addResultRequests(new ResultRequest.Builder()
                        .componentId(resultRequestComponentId1)
                        .build())
                .build();

        // Then
        assertThat(searchRequest.getDateTimeLocale()).isEqualTo(dateTimeLocale);
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
