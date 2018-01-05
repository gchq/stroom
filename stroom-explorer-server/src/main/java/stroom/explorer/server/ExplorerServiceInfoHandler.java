package stroom.explorer.server;

import org.springframework.context.annotation.Scope;
import stroom.entity.shared.SharedDocRefInfo;
import stroom.explorer.shared.ExplorerServiceInfoAction;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.DocRefInfo;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = ExplorerServiceInfoAction.class)
@Scope(value = StroomScope.TASK)
public class ExplorerServiceInfoHandler
        extends AbstractTaskHandler<ExplorerServiceInfoAction, SharedDocRefInfo> {

    private final ExplorerService explorerService;

    @Inject
    ExplorerServiceInfoHandler(final ExplorerService explorerService) {
        this.explorerService = explorerService;
    }

    @Override
    public SharedDocRefInfo exec(final ExplorerServiceInfoAction task) {
        final DocRefInfo docRefInfo = explorerService.info(task.getDocRef());

        return new SharedDocRefInfo.Builder()
                .type(docRefInfo.getDocRef().getType())
                .uuid(docRefInfo.getDocRef().getUuid())
                .name(docRefInfo.getDocRef().getName())
                .id(docRefInfo.getId())
                .createTime(docRefInfo.getCreateTime())
                .createUser(docRefInfo.getCreateUser())
                .updateTime(docRefInfo.getUpdateTime())
                .updateUser(docRefInfo.getUpdateUser())
                .build();
    }
}
