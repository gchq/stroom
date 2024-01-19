package stroom.query.language;

import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;

public class DocResolver {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocResolver.class);

    private final Provider<DocRefInfoService> docRefInfoServiceProvider;
    private final Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider;

    @Inject
    public DocResolver(final Provider<DocRefInfoService> docRefInfoServiceProvider,
                       final Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider) {
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
        this.dataSourceProviderRegistryProvider = dataSourceProviderRegistryProvider;
    }

    public DocRef resolveDataSourceRef(final String name) {
        // Try by uuid.
        try {
            return getDataSourceRefFromUuid(name);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }

        // Now try by name.
        return getDataSourceRefFromName(name);
    }

    public DocRef resolveDocRef(final String type, final String name) {
        final DocRefInfoService docRefInfoService = docRefInfoServiceProvider.get();

        // Try by UUID.
        final DocRef docRef = new DocRef(type, name);
        final Optional<DocRefInfo> optionalDocRef = docRefInfoService.info(docRef);
        if (optionalDocRef.isPresent()) {
            return optionalDocRef.get().getDocRef();
        }

        // Try by name.
        final List<DocRef> docRefs = docRefInfoService.findByName(type, name, false);
        if (docRefs.size() == 0) {
            throw new RuntimeException(type + " \"" + name + "\" not found");
        } else if (docRefs.size() > 1) {
            throw new RuntimeException("Multiple " +
                    type.toLowerCase(Locale.ROOT) +
                    " items found with name \"" +
                    name +
                    "\"");
        }
        return docRefs.get(0);
    }

    private DocRef getDataSourceRefFromUuid(final String uuid) {
        final DocRefInfoService docRefInfoService = docRefInfoServiceProvider.get();
        final DataSourceProviderRegistry dataSourceProviderRegistry = dataSourceProviderRegistryProvider.get();
        // Make sure that the uuid is both a valid docref and a DataSource
        return docRefInfoService.info(uuid)
                .map(DocRefInfo::getDocRef)
                .flatMap(dataSourceProviderRegistry::getDataSource)
                .map(DataSource::getDocRef)
                .orElseThrow(() ->
                        new RuntimeException("Data source not found with uuid \"" + uuid + "\""));
    }

    private DocRef getDataSourceRefFromName(final String name) {
        final DocRefInfoService docRefInfoService = docRefInfoServiceProvider.get();
        final DataSourceProviderRegistry dataSourceProviderRegistry = dataSourceProviderRegistryProvider.get();
        final List<DocRef> docRefs = docRefInfoService.findByName(
                null,
                name,
                false);
        if (NullSafe.isEmptyCollection(docRefs)) {
            throw new RuntimeException("Data source \"" + name + "\" not found");
        }

        final List<DocRef> result = docRefs.stream()
                .map(docRef -> dataSourceProviderRegistry.getDataSource(docRef)
                        .orElse(null))
                .filter(Objects::nonNull)
                .map(DataSource::getDocRef)
                .toList();

        if (result.isEmpty()) {
            throw new RuntimeException("Data source \"" + name + "\" not found");
        } else if (result.size() > 1) {
            throw new RuntimeException("Multiple data sources found for \"" + name + "\"");
        }
        return result.get(0);
    }
}
