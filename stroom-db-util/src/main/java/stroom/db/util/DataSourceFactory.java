package stroom.db.util;

import stroom.config.common.AbstractDbConfig;

import javax.sql.DataSource;

public interface DataSourceFactory {

    DataSource create(AbstractDbConfig dbConfig, String name, boolean unique);
}
