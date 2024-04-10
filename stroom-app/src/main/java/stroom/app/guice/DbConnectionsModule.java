package stroom.app.guice;

import com.google.inject.AbstractModule;

public class DbConnectionsModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        // All the modules for the DB connections
        install(new stroom.activity.impl.db.ActivityDbModule());
        install(new stroom.analytics.impl.db.AnalyticsDbModule());
        install(new stroom.annotation.impl.db.AnnotationDbModule());
        install(new stroom.cluster.lock.impl.db.ClusterLockDbModule());
        install(new stroom.config.global.impl.db.GlobalConfigDbModule());
        install(new stroom.data.store.impl.fs.db.FsDataStoreDbModule());
        install(new stroom.docstore.impl.db.DocStoreDBPersistenceDbModule());
        install(new stroom.explorer.impl.db.ExplorerDbModule());
        install(new stroom.index.impl.db.IndexDbModule());
        install(new stroom.job.impl.db.JobDbModule());
        install(new stroom.legacy.db.LegacyDbModule());
        install(new stroom.meta.impl.db.MetaDbModule());
        install(new stroom.node.impl.db.NodeDbModule());
        install(new stroom.processor.impl.db.ProcessorDbModule());
        install(new stroom.security.identity.db.IdentityDbModule());
        install(new stroom.security.impl.db.SecurityDbModule());
        install(new stroom.storedquery.impl.db.StoredQueryDbModule());
        install(new stroom.statistics.impl.sql.SQLStatisticsDbModule());
    }
}
