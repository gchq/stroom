package stroom.streamstore.meta.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class StreamMetaDataSource extends HikariDataSource {
    StreamMetaDataSource(final HikariConfig configuration) {
        super(configuration);
    }
}
