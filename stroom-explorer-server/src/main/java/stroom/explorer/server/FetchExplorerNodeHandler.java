package stroom.explorer.server;

import org.springframework.context.annotation.Scope;
import stroom.explorer.shared.FetchExplorerNodeAction;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchExplorerNodeAction.class)
@Scope(StroomScope.TASK)
class FetchExplorerNodeHandler
        extends AbstractTaskHandler<FetchExplorerNodeAction, FetchExplorerNodeResult> {
    private final ExplorerService explorerService;

    @Inject
    FetchExplorerNodeHandler(final ExplorerService explorerService) {
        this.explorerService = explorerService;
    }

    @Override
    public FetchExplorerNodeResult exec(final FetchExplorerNodeAction action) {
        return explorerService.getData(action.getCriteria());
    }
}
