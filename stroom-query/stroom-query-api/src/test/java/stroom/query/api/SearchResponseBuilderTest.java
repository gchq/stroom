package stroom.query.api;

import stroom.query.api.SearchResponse.FlatResultBuilder;
import stroom.query.api.SearchResponse.TableResultBuilder;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchResponseBuilderTest {
    @Test
    void doesBuildFlat() {
        // Given
        final ErrorMessage error0 = new ErrorMessage(Severity.ERROR, "something went wrong 0");
        final ErrorMessage error1 = new ErrorMessage(Severity.ERROR, "something went wrong 1");
        final String highlight0 = "SOMETHING";
        final String flatResultComponentId0 = "flatResult0";
        final String flatResultComponentId1 = "flatResult1";
        final String flatResultComponentId2 = "flatResult2";

        // When
        final SearchResponse searchResponse = new FlatResultBuilder()
                .complete(true)
                .errorMessages(List.of(error0, error1))
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
        assertThat(searchResponse.getErrorMessages()).hasSize(2);
        assertThat(searchResponse.getComplete()).isTrue();
        assertThat(searchResponse.getErrorMessages().contains(error0)).isTrue();
        assertThat(searchResponse.getErrorMessages().contains(error1)).isTrue();
        assertThat(searchResponse.getHighlights()).hasSize(1);
        assertThat(searchResponse.getHighlights().get(0)).isEqualTo(highlight0);
        final long resultCount = searchResponse.getResults().stream()
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
        final ErrorMessage error0 = new ErrorMessage(Severity.ERROR, "something went wrong 0");
        final ErrorMessage error1 = new ErrorMessage(Severity.ERROR, "something went wrong 1");
        final String highlight0 = "SOMETHING";
        final String flatResultComponentId0 = "tableResult0";
        final String flatResultComponentId1 = "tableResult1";
        final String flatResultComponentId2 = "tableResult2";

        // When
        final SearchResponse searchResponse = new TableResultBuilder()
                .complete(true)
                .errorMessages(List.of(error0, error1))
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
        assertThat(searchResponse.getErrorMessages()).hasSize(2);
        assertThat(searchResponse.getComplete()).isTrue();
        assertThat(searchResponse.getErrorMessages().contains(error0)).isTrue();
        assertThat(searchResponse.getErrorMessages().contains(error1)).isTrue();
        assertThat(searchResponse.getHighlights()).hasSize(1);
        assertThat(searchResponse.getHighlights().get(0)).isEqualTo(highlight0);
        final long resultCount = searchResponse.getResults().stream()
                .filter(result -> result instanceof TableResult)
                .map(result -> (TableResult) result)
                .map(Result::getComponentId)
                .filter(s -> s.startsWith("tableResult"))
                .count();
        assertThat(resultCount).isEqualTo(3L);
    }
}
