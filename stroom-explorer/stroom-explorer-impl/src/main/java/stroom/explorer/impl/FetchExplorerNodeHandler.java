package stroom.explorer.impl;

import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.FetchExplorerNodeAction;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;


class FetchExplorerNodeHandler
        extends AbstractTaskHandler<FetchExplorerNodeAction, FetchExplorerNodeResult> {
    private final ExplorerService explorerService;
    private final SecurityContext securityContext;

    @Inject
    FetchExplorerNodeHandler(final ExplorerService explorerService,
                             final SecurityContext securityContext) {
        this.explorerService = explorerService;
        this.securityContext = securityContext;
    }

    @Override
    public FetchExplorerNodeResult exec(final FetchExplorerNodeAction action) {
        return securityContext.secureResult(() -> explorerService.getData(action.getCriteria()));
    }
}
