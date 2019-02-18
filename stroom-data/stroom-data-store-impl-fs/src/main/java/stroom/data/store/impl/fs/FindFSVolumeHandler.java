package stroom.data.store.impl.fs;

import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;
import stroom.data.store.impl.fs.shared.FSVolume;
import stroom.data.store.impl.fs.shared.FindFSVolumeAction;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ResultList;

public class FindFSVolumeHandler extends AbstractTaskHandler<FindFSVolumeAction, ResultList<FSVolume>> {
    private final FileSystemVolumeService volumeService;
    private final DocumentEventLog documentEventLog;
    private final Security security;

    public FindFSVolumeHandler(final FileSystemVolumeService volumeService,
                               final DocumentEventLog documentEventLog,
                               final Security security) {
        this.volumeService = volumeService;
        this.documentEventLog = documentEventLog;
        this.security = security;
    }

    @Override
    public ResultList<FSVolume> exec(final FindFSVolumeAction action) {
        // TODO : @66 fill out query
        final Query query = new Query();
        final Advanced advanced = new Advanced();
        query.setAdvanced(advanced);
        final And and = new And();
        advanced.getAdvancedQueryItems().add(and);

        return security.secureResult(() -> {
            BaseResultList<FSVolume> result = null;

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
