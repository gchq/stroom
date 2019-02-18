package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.DeleteFSVolumeAction;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;

class DeleteFSVolumeHandler extends AbstractTaskHandler<DeleteFSVolumeAction, VoidResult> {
    private final FileSystemVolumeService volumeService;
    private final DocumentEventLog documentEventLog;
    private final Security security;

    @Inject
    DeleteFSVolumeHandler(final FileSystemVolumeService volumeService,
                                 final DocumentEventLog documentEventLog,
                                 final Security security) {
        this.volumeService = volumeService;
        this.documentEventLog = documentEventLog;
        this.security = security;
    }

    @Override
    public VoidResult exec(final DeleteFSVolumeAction action) {
        return security.secureResult(() -> {
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
