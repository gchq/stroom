package stroom.index.impl.db;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.index.dao.IndexShardDao;

import javax.inject.Inject;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class IndexShardDaoImpl implements IndexShardDao {

    private final ConnectionProvider connectionProvider;

    @Inject
    public IndexShardDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

}
