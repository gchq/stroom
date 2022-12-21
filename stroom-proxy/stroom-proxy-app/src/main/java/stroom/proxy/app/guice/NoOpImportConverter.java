package stroom.proxy.app.guice;

import stroom.docref.DocRef;
import stroom.importexport.api.ImportConverter;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;

import java.util.Map;

public class NoOpImportConverter implements ImportConverter {

    @Override
    public Map<String, byte[]> convert(final DocRef docRef,
                                       final Map<String, byte[]> dataMap,
                                       final ImportState importState,
                                       final ImportSettings importSettings,
                                       final String userId) {
        throw new UnsupportedOperationException("Import is not supported in proxy.");
    }
}
