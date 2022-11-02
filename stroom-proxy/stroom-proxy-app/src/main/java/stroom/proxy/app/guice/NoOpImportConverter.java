package stroom.proxy.app.guice;

import stroom.docref.DocRef;
import stroom.importexport.api.ImportConverter;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;

import java.util.Map;

public class NoOpImportConverter implements ImportConverter {

    @Override
    public Map<String, byte[]> convert(final DocRef docRef,
                                       final Map<String, byte[]> dataMap,
                                       final ImportState importState,
                                       final ImportMode importMode,
                                       final String userId) {
        throw new UnsupportedOperationException("Import is not supported in proxy.");
    }
}
