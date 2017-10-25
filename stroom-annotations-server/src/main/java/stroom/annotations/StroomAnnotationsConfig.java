package stroom.annotations;

import stroom.explorer.server.ExplorerActionHandlers;
import stroom.explorer.shared.ExplorerConstants;

import javax.inject.Inject;

public class StroomAnnotationsConfig {
    @Inject
    public StroomAnnotationsConfig(final ExplorerActionHandlers explorerActionHandlers) {
        explorerActionHandlers.add(30, ExplorerConstants.ANNOTATIONS, ExplorerConstants.ANNOTATIONS, new StroomAnnotationsStore());
    }
}
