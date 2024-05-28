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

        // Bind all the cross-module java migrations here so flyway knows what to run.
        // Don't do any SQL migrations as you can't guarantee that each module is in
        // the same DB/host. Java migrations must inject the dbConnProvider for each
        // module that they want to deal with, accepting that
        // Order doesn't matter here, but you are going to sort them aren't you.
        GuiceUtil.buildMultiBinder(binder(), AbstractCrossModuleJavaDbMigration.class)
                .addBinding(V07_04_00_005__Orphaned_Doc_Perms.class);
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
