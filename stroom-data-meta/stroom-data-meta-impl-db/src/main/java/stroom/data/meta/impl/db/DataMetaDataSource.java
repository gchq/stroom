package stroom.data.meta.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class DataMetaDataSource extends HikariDataSource {
    DataMetaDataSource(final HikariConfig configuration) {
        super(configuration);
    }
}
