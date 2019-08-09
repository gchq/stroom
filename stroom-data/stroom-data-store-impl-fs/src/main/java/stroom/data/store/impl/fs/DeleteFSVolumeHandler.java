package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.DeleteFsVolumeAction;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;

class DeleteFSVolumeHandler extends AbstractTaskHandler<DeleteFsVolumeAction, VoidResult> {
    private final FsVolumeService volumeService;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;

    @Inject
    DeleteFSVolumeHandler(final FsVolumeService volumeService,
                                 final DocumentEventLog documentEventLog,
                                 final SecurityContext securityContext) {
        this.volumeService = volumeService;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
    }

    @Override
    public VoidResult exec(final DeleteFsVolumeAction action) {
        return securityContext.secureResult(() -> {
            try {
                volumeService.delete(action.getVolume().getId());
                documentEventLog.delete(action.getVolume(), null);
            } catch (final RuntimeException e) {
                documentEventLog.delete(action.getVolume(), e);
                throw e;
            }

            return VoidResult.INSTANCE;
        });
    }
}
