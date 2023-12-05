package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.FeedKey;
import stroom.proxy.repo.dao.FeedDao;
import stroom.proxy.repo.dao.lmdb.serde.FeedKeySerde;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.proxy.repo.dao.lmdb.serde.Serde;
import stroom.util.io.PathCreator;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;

import java.nio.ByteBuffer;

@Singleton
public class FeedDaoLmdb extends AbstractDaoLmdb<Long, FeedKey> implements FeedDao {

    private final LongSerde keySerde = new LongSerde();
    private final FeedKeySerde valueSerde = new FeedKeySerde();


    @Inject
    public FeedDaoLmdb(final ProxyLmdbConfig proxyLmdbConfig,
                       final PathCreator pathCreator,
                       final LmdbEnvFactory lmdbEnvFactory) {
        super(proxyLmdbConfig, pathCreator, lmdbEnvFactory, "feed", "feed-index");
    }

    @Override
    RowKey<Long> createRowKey(final Env<ByteBuffer> env, final Dbi<ByteBuffer> dbi) {
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

    @Override
    public long getId(final FeedKey feedKey) {
        return super.getOrCreateId(feedKey);
    }

    @Override
    public FeedKey getKey(final long id) {
        return get(id).orElse(null);
    }

    @Override
    public int countFeeds() {
        return (int) super.count();
    }
}
