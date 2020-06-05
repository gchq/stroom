package stroom.legacy.impex_6_1;

import stroom.docref.DocRef;
import stroom.importexport.shared.ImportState;
import stroom.index.impl.IndexSerialiser;
import stroom.index.shared.IndexDoc;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;

@Singleton
@Deprecated
class IndexDataMapConverter implements DataMapConverter {
    private final IndexSerialiser serialiser;

    @Inject
    IndexDataMapConverter(final IndexSerialiser serialiser) {
        this.serialiser = serialiser;
    }

    @Override
    public Map<String, byte[]> convert(final DocRef docRef,
                                       final Map<String, byte[]> dataMap,
                                       final ImportState importState,
                                       final ImportState.ImportMode importMode,
                                       final String userId) {
        Map<String, byte[]> result = dataMap;
        if (dataMap.size() > 0 && !dataMap.containsKey("meta")) {
            try {
                IndexDoc document = new LegacyIndexDeserialiser()
                        .getIndexDocFromLegacyImport(
                                new stroom.legacy.model_6_1.DocRef(docRef.getType(), docRef.getUuid(), docRef.getName()),
                                dataMap);

                result = serialiser.write(document);

            } catch (final IOException | RuntimeException e) {
                importState.addMessage(Severity.ERROR, e.getMessage());
                result = null;
            }
        }

        return result;
    }
}
