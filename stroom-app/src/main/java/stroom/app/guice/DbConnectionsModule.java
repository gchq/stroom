/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.app.guice;

import stroom.app.db.migration.CrossModuleDbConnProvider;
import stroom.app.db.migration.CrossModuleDbMigrationsModule;
import stroom.util.shared.NullSafe;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Set;
import javax.sql.DataSource;

public class DbConnectionsModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        // All the modules for the DB connections
        install(new stroom.activity.impl.db.ActivityDbModule());
        install(new stroom.analytics.impl.db.AnalyticsDbModule());
        install(new stroom.annotation.impl.db.AnnotationDbModule());
        install(new stroom.cluster.lock.impl.db.ClusterLockDbModule());
        install(new stroom.credentials.impl.db.CredentialsDbModule());
        install(new stroom.config.global.impl.db.GlobalConfigDbModule());
        install(new stroom.data.store.impl.fs.db.FsDataStoreDbModule());
        install(new stroom.docstore.impl.db.DocStoreDBPersistenceDbModule());
        install(new stroom.explorer.impl.db.ExplorerDbModule());
        install(new stroom.gitrepo.impl.db.GitRepoDbModule());
        install(new stroom.index.impl.db.IndexDbModule());
        install(new stroom.job.impl.db.JobDbModule());
        install(new stroom.meta.impl.db.MetaDbModule());
        install(new stroom.node.impl.db.NodeDbModule());
        install(new stroom.processor.impl.db.ProcessorDbModule());
        install(new stroom.security.identity.db.IdentityDbModule());
        install(new stroom.security.impl.db.SecurityDbModule());
        install(new stroom.storedquery.impl.db.StoredQueryDbModule());
        install(new stroom.statistics.impl.sql.SQLStatisticsDbModule());
        install(new stroom.dashboard.impl.db.VisualisationAssetDbModule());

        // Special DB module for running cross-module java migrations
        NullSafe.consume(getCrossModuleDbMigrationsModule(), this::install);

        // This ensures all DB migrations get run as part of the guice bindings set up
        bind(DbMigrations.class).asEagerSingleton();
    }

    /**
     * Subclasses can override this to provide a modified cross-module module for testing
     */
    protected CrossModuleDbMigrationsModule getCrossModuleDbMigrationsModule() {
        return new CrossModuleDbMigrationsModule();
    }


    // --------------------------------------------------------------------------------


    @SuppressWarnings("unused") // Eager singleton to ensure migrations get run
    @Singleton
    static class DbMigrations {

        @Inject
        public DbMigrations(final Set<DataSource> dbConnProviders,
                            final CrossModuleDbConnProvider crossModuleDbConnProvider) {

            // Nothing to do here, we now know all dbConnProviders are migrated and ready
        }
    }
}
