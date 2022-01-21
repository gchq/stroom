package stroom.dashboard.impl.datasource;

import stroom.datasource.api.v2.DataSourceProvider;
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
public class DataSourceProviderRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourceProviderRegistry.class);

    private final Map<String, DataSourceProvider> dataSourceProviders = new ConcurrentHashMap<>();

    @Inject
    public DataSourceProviderRegistry(final Set<DataSourceProvider> providers) {
        for (final DataSourceProvider provider : providers) {
            dataSourceProviders.put(provider.getType(), provider);
        }
    }

    public Optional<DataSourceProvider> getDataSourceProvider(final DocRef dataSourceRef) {
        return Optional.ofNullable(dataSourceProviders.get(dataSourceRef.getType()));
    }
}
