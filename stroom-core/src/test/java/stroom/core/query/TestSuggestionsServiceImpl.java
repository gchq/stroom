package stroom.core.query;

import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.meta.api.MetaService;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContextFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ExtendWith(MockitoExtension.class)
class TestSuggestionsServiceImpl {

    @Mock
    private FeedStore feedStore;

    @Mock
    private MetaService metaService;

    @Mock
    private PipelineStore pipelineStore;

    private final TaskContextFactory taskContextFactory = new SimpleTaskContextFactory();
    private final SecurityContext securityContext = new MockSecurityContext();

    @Test
    void testFeedNameSuggestions1() {
        final Set<String> metaFeedNames = Set.of(
                "meta feed 1",
                "meta feed 2",
                "meta feed 3",
                "won't match 1",
                "common feed 1",
                "common feed 2");

        final Set<String> storeFeedNames = Set.of(
                "store feed 1",
                "store feed 2",
                "store feed 3",
                "won't match 2",
                "common feed 1",
                "common feed 2");

        final List<String> matches = doFeedNameTest(metaFeedNames, storeFeedNames);

        Assertions.assertThat(matches)
                .containsExactlyInAnyOrder(
                        "meta feed 1",
                        "meta feed 2",
                        "meta feed 3",
                        "store feed 1",
                        "store feed 2",
                        "store feed 3",
                        "common feed 1",
                        "common feed 2");
    }

    @Test
    void testFeedNameSuggestions2() {
        final Set<String> metaFeedNames = Collections.emptySet();

        final Set<String> storeFeedNames = Set.of(
                "store feed 1",
                "store feed 2",
                "store feed 3",
                "common feed 1",
                "common feed 2");

        final List<String> matches = doFeedNameTest(metaFeedNames, storeFeedNames);

        Assertions.assertThat(matches)
                .containsExactlyInAnyOrder(
                        "store feed 1",
                        "store feed 2",
                        "store feed 3",
                        "common feed 1",
                        "common feed 2");
    }

    @Test
    void testFeedNameSuggestions3() {
        final Set<String> metaFeedNames = Set.of(
                "meta feed 1",
                "meta feed 2",
                "meta feed 3",
                "common feed 1",
                "common feed 2");

        final Set<String> storeFeedNames = Collections.emptySet();

        final List<String> matches = doFeedNameTest(metaFeedNames, storeFeedNames);

        Assertions.assertThat(matches)
                .containsExactlyInAnyOrder(
                        "meta feed 1",
                        "meta feed 2",
                        "meta feed 3",
                        "common feed 1",
                        "common feed 2");
    }

    @Test
    void testFeedNameSuggestions4() {
        final Set<String> metaFeedNames = Collections.emptySet();

        final Set<String> storeFeedNames = Collections.emptySet();

        final List<String> matches = doFeedNameTest(metaFeedNames, storeFeedNames);

        Assertions.assertThat(matches)
                .isEmpty();
    }

    private List<String> doFeedNameTest(final Set<String> metaFeedNames, final Set<String> storeFeedNames) {
        final SuggestionsService suggestionsService = new SuggestionsServiceImpl(
                metaService, pipelineStore, securityContext, feedStore, taskContextFactory);

        final String userInput = "feed";
        final FetchSuggestionsRequest request = new FetchSuggestionsRequest(
                MetaFields.STREAM_STORE_DOC_REF,
                MetaFields.FEED_NAME,
                userInput);


        Mockito.when(metaService.getFeeds())
                .thenReturn(metaFeedNames);

        Mockito.when(feedStore.list())
                .thenReturn(storeFeedNames.stream()
                        .map(name -> new DocRef.Builder().name(name).build())
                        .collect(Collectors.toList()));

        return suggestionsService.fetch(request);
    }
}