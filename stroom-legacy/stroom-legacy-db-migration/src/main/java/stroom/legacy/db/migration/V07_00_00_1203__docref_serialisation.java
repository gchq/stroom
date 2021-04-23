package stroom.legacy.db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused") // used by FlyWay
@Deprecated
public class V07_00_00_1203__docref_serialisation extends BaseJavaMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(V07_00_00_1203__docref_serialisation.class);

    /**
     * Executes this migration. The execution will automatically take place within a transaction, when the underlying
     * database supports it.
     *
     * @param flywayContext The flyway context to use to execute statements.
     * @throws Exception when the migration failed.
     */
    @Override
    public void migrate(final Context flywayContext) {

        // The work of this migration is now done in stroom.legacy.db.migration.V07_00_00_1202__property_rename
        // Leaving it here empty to avoid breaking any existing beta deployments
    }
}
