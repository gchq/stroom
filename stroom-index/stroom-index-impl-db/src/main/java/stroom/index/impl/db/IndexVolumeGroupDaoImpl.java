package stroom.index.impl.db;

import stroom.index.dao.IndexVolumeGroupDao;
import stroom.index.shared.IndexVolumeGroup;

import javax.inject.Inject;
import java.util.Optional;

public class IndexVolumeGroupDaoImpl implements IndexVolumeGroupDao {

    private final ConnectionProvider connectionProvider;

    @Inject
    public IndexVolumeGroupDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public IndexVolumeGroup create() {
//        return JooqUtil.contextResult(connectionProvider, context -> {
//            final Optional<MetaFeedRecord> optional = context
//                    .insertInto(META_FEED, META_FEED.NAME)
//                    .values(name)
//                    .onDuplicateKeyIgnore()
//                    .returning(META_FEED.ID)
//                    .fetchOptional();
//
//            return optional
//                    .map(record -> {
//                        final Integer id = record.getId();
//                        cache.put(name, id);
//                        return id;
//                    })
//                    .orElseGet(() -> get(name));
//        });
        return null;
    }

    @Override
    public IndexVolumeGroup update(IndexVolumeGroup record) {
        return null;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<IndexVolumeGroup> fetch(int id) {
        return Optional.empty();
    }
}
