package stroom.data.store.impl.fs;

import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FindFsVolumeAction;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ResultList;

import javax.inject.Inject;

class FindFsVolumeHandler extends AbstractTaskHandler<FindFsVolumeAction, ResultList<FsVolume>> {
    private final FsVolumeService volumeService;
    private final DocumentEventLog documentEventLog;
    private final Security security;

    @Inject
    FindFsVolumeHandler(final FsVolumeService volumeService,
                        final DocumentEventLog documentEventLog,
                        final Security security) {
        this.volumeService = volumeService;
        this.documentEventLog = documentEventLog;
        this.security = security;
    }

    @Override
    public ResultList<FsVolume> exec(final FindFsVolumeAction action) {
        // TODO : @66 fill out query
        final Query query = new Query();
        final Advanced advanced = new Advanced();
        query.setAdvanced(advanced);
        final And and = new And();
        advanced.getAdvancedQueryItems().add(and);

        return security.secureResult(() -> {
            BaseResultList<FsVolume> result = null;

            try {
                result = volumeService.find(action.getCriteria());
                documentEventLog.search(action.getCriteria(), query, result, null);
            } catch (final RuntimeException e) {
                documentEventLog.search(action.getCriteria(), query, result, e);
                throw e;
            }

            return result;
        });
    }
}
