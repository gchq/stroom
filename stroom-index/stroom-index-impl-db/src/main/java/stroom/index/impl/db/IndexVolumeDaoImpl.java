package stroom.index.impl.db;

import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.util.JooqUtil;
import stroom.index.dao.IndexVolumeDao;
import stroom.index.shared.IndexVolume;

import javax.inject.Inject;

import java.util.Set;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class IndexVolumeDaoImpl implements IndexVolumeDao {

    private final ConnectionProvider connectionProvider;

    @Inject
    public IndexVolumeDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Set<IndexVolume> getVolumesForIndex(String indexUuid) {
        return null;
    }
}
