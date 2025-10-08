package stroom.lmdb2;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb.stream.LmdbEntry;
import stroom.lmdb.stream.LmdbIterable;
import stroom.lmdb.stream.LmdbKeyRange;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

public class LmdbDb {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbDb.class);

    private final Env<ByteBuffer> env;
    private final Dbi<ByteBuffer> dbi;
    private final String name;
    private final Set<DbiFlags> dbiFlags;
    private final LmdbErrorHandler errorHandler;

    public LmdbDb(final Env<ByteBuffer> env,
                  final String name,
                  final Set<DbiFlags> dbiFlags,
                  final LmdbErrorHandler errorHandler) {
        this.env = env;
        this.name = name;
        this.dbiFlags = dbiFlags;
        this.errorHandler = errorHandler;

        try {
            final DbiFlags[] envFlagsArr = dbiFlags.toArray(new DbiFlags[0]);
            final byte[] nameBytes = name == null
                    ? null
                    : name.getBytes(UTF_8);
            dbi = env.openDbi(nameBytes, envFlagsArr);
        } catch (final RuntimeException e) {
            errorHandler.error(e);
            throw e;
        }
    }

    public boolean put(final WriteTxn writeTxn,
                       final ByteBuffer key,
                       final ByteBuffer val,
                       final PutFlags... flags) {
        try {
            return dbi.put(writeTxn.get(), key, val, flags);
        } catch (final RuntimeException e) {
            try {
                final String msg = LogUtil.message(
                        "Error putting key:\n{}\nval {}\nflags {}",
                        ByteBufferUtils.byteBufferInfo(key),
                        ByteBufferUtils.byteBufferInfo(val),
                        flags);
                error(msg, e);
            } catch (final RuntimeException e2) {
                error(e);
            }
            throw e;
        }
    }

    public ByteBuffer get(final AbstractTxn txn, final ByteBuffer key) {
        try {
            return dbi.get(txn.get(), key);
        } catch (final RuntimeException e) {
            try {
                final String msg = LogUtil.message("Error getting key: {}", ByteBufferUtils.byteBufferInfo(key));
                error(msg, e);
            } catch (final RuntimeException e2) {
                error(e);
            }
            throw e;
        }
    }

    private void read(final Consumer<Txn<ByteBuffer>> consumer) {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            consumer.accept(txn);
        } catch (final RuntimeException e) {
            error(e);
            throw e;
        }
    }

    private <R> R readResult(final Function<Txn<ByteBuffer>, R> function) {
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            return function.apply(txn);
        } catch (final RuntimeException e) {
            error(e);
            throw e;
        }
    }

    public <R> R iterateResult(final AbstractTxn txn,
                               final LmdbKeyRange keyRange,
                               final Function<Iterator<LmdbEntry>, R> iteratorConsumer) {
        try (final LmdbIterable iterable = LmdbIterable.create(txn.get(), dbi, keyRange)) {
            try {
                final Iterator<LmdbEntry> iterator = iterable.iterator();
                return iteratorConsumer.apply(iterator);
            } catch (final Throwable e) {
                error(e);
            }
        } catch (final Throwable e) {
            error(e);
        }
        return null;
    }

    public void iterate(final AbstractTxn txn,
                        final LmdbKeyRange keyRange,
                        final Consumer<Iterator<LmdbEntry>> iteratorConsumer) {
        try (final LmdbIterable iterable = LmdbIterable.create(txn.get(), dbi, keyRange)) {
            try {
                final Iterator<LmdbEntry> iterator = iterable.iterator();
                iteratorConsumer.accept(iterator);
            } catch (final Throwable e) {
                error(e);
            }
        } catch (final Throwable e) {
            error(e);
        }
    }

    public boolean delete(final WriteTxn txn, final ByteBuffer key) {
        try {
            return dbi.delete(txn.get(), key);
        } catch (final RuntimeException e) {
            try {
                final String msg = LogUtil.message("Error deleting entry with key: {}",
                        ByteBufferUtils.byteBufferInfo(key));
                error(msg, e);
            } catch (final RuntimeException e2) {
                error(e);
            }
            throw e;
        }
    }

    public boolean delete(final WriteTxn txn, final ByteBuffer key, final ByteBuffer value) {
        try {
            return dbi.delete(txn.get(), key, value);
        } catch (final RuntimeException e) {
            try {
                final String msg = LogUtil.message(
                        "Error deleting entry with key:\n{}\nval {}",
                        ByteBufferUtils.byteBufferInfo(key),
                        ByteBufferUtils.byteBufferInfo(value));
                error(msg, e);
            } catch (final RuntimeException e2) {
                error(e);
            }
            throw e;
        }
    }

    public long count(final ReadTxn txn) {
        return dbi.stat(txn.get()).entries;
    }

    public long count() {
        return readResult(txn -> dbi.stat(txn).entries);
    }

    public void drop(final WriteTxn writeTxn) {
        try {
            dbi.drop(writeTxn.get());
        } catch (final RuntimeException e) {
            error(e);
            throw e;
        }
    }

    private void error(final Throwable e) {
        LOGGER.debug(e::getMessage, e);
        errorHandler.error(e);
    }

    private void error(final String msg, final Throwable e) {
        LOGGER.debug(msg, e);
        errorHandler.error(e);
    }

    public Dbi<ByteBuffer> getDbi() {
        return dbi;
    }

    @Override
    public String toString() {
        return "LmdbDb{" +
               "env=" + env +
               ", name='" + name + '\'' +
               ", dbiFlags=" + dbiFlags +
               '}';
    }
}
