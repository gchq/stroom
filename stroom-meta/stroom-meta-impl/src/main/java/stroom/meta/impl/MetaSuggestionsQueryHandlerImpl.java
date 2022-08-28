package stroom.meta.impl;

import stroom.core.query.SuggestionsQueryHandler;
import stroom.core.query.SuggestionsService;
import stroom.datasource.api.v2.AbstractField;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.meta.api.MetaService;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.pipeline.PipelineStore;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.filter.QuickFilterPredicateFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

@Singleton
public class MetaSuggestionsQueryHandlerImpl implements MetaSuggestionsQueryHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetaSuggestionsQueryHandlerImpl.class);
    private static final int LIMIT = 20;

    private final MetaService metaService;
    private final PipelineStore pipelineStore;
    private final SecurityContext securityContext;
    private final FeedStore feedStore;
    private final TaskContextFactory taskContextFactory;

    // This may need changing if we have suggestions that are not for the stream store data source
    private final Map<String, Function<String, List<String>>> fieldNameToFunctionMap = Map.of(
            MetaFields.FEED.getName(), this::createFeedList,
            MetaFields.PIPELINE.getName(), this::createPipelineList,
            MetaFields.TYPE.getName(), this::createStreamTypeList,
            MetaFields.STATUS.getName(), this::createStatusList);

    @SuppressWarnings("unused")
    @Inject
    MetaSuggestionsQueryHandlerImpl(final MetaService metaService,
                                    final PipelineStore pipelineStore,
                                    final SecurityContext securityContext,
                                    final FeedStore feedStore,
                                    final TaskContextFactory taskContextFactory,
                                    final SuggestionsService suggestionsService) {
        this.metaService = metaService;
        this.pipelineStore = pipelineStore;
        this.securityContext = securityContext;
        this.feedStore = feedStore;
        this.taskContextFactory = taskContextFactory;

        suggestionsService.registerHandler(MetaFields.STREAM_STORE_TYPE, this);
    }

    @Override
    public CompletableFuture<List<String>> getSuggestions(final DocRef dataSource, final AbstractField field,
                                                          final String query) {
        return CompletableFuture.supplyAsync(() -> securityContext.secureResult(() -> {
            List<String> result = Collections.emptyList();

            final String fieldName = field.getName();
            final Function<String, List<String>> suggestionFunc = fieldNameToFunctionMap.get(fieldName);
            if (suggestionFunc != null) {
                result = suggestionFunc.apply(query);
            }
            return result;
        }));
    }

    @NotNull
    private List<String> createPipelineList(final String userInput) {
        final List<String> result;
        final Stream<String> stream = pipelineStore.list().stream()
                .map(DocRef::getName);
        result = QuickFilterPredicateFactory.filterStream(userInput, stream)
                .limit(LIMIT)
                .collect(Collectors.toList());

        return result;
    }

    @NotNull
    private List<String> createStatusList(final String userInput) {
        final List<String> result;
        Stream<String> stream = Arrays.stream(Status.values())
                .map(Status::getDisplayValue);
        result = QuickFilterPredicateFactory.filterStream(userInput, stream)
                .limit(LIMIT)
                .collect(Collectors.toList());
        return result;
    }

    private List<String> createFeedList(final String userInput) {
        // TODO this seems pretty inefficient as each call hits the db to get ALL feeds
        //   then limits/filters in java.  Needs to work off a cached feed name list

        return taskContextFactory.contextResult("Get all feed names", parentTaskContext ->
                createFeedList(parentTaskContext, userInput)).get();
    }

    private List<String> createFeedList(final TaskContext parentTaskContext, final String userInput) {
        // To get a list of feed names we need to combine the names from the meta service
        // and the feed store. Meta service only has feeds which have data, but may contain
        // feeds that have been deleted as docs.
        final CompletableFuture<Set<String>> metaFeedsFuture = CompletableFuture.supplyAsync(
                taskContextFactory.contextResult(
                        "Get meta feed names",
                        taskContext -> metaService.getFeeds()));

        final CompletableFuture<List<String>> docFeedsFuture = CompletableFuture.supplyAsync(
                taskContextFactory.contextResult(
                        "Get doc feed names",
                        taskContext ->
                                feedStore.list()
                                        .stream()
                                        .map(DocRef::getName)
                                        .collect(Collectors.toList())));

        try {
            // Make async calls to get the two lists then combine
            return metaFeedsFuture
                    .thenCombine(docFeedsFuture, (metaFeedNames, docFeedNames) ->
                            QuickFilterPredicateFactory.filterStream(
                                            userInput,
                                            Stream.concat(metaFeedNames.stream(), docFeedNames.stream())
                                                    .parallel())
                                    .distinct()
                                    .limit(LIMIT)
                                    .collect(Collectors.toList()))
                    .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Thread interrupted", e);
            return Collections.emptyList();
        } catch (ExecutionException e) {
            throw new RuntimeException("Error getting feed name suggestions: " + e.getMessage(), e);
        }
    }

    private List<String> createStreamTypeList(final String userInput) {
        return QuickFilterPredicateFactory.filterStream(
                        userInput, metaService.getTypes().stream())
                .limit(LIMIT)
                .collect(Collectors.toList());
    }
}
