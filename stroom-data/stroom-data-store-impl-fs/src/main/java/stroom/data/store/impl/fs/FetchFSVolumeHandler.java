package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FetchFsVolumeAction;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;

class FetchFSVolumeHandler extends AbstractTaskHandler<FetchFsVolumeAction, FsVolume> {
    private final FsVolumeService volumeService;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;

    @Inject
    FetchFSVolumeHandler(final FsVolumeService volumeService,
                                final DocumentEventLog documentEventLog,
                                final SecurityContext securityContext) {
        this.volumeService = volumeService;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
    }

    @Override
    public FsVolume exec(final FetchFsVolumeAction action) {
        return securityContext.secureResult(() -> {
            FsVolume result;

            try {
                result = volumeService.fetch(action.getVolume().getId());
                documentEventLog.view(action.getVolume(), null);
            } catch (final RuntimeException e) {
                documentEventLog.view(action.getVolume(), e);
                throw e;
            }

            return result;
        });
    }
}
