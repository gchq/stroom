package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.ForwardDest;
import stroom.proxy.repo.dao.ForwardDestDao;
import stroom.proxy.repo.dao.lmdb.serde.IntegerSerde;
import stroom.proxy.repo.dao.lmdb.serde.Serde;
import stroom.proxy.repo.dao.lmdb.serde.StringSerde;
import stroom.util.io.PathCreator;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Singleton
public class ForwardDestDaoLmdb
        extends AbstractDaoLmdb<Integer, String>
        implements ForwardDestDao {

    private final IntegerSerde keySerde = new IntegerSerde();
    private final StringSerde valueSerde = new StringSerde();

    @Inject
    public ForwardDestDaoLmdb(final ProxyLmdbConfig proxyLmdbConfig,
                              final PathCreator pathCreator,
                              final LmdbEnvFactory lmdbEnvFactory) {
        super(proxyLmdbConfig, pathCreator, lmdbEnvFactory, "forward-dest", "forward-dest-index");
    }

    @Override
    public Optional<ForwardDest> get(final int id) {
        return super.get(id).map(name -> new ForwardDest(id, name));
    }

    @Override
    public List<ForwardDest> getAllForwardDests() {
        final List<ForwardDest> list = new ArrayList<>();
        getAll((k, v) -> list.add(new ForwardDest(k, v)));
        return list;
    }

    @Override
    public int getForwardDestId(final String name) {
        return getOrCreateId(name);
    }

    @Override
    public int countForwardDest() {
        return (int) super.count();
    }

    @Override
    RowKey<Integer> createRowKey(final Env<ByteBuffer> env, final Dbi<ByteBuffer> dbi) {
        return new IntegerRowKey(env, dbi);
    }

    @Override
    Serde<String> getValueSerde() {
        return valueSerde;
    }

    @Override
    Serde<Integer> getKeySerde() {
        return keySerde;
    }

    @Override
    int getKeyLength() {
        return Integer.BYTES;
    }


//    @Override
//    public List<ForwardDest> getAllForwardDests() {
//        final List<ForwardDest> list = new ArrayList<>();
//        try (final Txn<ByteBuffer> txn = env.txnRead()) {
//            try (final CursorIterable<ByteBuffer> cursor = feedDbi.iterate(txn)) {
//                for (final KeyVal<ByteBuffer> kv : cursor) {
//                    final int id = intSerde.deserialise(kv.val());
//                    final String name = nameSerde.deserialise(kv.val());
//                    final ForwardDest forwardDest = new ForwardDest(id, name);
//                    list.add(forwardDest);
//                }
//            }
//        }
//        return list;
//    }
//
//    @Override
//    public int getForwardDestId(final String name) {
//        final ByteBuffer valueByteBuffer = nameSerde.serialise(name);
//        final long hash = ByteBufferUtils.xxHash(valueByteBuffer);
//        final ByteBuffer hashByteBuffer = longSerde.serialise(hash);
//        Optional<Integer> id = fetchId(feedDbi, indexDbi, hashByteBuffer, valueByteBuffer);
//        if (id.isPresent()) {
//            return id.get();
//        }
//
//        synchronized (this) {
//            // Try fetch under lock.
//            id = fetchId(feedDbi, indexDbi, hashByteBuffer, valueByteBuffer);
//            if (id.isPresent()) {
//                return id.get();
//            }
//
//            // Couldn't fetch, try put.
//            final int newId = rowKey.next();
//            final ByteBuffer idByteBuffer = intSerde.serialise(newId);
//            put(idByteBuffer, hashByteBuffer, valueByteBuffer);
//            writeQueue.sync();
//
//            return newId;
//        }
//    }
//
//    private void put(final ByteBuffer idBuffer, final ByteBuffer hashByteBuffer, final ByteBuffer value) {
//        writeQueue.write(txn -> {
//            final ByteBuffer existingIndexValue = indexDbi.get(txn, hashByteBuffer);
//            if (existingIndexValue != null) {
//                final ByteBuffer appended;
//                final int bufferSize = existingIndexValue.remaining() + Long.BYTES;
//                try (final MyByteBufferOutput output = new MyByteBufferOutput(bufferSize, bufferSize)) {
//                    output.writeByteBuffer(existingIndexValue);
//                    output.writeByteBuffer(idBuffer);
//                    output.flush();
//                    appended = output.getByteBuffer().flip();
//                }
//                indexDbi.put(txn, hashByteBuffer, appended);
//            } else {
//                indexDbi.put(txn, hashByteBuffer, idBuffer);
//            }
//
//            feedDbi.put(txn, idBuffer, value);
//        });
//    }
//
//    private Optional<Integer> fetchId(final Dbi<ByteBuffer> dbi,
//                                      final Dbi<ByteBuffer> indexDbi,
//                                      final ByteBuffer hashByteBuffer,
//                                      final ByteBuffer valueByteBuffer) {
//        try (final Txn<ByteBuffer> txn = env.txnRead()) {
//            final ByteBuffer value = indexDbi.get(txn, hashByteBuffer);
//            if (value != null) {
//                while (value.hasRemaining()) {
//                    final int id = intSerde.deserialise(value);
//                    final ByteBuffer index = intSerde.serialise(id);
//                    final ByteBuffer storedValue = dbi.get(txn, index);
//                    if (storedValue.equals(valueByteBuffer)) {
//                        return Optional.of(id);
//                    }
//                }
//            }
//            return Optional.empty();
//        }
//    }
//
//    @Override
//    public int countForwardDest() {
//        try (final Txn<ByteBuffer> txn = env.txnRead()) {
//            return (int) LmdbUtil.count(txn, feedDbi);
//        }
//    }
//
//    @Override
//    public void clear() {
//        writeQueue.write(txn -> LmdbUtil.deleteAll(txn, feedDbi));
//        writeQueue.write(txn -> LmdbUtil.deleteAll(txn, indexDbi));
//        writeQueue.commit();
//        writeQueue.sync();
//
//        rowKey = new IntegerRowKey(env, feedDbi);
//    }
}
