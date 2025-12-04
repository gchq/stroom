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

package stroom.core.meta;

import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.PipelineStore;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.suggestions.api.SuggestionsService;
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

@ExtendWith(MockitoExtension.class)
class TestMetaSuggestionsQueryHandler {

    @Mock
    private FeedStore feedStore;

    @Mock
    private MetaService metaService;

    @Mock
    private PipelineStore pipelineStore;

    @Mock
    private SuggestionsService suggestionsService;

    @Mock
    private DocRefInfoService docRefInfoService;

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
        final MetaSuggestionsQueryHandlerImpl queryHandler = new MetaSuggestionsQueryHandlerImpl(
                metaService, pipelineStore, securityContext, feedStore, taskContextFactory, docRefInfoService,
                suggestionsService, new ExpressionPredicateFactory());

        final String userInput = "feed";
        final FetchSuggestionsRequest request = new FetchSuggestionsRequest(
                MetaFields.STREAM_STORE_DOC_REF,
                MetaFields.FEED,
                userInput);

        Mockito.when(metaService.getFeeds())
                .thenReturn(metaFeedNames);

        Mockito.when(feedStore.list())
                .thenReturn(storeFeedNames.stream()
                        .map(name -> DocRef.builder().type(FeedDoc.TYPE).uuid(name).name(name).build())
                        .toList());

        return queryHandler.getSuggestions(request).getList();
    }
}
