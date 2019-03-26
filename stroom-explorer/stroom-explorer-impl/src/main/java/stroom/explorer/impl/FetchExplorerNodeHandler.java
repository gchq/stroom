package stroom.explorer.impl;

import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.FetchExplorerNodeAction;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.security.api.Security;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;


class FetchExplorerNodeHandler
        extends AbstractTaskHandler<FetchExplorerNodeAction, FetchExplorerNodeResult> {
    private final ExplorerService explorerService;
    private final Security security;

    @Inject
    FetchExplorerNodeHandler(final ExplorerService explorerService,
                             final Security security) {
        this.explorerService = explorerService;
        this.security = security;
    }

    @Override
    public FetchExplorerNodeResult exec(final FetchExplorerNodeAction action) {
        return security.secureResult(() -> explorerService.getData(action.getCriteria()));
    }
}
