package stroom.legacy.impex_6_1;

import stroom.docref.DocRef;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.util.shared.Severity;

import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Deprecated
class ElasticClusterDataMapConverter implements DataMapConverter {

    private final ElasticClusterSerialiser serialiser;

    @Inject
    ElasticClusterDataMapConverter(final ElasticClusterSerialiser serialiser) {
        this.serialiser = serialiser;
    }

    @Override
    public Map<String, byte[]> convert(final DocRef docRef,
                                       final Map<String, byte[]> dataMap,
                                       final ImportState importState,
                                       final ImportSettings importSettings,
                                       final String userId) {
        Map<String, byte[]> result = dataMap;

        try {
            if (!dataMap.containsKey("meta")) {
                if (dataMap.containsKey("dat")) {
                    dataMap.put("meta", dataMap.remove("dat"));
                    final ElasticClusterDoc document = serialiser.read(dataMap);
                    result = serialiser.write(document);
                }
            }
        } catch (final IOException | RuntimeException e) {
            importState.addMessage(Severity.ERROR, e.getMessage());
            result = null;
        }

        return result;
    }
}
