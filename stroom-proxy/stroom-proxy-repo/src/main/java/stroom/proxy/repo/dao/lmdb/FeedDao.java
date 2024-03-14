package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.FeedAndType;
import stroom.proxy.repo.dao.lmdb.serde.FeedAndTypeSerde;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class FeedDao extends AbstractDaoLmdb<Long, FeedAndType> {

    @Inject
    public FeedDao(final LmdbEnv env) {
        super(env, "feed", "feed-index", new LongSerde(), new FeedAndTypeSerde());
    }

    @Override
    RowKey<Long> createRowKey(final Db<Long, FeedAndType> db) {
        return new LongRowKey(db);
    }

    public long getId(final FeedAndType feedKey) {
        return super.getOrCreateKey(feedKey);
    }

    public FeedAndType getKey(final long id) {
        return get(id).orElse(null);
    }
}
