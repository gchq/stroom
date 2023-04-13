package stroom.query.common.v2;

import stroom.docref.DocRef;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@SuppressWarnings("unused")
public class StoreFactoryRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreFactoryRegistry.class);

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
