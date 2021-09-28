package stroom.db.util;

import stroom.config.common.HasDbConfig;

import javax.sql.DataSource;

public interface DataSourceFactory {

    DataSource create(HasDbConfig config, String name, boolean unique);
}
