package stroom.app.db.migration;

import javax.sql.DataSource;

/**
 * Special DB module used only for cross-module java flyway migrations
 */
public interface CrossModuleDbConnProvider extends DataSource {

}
