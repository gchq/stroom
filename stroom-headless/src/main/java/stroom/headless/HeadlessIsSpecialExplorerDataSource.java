package stroom.headless;

import stroom.docref.DocRef;
import stroom.explorer.api.IsSpecialExplorerDataSource;

import java.util.Collections;
import java.util.List;

public class HeadlessIsSpecialExplorerDataSource implements IsSpecialExplorerDataSource {

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return Collections.emptyList();
    }
}
