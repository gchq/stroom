package stroom.query.language;

import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DataSourceProvider;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.DataSourceProviderRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;

public class DataSourceResolver {

    private final DocRefInfoService docRefInfoService;
    private final DataSourceProviderRegistry dataSourceProviderRegistry;

    @Inject
    public DataSourceResolver(final DocRefInfoService docRefInfoService,
                              final DataSourceProviderRegistry dataSourceProviderRegistry) {
        this.docRefInfoService = docRefInfoService;
        this.dataSourceProviderRegistry = dataSourceProviderRegistry;
    }

    public SearchRequest resolveDataSource(SearchRequest request) {
        Objects.requireNonNull(request, "Null request");

        String dataSourceName = null;
        if (request.getQuery() != null && request.getQuery().getDataSource() != null) {
            dataSourceName = request.getQuery().getDataSource().getName();
        }

        final DataSource dataSource = resolveDataSource(dataSourceName);
        if (dataSource == null) {
            throw new RuntimeException("Unable to find data source \"" + dataSourceName + "\"");
        }

        return request.copy().query(request.getQuery().copy().dataSource(dataSource.getDocRef()).build()).build();
    }

    public DataSource resolveDataSource(String dataSourceName) {
        if (dataSourceName == null) {
            throw new RuntimeException("Null data source name");
        }

        final List<DocRef> docRefs = docRefInfoService.findByName(
                null,
                dataSourceName,
                false);
        if (docRefs == null || docRefs.size() == 0) {
            throw new RuntimeException("Data source \"" + dataSourceName + "\" not found");
        }

        // TODO : Deal with duplicate names.

        DocRef resolved = null;
        for (final DocRef docRef : docRefs) {
            final Optional<DataSourceProvider> optional =
                    dataSourceProviderRegistry.getDataSourceProvider(docRef);
            if (optional.isPresent()) {
                return optional.get().getDataSource(docRef);
            }
        }

        return null;
    }

    public DataSource resolveDataSource(final DocRef dataSourceRef) {
        return dataSourceProviderRegistry
                .getDataSourceProvider(dataSourceRef)
                .map(provider -> provider.getDataSource(dataSourceRef))
                .orElse(null);
    }
}
