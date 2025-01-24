package stroom.query.common.v2;

import stroom.docref.DocRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class SearchProviderRegistry {

    private final Map<String, SearchProvider> searchProviderMap = new ConcurrentHashMap<>();

    @Inject
    public SearchProviderRegistry(final Set<SearchProvider> providers,
                                  final AdditionalSearchProviders additionalSearchProviders) {
        for (final SearchProvider provider : providers) {
            searchProviderMap.put(provider.getDataSourceType(), provider);
        }
        for (final SearchProvider provider : additionalSearchProviders.getSearchProviders()) {
            searchProviderMap.put(provider.getDataSourceType(), provider);
        }
    }

    public Optional<SearchProvider> getSearchProvider(final DocRef dataSourceRef) {
        final DocRef docRef = LegacyDocRefConverter.convert(dataSourceRef);
        return Optional.ofNullable(searchProviderMap.get(docRef.getType()));
    }
}
