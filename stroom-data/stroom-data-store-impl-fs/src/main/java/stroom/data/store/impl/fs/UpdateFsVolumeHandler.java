package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.UpdateFsVolumeAction;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;

class UpdateFsVolumeHandler extends AbstractTaskHandler<UpdateFsVolumeAction, FsVolume> {
    private final FsVolumeService volumeService;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;

    @Inject
    UpdateFsVolumeHandler(final FsVolumeService volumeService,
                          final DocumentEventLog documentEventLog,
                          final SecurityContext securityContext) {
        this.volumeService = volumeService;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
    }

    @Override
    public FsVolume exec(final UpdateFsVolumeAction action) {
        return securityContext.secureResult(() -> {
            FsVolume result = null;

            final FsVolume volume = action.getVolume();
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
