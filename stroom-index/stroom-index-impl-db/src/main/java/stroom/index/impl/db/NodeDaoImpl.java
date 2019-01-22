package stroom.index.impl.db;

import org.jooq.Record;
import org.jooq.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.index.dao.NodeDao;

import javax.inject.Inject;

import static org.jooq.impl.DSL.table;

public class NodeDaoImpl implements NodeDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeDaoImpl.class);

    private final ConnectionProvider connectionProvider;

    // Stroom User table
    private static final Table<Record> TABLE_STROOM_USER = table("stroom_user");

    @Inject
    public NodeDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }
}
