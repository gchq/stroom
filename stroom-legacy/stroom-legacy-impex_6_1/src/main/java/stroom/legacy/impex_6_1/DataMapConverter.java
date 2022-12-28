package stroom.legacy.impex_6_1;

import stroom.docref.DocRef;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;

import java.util.Map;

@Deprecated
public interface DataMapConverter {

    Map<String, byte[]> convert(DocRef docRef,
                                Map<String, byte[]> dataMap,
                                ImportState importState,
                                ImportSettings importSettings,
                                String userId);
}
