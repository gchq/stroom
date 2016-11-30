package stroom.explorer.server;

import stroom.explorer.shared.FetchExplorerDataAction;
import stroom.explorer.shared.FetchExplorerDataResult;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchExplorerDataAction.class)
class FetchExplorerDataHandler
        extends AbstractTaskHandler<FetchExplorerDataAction, FetchExplorerDataResult> {
    private final ExplorerService explorerService;

    @Inject
    FetchExplorerDataHandler(final ExplorerService explorerService) {
        this.explorerService = explorerService;
    }

    @Override
    public FetchExplorerDataResult exec(final FetchExplorerDataAction action) {
        return explorerService.getData(action.getCriteria());
    }
}
