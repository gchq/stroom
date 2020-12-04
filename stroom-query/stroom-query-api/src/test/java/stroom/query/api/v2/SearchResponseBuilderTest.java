package stroom.query.api.v2;

import stroom.query.api.v2.SearchResponse.FlatResultBuilder;
import stroom.query.api.v2.SearchResponse.TableResultBuilder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchResponseBuilderTest {
    @Test
    void doesBuildFlat() {
        // Given
        final String error0 = "something went wrong 0";
        final String error1 = "something went wrong 1";
        final String highlight0 = "SOMETHING";
        final String flatResultComponentId0 = "flatResult0";
        final String flatResultComponentId1 = "flatResult1";
        final String flatResultComponentId2 = "flatResult2";

        // When
        final SearchResponse searchResponse = new FlatResultBuilder()
                .complete(true)
                .errors(List.of(error0, error1))
                .highlights(List.of(highlight0))
                .results(List.of(
                        FlatResult.builder()
                                .componentId(flatResultComponentId0)
                                .build(),
                        FlatResult.builder()
                                .componentId(flatResultComponentId1)
                                .build(),
                        FlatResult.builder()
                                .componentId(flatResultComponentId2)
                                .build()))
                .build();

        // Then
        assertThat(searchResponse.getErrors()).hasSize(2);
        assertThat(searchResponse.getComplete()).isTrue();
        assertThat(searchResponse.getErrors().contains(error0)).isTrue();
        assertThat(searchResponse.getErrors().contains(error1)).isTrue();
        assertThat(searchResponse.getHighlights()).hasSize(1);
        assertThat(searchResponse.getHighlights().get(0)).isEqualTo(highlight0);
        long resultCount = searchResponse.getResults().stream()
                .filter(result -> result instanceof FlatResult)
                .map(result -> (FlatResult) result)
                .map(Result::getComponentId)
                .filter(s -> s.startsWith("flatResult"))
                .count();
        assertThat(resultCount).isEqualTo(3L);
    }

    @Test
    void doesBuildTable() {
        // Given
        final String error0 = "something went wrong 0";
        final String error1 = "something went wrong 1";
        final String highlight0 = "SOMETHING";
        final String flatResultComponentId0 = "tableResult0";
        final String flatResultComponentId1 = "tableResult1";
        final String flatResultComponentId2 = "tableResult2";

        // When
        final SearchResponse searchResponse = new TableResultBuilder()
                .complete(true)
                .errors(List.of(error0, error1))
                .highlights(List.of(highlight0))
                .results(List.of(
                        TableResult.builder()
                                .componentId(flatResultComponentId0)
                                .build(),
                        TableResult.builder()
                                .componentId(flatResultComponentId1)
                                .build(),
                        TableResult.builder()
                                .componentId(flatResultComponentId2)
                                .build()))
                .build();

        // Then
        assertThat(searchResponse.getErrors()).hasSize(2);
        assertThat(searchResponse.getComplete()).isTrue();
        assertThat(searchResponse.getErrors().contains(error0)).isTrue();
        assertThat(searchResponse.getErrors().contains(error1)).isTrue();
        assertThat(searchResponse.getHighlights()).hasSize(1);
        assertThat(searchResponse.getHighlights().get(0)).isEqualTo(highlight0);
        long resultCount = searchResponse.getResults().stream()
                .filter(result -> result instanceof TableResult)
                .map(result -> (TableResult) result)
                .map(Result::getComponentId)
                .filter(s -> s.startsWith("tableResult"))
                .count();
        assertThat(resultCount).isEqualTo(3L);
    }
}
