package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FSVolume;
import stroom.data.store.impl.fs.shared.FetchFSVolumeAction;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;

class FetchFSVolumeHandler extends AbstractTaskHandler<FetchFSVolumeAction, FSVolume> {
    private final FileSystemVolumeService volumeService;
    private final DocumentEventLog documentEventLog;
    private final Security security;

    @Inject
    FetchFSVolumeHandler(final FileSystemVolumeService volumeService,
                                final DocumentEventLog documentEventLog,
                                final Security security) {
        this.volumeService = volumeService;
        this.documentEventLog = documentEventLog;
        this.security = security;
    }

    @Override
    public FSVolume exec(final FetchFSVolumeAction action) {
        return security.secureResult(() -> {
            FSVolume result;

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
