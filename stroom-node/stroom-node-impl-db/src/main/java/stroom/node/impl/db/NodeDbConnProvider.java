package stroom.node.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class NodeDbConnProvider extends HikariDataSource {
    NodeDbConnProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
