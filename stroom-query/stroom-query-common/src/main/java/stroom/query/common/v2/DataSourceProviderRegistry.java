package stroom.query.common.v2;

import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DataSourceProvider;
import stroom.docref.DocRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@SuppressWarnings("unused")
public class DataSourceProviderRegistry {

    private final Map<String, DataSourceProvider> dataSourceProviders = new ConcurrentHashMap<>();

    @Inject
    public DataSourceProviderRegistry(final Set<DataSourceProvider> providers) {
        for (final DataSourceProvider provider : providers) {
            dataSourceProviders.put(provider.getType(), provider);
        }
    }

    public Optional<DataSourceProvider> getDataSourceProvider(final String type) {
        return Optional.ofNullable(dataSourceProviders.get(type));
    }

    public Optional<DataSource> getDataSource(final DocRef docRef) {
        return getDataSourceProvider(docRef.getType())
                .map(dsp -> dsp.getDataSource(docRef));
    }

    public Set<String> getTypes() {
        return dataSourceProviders.keySet();
    }
}
