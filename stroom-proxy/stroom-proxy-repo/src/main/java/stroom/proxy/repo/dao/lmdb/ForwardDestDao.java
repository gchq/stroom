package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.ForwardDest;
import stroom.proxy.repo.dao.lmdb.serde.IntegerSerde;
import stroom.proxy.repo.dao.lmdb.serde.Serde;
import stroom.proxy.repo.dao.lmdb.serde.StringSerde;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.lmdbjava.Dbi;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Singleton
public class ForwardDestDao extends AbstractDaoLmdb<Integer, String> {

    private final IntegerSerde keySerde = new IntegerSerde();
    private final StringSerde valueSerde = new StringSerde();

    @Inject
    public ForwardDestDao(final LmdbEnv env) {
        super(env, "forward-dest", "forward-dest-index");
    }

    public Optional<ForwardDest> get(final int id) {
        return super.get(id).map(name -> new ForwardDest(id, name));
    }

    public List<ForwardDest> getAllForwardDests() {
        final List<ForwardDest> list = new ArrayList<>();
        getAll((k, v) -> list.add(new ForwardDest(k, v)));
        return list;
    }

    public int getForwardDestId(final String name) {
        return getOrCreateKey(name);
    }

    public int countForwardDest() {
        return (int) super.count();
    }

    @Override
    RowKey<Integer> createRowKey(final LmdbEnv env, final Dbi<ByteBuffer> dbi) {
        return new IntegerRowKey(env, dbi);
    }

    Serde<String> getValueSerde() {
        return valueSerde;
    }

    Serde<Integer> getKeySerde() {
        return keySerde;
    }

    int getKeyLength() {
        return Integer.BYTES;
    }
}
