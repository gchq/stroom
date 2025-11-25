package stroom.searchable.impl;

import stroom.docref.DocRef;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.LegacyDocRefConverter;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProvider;
import stroom.query.common.v2.SearchProviderRegistry;
import stroom.searchable.api.Searchable;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.ui.config.shared.UiConfig;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Singleton
public class SearchProviderRegistryImpl implements SearchProviderRegistry {

    private final Map<String, SearchProvider> searchProviderMap = new ConcurrentHashMap<>();


    @Inject
    SearchProviderRegistryImpl(final Executor executor,
                               final TaskManager taskManager,
                               final TaskContextFactory taskContextFactory,
                               final UiConfig clientConfig,
                               final CoprocessorsFactory coprocessorsFactory,
                               final ResultStoreFactory resultStoreFactory,
                               final SecurityContext securityContext,
                               final Set<SearchProvider> providers,
                               final Map<String, Searchable> searchables) {
        for (final SearchProvider provider : providers) {
            searchProviderMap.put(provider.getDataSourceType(), provider);
        }

        for (final Searchable searchable : searchables.values()) {
            final SearchableSearchProvider searchableSearchProvider =
                    new SearchableSearchProvider(executor,
                            taskManager,
                            taskContextFactory,
                            clientConfig,
                            coprocessorsFactory,
                            resultStoreFactory,
                            securityContext,
                            searchable);
            searchProviderMap.put(searchable.getDataSourceType(), searchableSearchProvider);
        }
    }

    @Override
    public Optional<SearchProvider> getSearchProvider(final DocRef dataSourceRef) {
        final DocRef docRef = LegacyDocRefConverter.convert(dataSourceRef);
        return Optional.ofNullable(searchProviderMap.get(docRef.getType()));
    }
}
