package stroom.proxy.repo;

import stroom.db.util.JooqHelper;

import org.jooq.SQLDialect;

import javax.sql.DataSource;

public class SqliteJooqHelper extends JooqHelper {

    public SqliteJooqHelper(final DataSource dataSource) {
        super(dataSource, SQLDialect.SQLITE);
    }
}
