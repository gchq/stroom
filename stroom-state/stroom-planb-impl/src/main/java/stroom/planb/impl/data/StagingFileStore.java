package stroom.planb.impl.data;

import stroom.planb.impl.db.StatePaths;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class StagingFileStore extends SequentialFileStore {

    @Inject
    public StagingFileStore(final StatePaths statePaths) {
        super(statePaths.getStagingDir());
    }
}
