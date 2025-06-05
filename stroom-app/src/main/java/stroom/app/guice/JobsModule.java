package stroom.app.guice;

import stroom.data.store.impl.DataRetentionJobModule;
import stroom.data.store.impl.fs.FsDataStoreJobsModule;
import stroom.data.store.impl.fs.FsVolumeJobsModule;
import stroom.gitrepo.impl.GitRepoJobsModule;

import com.google.inject.AbstractModule;

public class JobsModule extends AbstractModule {

    @Override
    protected void configure() {

        // Job modules with no other obvious home
        install(new DataRetentionJobModule());
        install(new FsVolumeJobsModule());
        install(new stroom.node.impl.NodeJobsModule());
        install(new FsDataStoreJobsModule());
        install(new GitRepoJobsModule());
    }
}
