package stroom.explorer;

import stroom.explorer.shared.FetchExplorerNodeAction;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchExplorerNodeAction.class)
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
