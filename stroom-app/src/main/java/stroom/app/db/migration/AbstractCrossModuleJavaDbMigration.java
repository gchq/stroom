package stroom.app.db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;

/**
 * Superclass for all cross-module migrations. Make sure to multi-bind all migration subclasses to
 * this superclass in {@link CrossModuleDbMigrationsModule}, so that they all get run.
 */
public abstract class AbstractCrossModuleJavaDbMigration extends BaseJavaMigration {

}
