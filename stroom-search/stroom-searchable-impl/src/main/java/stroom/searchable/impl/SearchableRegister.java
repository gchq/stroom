package stroom.searchable.impl;

import stroom.datasource.api.v2.DataSourceProvider;
import stroom.query.common.v2.AdditionalDataSourceProviders;
import stroom.query.common.v2.AdditionalSearchProviders;
import stroom.query.common.v2.CoprocessorsFactory;
import stroom.query.common.v2.ResultStoreFactory;
import stroom.query.common.v2.SearchProvider;
import stroom.searchable.api.Searchable;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.ui.config.shared.UiConfig;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

@Singleton
public class SearchableRegister implements AdditionalDataSourceProviders, AdditionalSearchProviders {

    private final Set<DataSourceProvider> dataSourceProviders = new HashSet<>();
    private final Set<SearchProvider> searchProviders = new HashSet<>();

    @Inject
    SearchableRegister(final Executor executor,
                       final TaskManager taskManager,
                       final TaskContextFactory taskContextFactory,
                       final UiConfig clientConfig,
                       final CoprocessorsFactory coprocessorsFactory,
                       final ResultStoreFactory resultStoreFactory,
                       final SecurityContext securityContext,
                       final Set<Searchable> searchables) {
        for (final Searchable searchable : searchables) {
            final SearchableSearchProvider searchableSearchProvider =
                    new SearchableSearchProvider(executor,
                            taskManager,
                            taskContextFactory,
                            clientConfig,
                            coprocessorsFactory,
                            resultStoreFactory,
                            securityContext,
                            searchable);
            dataSourceProviders.add(searchableSearchProvider);
            searchProviders.add(searchableSearchProvider);
        }
    }

    @Override
    public Set<DataSourceProvider> getDataSourceProviders() {
        return dataSourceProviders;
    }

    @Override
    public Set<SearchProvider> getSearchProviders() {
        return searchProviders;
    }
}
