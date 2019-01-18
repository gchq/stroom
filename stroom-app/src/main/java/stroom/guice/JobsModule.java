package stroom.guice;

import com.google.inject.AbstractModule;
import stroom.IndexServerJobsModule;
import stroom.PipelineJobsModule;
import stroom.StroomCoreServerJobsModule;
import stroom.config.global.impl.db.GlobalConfigJobsModule;
import stroom.dashboard.DashboardJobsModule;
import stroom.data.meta.impl.db.DataMetaJobsModule;
import stroom.data.store.impl.fs.FileSystemJobsModule;
import stroom.statistics.sql.SQLStatisticsJobsModule;

public class JobsModule extends AbstractModule {
    @Override
    protected void configure(){
       install(new DashboardJobsModule());
       install(new StroomCoreServerJobsModule());
       install(new GlobalConfigJobsModule());
       install(new PipelineJobsModule());
       install(new IndexServerJobsModule());
       install(new FileSystemJobsModule());
       install(new SQLStatisticsJobsModule());
       install(new DataMetaJobsModule());
    }
}
