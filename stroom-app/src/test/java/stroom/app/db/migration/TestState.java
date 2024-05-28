package stroom.app.db.migration;

import org.flywaydb.core.api.MigrationVersion;

public record TestState(MigrationVersion targetVersion,
                        MigrationVersion testDataVersion) {

}
