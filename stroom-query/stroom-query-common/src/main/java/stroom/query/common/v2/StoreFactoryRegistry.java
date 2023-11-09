package stroom.query.common.v2;

import stroom.docref.DocRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class StoreFactoryRegistry {

    private final Map<String, SearchProvider> dataSourceProviders = new ConcurrentHashMap<>();

    @Inject
    public StoreFactoryRegistry(final Set<SearchProvider> factories) {
        for (final SearchProvider factory : factories) {
            dataSourceProviders.put(factory.getType(), factory);
        }
    }

    public Optional<SearchProvider> getStoreFactory(final DocRef dataSourceRef) {
        return Optional.ofNullable(dataSourceProviders.get(dataSourceRef.getType()));
    }
}
