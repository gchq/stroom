package stroom.explorer.impl;

import stroom.docref.DocRefInfo;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerServiceInfoAction;
import stroom.explorer.shared.SharedDocRefInfo;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;


class ExplorerServiceInfoHandler extends AbstractTaskHandler<ExplorerServiceInfoAction, SharedDocRefInfo> {

    private final ExplorerService explorerService;
    private final SecurityContext securityContext;

    @Inject
    ExplorerServiceInfoHandler(final ExplorerService explorerService,
                               final SecurityContext securityContext) {
        this.explorerService = explorerService;
        this.securityContext = securityContext;
    }

    @Override
    public SharedDocRefInfo exec(final ExplorerServiceInfoAction task) {
        return securityContext.secureResult(() -> {
            final DocRefInfo docRefInfo = explorerService.info(task.getDocRef());

            return new SharedDocRefInfo.Builder()
                    .type(docRefInfo.getDocRef().getType())
                    .uuid(docRefInfo.getDocRef().getUuid())
                    .name(docRefInfo.getDocRef().getName())
                    .otherInfo(docRefInfo.getOtherInfo())
                    .createTime(docRefInfo.getCreateTime())
                    .createUser(docRefInfo.getCreateUser())
                    .updateTime(docRefInfo.getUpdateTime())
                    .updateUser(docRefInfo.getUpdateUser())
                    .build();
        });
    }
}
