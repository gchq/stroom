package stroom.elastic.server;

import stroom.explorer.server.ExplorerActionHandlers;
import stroom.explorer.shared.ExplorerConstants;

import javax.inject.Inject;

public class StroomElasticConfig {
    @Inject
    public StroomElasticConfig(final ExplorerActionHandlers explorerActionHandlers,
                               final StroomElasticExplorerActionHandler actionHandler) {
        explorerActionHandlers.add(30, ExplorerConstants.ELASTIC_SEARCH, ExplorerConstants.ELASTIC_SEARCH, actionHandler);
    }
}
