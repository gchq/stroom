package stroom.legacy.impex_6_1;

import stroom.docref.DocRef;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.index.impl.IndexSerialiser;
import stroom.index.impl.selection.VolumeConfig;
import stroom.index.shared.IndexDoc;
import stroom.util.shared.Severity;

import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
@Deprecated
class IndexDataMapConverter implements DataMapConverter {

    private final IndexSerialiser serialiser;
    private final Provider<VolumeConfig> volumeConfigProvider;

    @Inject
    IndexDataMapConverter(final IndexSerialiser serialiser,
                          final Provider<VolumeConfig> volumeConfigProvider) {
        this.serialiser = serialiser;
        this.volumeConfigProvider = volumeConfigProvider;
    }

    @Override
    public Map<String, byte[]> convert(final DocRef docRef,
                                       final Map<String, byte[]> dataMap,
                                       final ImportState importState,
                                       final ImportSettings importSettings,
                                       final String userId) {
        Map<String, byte[]> result = dataMap;
        if (dataMap.size() > 0 && !dataMap.containsKey("meta")) {
            try {
                IndexDoc document = new LegacyIndexDeserialiser()
                        .getIndexDocFromLegacyImport(
                                new stroom.legacy.model_6_1.DocRef(
                                        docRef.getType(),
                                        docRef.getUuid(),
                                        docRef.getName()),
                                dataMap,
                                volumeConfigProvider.get());

                result = serialiser.write(document);

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }
}
