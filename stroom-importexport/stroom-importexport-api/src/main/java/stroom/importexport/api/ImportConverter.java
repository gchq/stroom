package stroom.importexport.api;

import stroom.docref.DocRef;
import stroom.importexport.shared.ImportState;

import java.util.Map;

public interface ImportConverter {
    Map<String, byte[]> convert(DocRef docRef,
                                Map<String, byte[]> dataMap,
                                ImportState importState,
                                ImportState.ImportMode importMode,
                                String userId);
}
