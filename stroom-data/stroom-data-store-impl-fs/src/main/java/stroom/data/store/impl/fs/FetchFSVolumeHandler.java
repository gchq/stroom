package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FetchFsVolumeAction;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.Security;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;

class FetchFSVolumeHandler extends AbstractTaskHandler<FetchFsVolumeAction, FsVolume> {
    private final FsVolumeService volumeService;
    private final DocumentEventLog documentEventLog;
    private final Security security;

    @Inject
    FetchFSVolumeHandler(final FsVolumeService volumeService,
                                final DocumentEventLog documentEventLog,
                                final Security security) {
        this.volumeService = volumeService;
        this.documentEventLog = documentEventLog;
        this.security = security;
    }

    @Override
    public FsVolume exec(final FetchFsVolumeAction action) {
        return security.secureResult(() -> {
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
