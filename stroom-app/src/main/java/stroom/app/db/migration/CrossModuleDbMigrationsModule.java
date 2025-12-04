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

package stroom.app.db.migration;

import stroom.config.app.CrossModuleConfig.CrossModuleDbConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.db.util.FlywayUtil;
import stroom.util.guice.GuiceUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.Injector;
import com.google.inject.Key;
import org.flywaydb.core.api.migration.JavaMigration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * This DB module is a bit special. The aim is run java only flyway migrations across all
 * dbConnProviders, so we can do cross-module migrations. You can't do SQL migrations as
 * each module could potentially be on a different database instance/connection.
 */
public class CrossModuleDbMigrationsModule
        extends AbstractFlyWayDbModule<CrossModuleDbConfig, CrossModuleDbConnProvider> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CrossModuleDbMigrationsModule.class);

    private static final String MODULE = "cross-module";
    private static final String FLYWAY_TABLE = "cross_module_schema_history";

    @Override
    protected void configure() {
        // Don't call super.configure() else it will try to multi-bind AppDbConnProvider as
        // a Datasource which will result in a circular dependency


        // * *******************************************************
        // * ********************** WARNING ************************
        // * *******************************************************
        // *
        // * This approach of having a cross-module module is problematic.
        // * It gets run after all the other modules, so it has
        // * no idea what state each of the other modules is in, e.g. a
        // * cross-module mig written for v7.5 may get run after all the
        // * other modules have been brought up to v7.10 and thus
        // * look different to what this mig expects.
        // *
        // * Leaving it here in case it is we find a way to safely use it.
        // *
        // * *******************************************************
        // * *******************************************************
        // * *******************************************************


        // Bind all the cross-module java migrations here so flyway knows what to run.
        // Don't do any SQL migrations as you can't guarantee that each module is in
        // the same DB/host. Java migrations must inject the dbConnProvider for each
        // module that they want to deal with, accepting that
        // Order doesn't matter here, but you are going to sort them aren't you.
        GuiceUtil.buildMultiBinder(binder(), AbstractCrossModuleJavaDbMigration.class)
                .addBinding(V07_05_00_005__Orphaned_Doc_Perms.class);
    }

    @Override
    protected String getModuleName() {
        return MODULE;
    }

    @Override
    protected Class<CrossModuleDbConnProvider> getConnectionProviderType() {
        // Not used for our App-wide module
        return null;
    }

    @Override
    protected CrossModuleDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    @Override
    protected String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    protected boolean performMigration(final DataSource dataSource, final Injector injector) {

        // Force all module data sources to be created, so we can be sure all db modules are
        // fully migrated in order for us to do cross-module migrations
        injector.getInstance(Key.get(GuiceUtil.setOf(DataSource.class)));

        // Get all our app-wide java migs
        final List<JavaMigration> javaMigrations = getCrossModuleMigrations(injector);

        // Add any additional migs, i.e. test data for migration testing
//        javaMigrations.addAll(getAdditionalMigrations(injector));

        // Add any additional migs, i.e. test data for migration testing
        getAdditionalMigrationClasses().stream()
                .map(injector::getInstance)
                .forEach(javaMigrations::add);

        LOGGER.info("Using migrations classes:\n{}",
                javaMigrations.stream()
                        .sorted(Comparator.comparing(JavaMigration::getVersion))
                        .map(migration -> "  " + migration.getVersion() + " - " + migration.getClass().getName())
                        .collect(Collectors.joining("\n")));

        FlywayUtil.migrate(
                dataSource,
                javaMigrations, // Guice injected java migrations only
                getMigrationTarget().orElse(null), // Where to migrate up to
                getFlyWayTableName(),
                getModuleName());
        return true;
    }

    protected List<JavaMigration> getCrossModuleMigrations(final Injector injector) {
        return injector.getInstance(
                        Key.get(GuiceUtil.setOf(AbstractCrossModuleJavaDbMigration.class)))
                .stream()
                .map(obj -> (JavaMigration) obj)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * This gets overridden for testing to allow the test migration to be added in.
     * Order doesn't matter as the migrations will be ordered by
     * {@link org.flywaydb.core.api.MigrationVersion} anyway.
     */
    protected List<Class<? extends JavaMigration>> getAdditionalMigrationClasses() {
        return Collections.emptyList();
    }


    // --------------------------------------------------------------------------------


    private static class DataSourceImpl extends DataSourceProxy implements CrossModuleDbConnProvider {

        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource, MODULE);
        }
    }
}
