package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FSVolume;
import stroom.data.store.impl.fs.shared.UpdateFSVolumeAction;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;

class UpdateFSVolumeHandler extends AbstractTaskHandler<UpdateFSVolumeAction, FSVolume> {
    private final FileSystemVolumeService volumeService;
    private final DocumentEventLog documentEventLog;
    private final Security security;

    @Inject
    UpdateFSVolumeHandler(final FileSystemVolumeService volumeService,
                                 final DocumentEventLog documentEventLog,
                                 final Security security) {
        this.volumeService = volumeService;
        this.documentEventLog = documentEventLog;
        this.security = security;
    }

    @Override
    public FSVolume exec(final UpdateFSVolumeAction action) {
        return security.secureResult(() -> {
            FSVolume result = null;

            final FSVolume volume = action.getVolume();
            if (volume.getId() == null) {
                try {
                    result = volumeService.create(volume);
                    documentEventLog.create(volume, null);
                } catch (final RuntimeException e) {
                    documentEventLog.create(volume, e);
                    throw e;
                }
            } else {
                try {
                    result = volumeService.update(volume);
                    documentEventLog.update(volume, result, null);
                } catch (final RuntimeException e) {
                    documentEventLog.update(volume, result, e);
                    throw e;
                }
            }

            return result;
        });
    }
}
