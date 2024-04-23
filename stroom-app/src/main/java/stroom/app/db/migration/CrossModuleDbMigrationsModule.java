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

import java.util.Set;
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

        // Bind all the cross-module java migrations here so flyway knows what to run.
        // Don't do any SQL migrations as you can't guarantee that each module is in
        // the same DB/host. Java migrations must inject the dbConnProvider for each
        // module that they want to deal with, accepting that
        // Order doesn't matter here, but you are going to sort them aren't you.
        GuiceUtil.buildMultiBinder(binder(), AbstractCrossModuleJavaDbMigration.class)
                .addBinding(V07_04_00_005__Fs_Vol_Grp_Name_to_UUID.class)
                .addBinding(V07_04_00_010__Idx_Vol_Grp_Name_to_UUID.class);
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
        // up-to-date in order for us to do cross-module migrations
        final Set<DataSource> dataSources = injector.getInstance(
                Key.get(GuiceUtil.setOf(DataSource.class)));

        // Get all our app-wide java migs
        final Set<JavaMigration> javaMigrations = injector.getInstance(
                        Key.get(GuiceUtil.setOf(AbstractCrossModuleJavaDbMigration.class)))
                .stream()
                .map(obj -> (JavaMigration) obj)
                .collect(Collectors.toSet());

        FlywayUtil.migrate(
                dataSource,
                javaMigrations, // Guice injected java migrations only
                getMigrationTarget().orElse(null),
                getFlyWayTableName(),
                getModuleName());
        return true;
    }


    // --------------------------------------------------------------------------------


    private static class DataSourceImpl extends DataSourceProxy implements CrossModuleDbConnProvider {

        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource, MODULE);
        }
    }
}
