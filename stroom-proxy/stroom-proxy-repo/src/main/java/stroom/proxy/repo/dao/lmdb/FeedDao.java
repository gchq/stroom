package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.dao.lmdb.serde.FeedKeySerde;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.proxy.repo.dao.lmdb.serde.Serde;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.lmdbjava.Dbi;

import java.nio.ByteBuffer;

@Singleton
public class FeedDao extends AbstractDaoLmdb<Long, FeedKey> {

    private final LongSerde keySerde = new LongSerde();
    private final FeedKeySerde valueSerde = new FeedKeySerde();


    @Inject
    public FeedDao(final LmdbEnv env) {
        super(env, "feed", "feed-index");
    }

    @Override
    RowKey<Long> createRowKey(final LmdbEnv env, final Dbi<ByteBuffer> dbi) {
        return new LongRowKey(env, dbi);
    }

    @Override
    Serde<FeedKey> getValueSerde() {
        return valueSerde;
    }

    @Override
    Serde<Long> getKeySerde() {
        return keySerde;
    }

    @Override
    int getKeyLength() {
        return Long.BYTES;
    }

    public long getId(final FeedKey feedKey) {
        return super.getOrCreateKey(feedKey);
    }

    public FeedKey getKey(final long id) {
        return get(id).orElse(null);
    }

    public int countFeeds() {
        return (int) super.count();
    }
}
