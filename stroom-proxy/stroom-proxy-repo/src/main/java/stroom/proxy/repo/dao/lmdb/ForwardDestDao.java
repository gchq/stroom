package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.ForwardDest;
import stroom.proxy.repo.dao.lmdb.serde.IntegerSerde;
import stroom.proxy.repo.dao.lmdb.serde.StringSerde;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Singleton
public class ForwardDestDao extends AbstractDaoLmdb<Integer, String> {

    @Inject
    public ForwardDestDao(final LmdbEnv env) {
        super(env, "forward-dest", "forward-dest-index", new IntegerSerde(), new StringSerde());
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
    RowKey<Integer> createRowKey(final Db<Integer, String> db) {
        return new IntegerRowKey(db);
    }
}
