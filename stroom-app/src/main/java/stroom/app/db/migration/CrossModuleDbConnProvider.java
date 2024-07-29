package stroom.app.db.migration;

import javax.sql.DataSource;

/**
 * Special DB module used only for cross-module java flyway migrations.
 * Not to be used for any other purpose.
 */
public interface CrossModuleDbConnProvider extends DataSource {

}
