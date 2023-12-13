package stroom.proxy.repo.dao.lmdb;

import stroom.lmdb.serde.Serde;
import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.dao.lmdb.serde.FeedKeySerde;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.lmdbjava.Dbi;

import java.nio.ByteBuffer;

@Singleton
public class FeedDao extends AbstractDaoLmdb<Long, FeedKey> {

    private final LongSerde keySerde;
    private final FeedKeySerde valueSerde;


    @Inject
    public FeedDao(final LmdbEnv env,
                   final LongSerde keySerde,
                   final FeedKeySerde valueSerde,
                   final LongSerde hashSerde) {
        super(env, "feed", "feed-index", hashSerde);
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
    }

    @Override
    RowKey<Long> createRowKey(final LmdbEnv env, final Dbi<ByteBuffer> dbi) {
        return new LongRowKey(env, dbi, keySerde);
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
