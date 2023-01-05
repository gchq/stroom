package stroom.query.language;

import stroom.datasource.api.v2.DataSourceProvider;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.DataSourceProviderRegistry;

import java.util.List;
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
        String dataSourceName = null;
        if (request != null &&
                request.getQuery() != null &&
                request.getQuery().getDataSource() != null) {
            dataSourceName = request.getQuery().getDataSource().getName();
        }
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
                resolved = docRef;
                break;
            }
        }

        if (resolved == null) {
            throw new RuntimeException("Unable to find data source \"" + dataSourceName + "\"");
        }

        return request.copy().query(request.getQuery().copy().dataSource(resolved).build()).build();
    }
}
