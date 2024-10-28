package stroom.query.language;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

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

        // Try by name (case-insensitive).
        final List<DocRef> docRefs = docRefInfoService.findByName(
                type, name, false, false);
        if (docRefs.isEmpty()) {
            throw new RuntimeException(type + " \"" + name + "\" not found");
        } else if (docRefs.size() > 1) {
            throw new RuntimeException("Multiple " +
                    type.toLowerCase(Locale.ROOT) +
                    " items found with name \"" +
                    name +
                    "\"");
        }
        return docRefs.getFirst();
    }

    private DocRef getDataSourceRefFromUuid(final String uuid) {
        final DocRefInfoService docRefInfoService = docRefInfoServiceProvider.get();
        final DataSourceProviderRegistry dataSourceProviderRegistry = dataSourceProviderRegistryProvider.get();
        // Make sure that the uuid is both a valid docref and the type
        // is considered a datasource, i.e. there is a provider for that type
        final DocRef docRef = docRefInfoService.info(uuid)
                .map(DocRefInfo::getDocRef)
                .orElseThrow(() ->
                        new RuntimeException("Data source not found with uuid \"" + uuid + "\""));

        dataSourceProviderRegistry.getDataSourceProvider(
                        docRef.getType())
                .orElseThrow(() -> new RuntimeException(
                        "No datasource provider found for type '" + docRef.getType() + "'"));

        // Type has a provider so all good
        return docRef;
    }

    private DocRef getDataSourceRefFromName(final String name) {
        final DataSourceProviderRegistry dataSourceProviderRegistry = dataSourceProviderRegistryProvider.get();
        final List<DocRef> docRefs = dataSourceProviderRegistry
                .list()
                .stream()
                .filter(docRef ->
                        NullSafe.equalsIgnoreCase(docRef, DocRef::getName, name))
                .toList();
        if (docRefs.isEmpty()) {
            throw new RuntimeException("Data source \"" +
                    name +
                    "\" not found. You may not have permission to use it.");
        } else if (docRefs.size() > 1) {
            throw new RuntimeException("Multiple data sources found for \"" + name + "\"");
        }
        return docRefs.getFirst();
    }
}
