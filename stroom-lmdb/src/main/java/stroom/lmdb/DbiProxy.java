package stroom.lmdb;

import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.Cursor;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A thin wrapper around a {@link Dbi} to abstract the user of this class from the actual
 * {@link Dbi} instance. This allows us to recreate the {@link Dbi} instance, e.g. after
 * doing a copy/compact of the {@link org.lmdbjava.Env}.
 * <p>
 * {@link DbiProxy} is constructed with a {@link Supplier} so the {@link Dbi} can be renewed
 * when required
 * </p>
 */
public class DbiProxy {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DbiProxy.class);

    private final LmdbEnv lmdbEnv;
    private final Supplier<Dbi<ByteBuffer>> dbiSupplier;
    private final String name;
    private volatile Dbi<ByteBuffer> dbi;

    private DbiProxy(final LmdbEnv lmdbEnv,
                     final String name,
                     final Supplier<Dbi<ByteBuffer>> dbiSupplier) {
        this.lmdbEnv = lmdbEnv;
        this.name = name;
        this.dbiSupplier = Objects.requireNonNull(dbiSupplier);
        this.dbi = dbiSupplier.get();
        LOGGER.debug(() -> LogUtil.message("Initialising dbi {}:{} with name '{}' in lmdbEnv: {}",
                System.identityHashCode(this),
                System.identityHashCode(dbi),
                name,
                lmdbEnv.getName().orElseGet(() -> lmdbEnv.getLocalDir().toString())));
    }

    /**
     * @param lmdbEnv     The {@link LmdbEnv} for logging purposes
     * @param name        The name of the Dbi
     * @param dbiSupplier {@link Supplier#get()} will be called to initialise the {@link DbiProxy}
     */
    public static DbiProxy create(final LmdbEnv lmdbEnv,
                                  final String name,
                                  final Supplier<Dbi<ByteBuffer>> dbiSupplier) {
        return new DbiProxy(lmdbEnv, name, dbiSupplier);
    }

    /**
     * Remove the {@link Dbi} so it can no longer be used.
     */
    void clear() {
        LOGGER.debug(() -> LogUtil.message("Clearing dbi {}:{} with name '{}' in lmdbEnv: {}",
                System.identityHashCode(this),
                System.identityHashCode(dbi),
                name,
                lmdbEnv.getName().orElseGet(() -> lmdbEnv.getLocalDir().toString())));
        dbi = null;
    }

    /**
     * If there is no {@link Dbi} create a new one using the internal supplier.
     */
    void renew() {
        if (dbi == null) {
            synchronized (this) {
                if (dbi == null) {
                    dbi = dbiSupplier.get();
                    LOGGER.debug(() -> LogUtil.message("Renewing dbi {}:{} with name '{}' in lmdbEnv: {}",
                            System.identityHashCode(this),
                            System.identityHashCode(dbi),
                            name,
                            lmdbEnv.getName().orElseGet(() -> lmdbEnv.getLocalDir().toString())));
                }
            }
        }
    }

    /**
     * @return The underlying dbi. Do NOT hold onto the dbi instance as it may be mutated.
     */
    Dbi<ByteBuffer> getDbi() {
        try {
            return dbi;
        } catch (NullPointerException e) {
            throw new IllegalStateException(LogUtil.message(
                    "dbi with name '{}' in env '{}' is not initialised",
                    name,
                    lmdbEnv.getName().orElseGet(() ->
                            FileUtil.getCanonicalPath(lmdbEnv.getLocalDir()))));
        }
    }

    /**
     * Use the underlying {@link Dbi} instance. Do NOT hold onto the dbi instance as it may be mutated.
     * Best avoid using it.
     */
    public void withDbi(final Consumer<Dbi<ByteBuffer>> dbiConsumer) {
        dbiConsumer.accept(dbi);
    }

    @Override
    public String toString() {
        return "DbiProxy{" +
               "name='" + name + '\'' +
               " env='" + lmdbEnv.getName().orElseGet(() ->
                FileUtil.getCanonicalPath(lmdbEnv.getLocalDir())) + '\'' +
               '}';
    }


    // --------------------------------------------------------------------------------
    // Following methods all delegate to the underlying Dbi
    // --------------------------------------------------------------------------------

    public void close() {
        getDbi().close();
    }

    public boolean delete(final ByteBuffer key) {
        return getDbi().delete(key);
    }

    public boolean delete(final Txn<ByteBuffer> txn, final ByteBuffer key) {
        return getDbi().delete(txn, key);
    }

    public boolean delete(final Txn<ByteBuffer> txn, final ByteBuffer key, final ByteBuffer val) {
        return getDbi().delete(txn, key, val);
    }

    public void drop(final Txn<ByteBuffer> txn) {
        getDbi().drop(txn);
    }

    public void drop(final Txn<ByteBuffer> txn, final boolean delete) {
        getDbi().drop(txn, delete);
    }

    public ByteBuffer get(final Txn<ByteBuffer> txn, final ByteBuffer key) {
        return getDbi().get(txn, key);
    }

    public byte[] getName() {
        return getDbi().getName();
    }

    public CursorIterable<ByteBuffer> iterate(final Txn<ByteBuffer> txn) {
        return getDbi().iterate(txn);
    }

    public CursorIterable<ByteBuffer> iterate(final Txn<ByteBuffer> txn,
                                              final KeyRange<ByteBuffer> range) {
        return getDbi().iterate(txn, range);
    }

    public CursorIterable<ByteBuffer> iterate(final Txn<ByteBuffer> txn,
                                              final KeyRange<ByteBuffer> range,
                                              final Comparator<ByteBuffer> comparator) {
        return getDbi().iterate(txn, range, comparator);
    }

    public List<DbiFlags> listFlags(final Txn<ByteBuffer> txn) {
        return getDbi().listFlags(txn);
    }

    public Cursor<ByteBuffer> openCursor(final Txn<ByteBuffer> txn) {
        return getDbi().openCursor(txn);
    }

    public void put(final ByteBuffer key, final ByteBuffer val) {
        getDbi().put(key, val);
    }

    public boolean put(final Txn<ByteBuffer> txn,
                       final ByteBuffer key,
                       final ByteBuffer val,
                       final PutFlags... flags) {
        return getDbi().put(txn, key, val, flags);
    }

    public ByteBuffer reserve(final Txn<ByteBuffer> txn,
                              final ByteBuffer key,
                              final int size,
                              final PutFlags... op) {
        return getDbi().reserve(txn, key, size, op);
    }

    public Stat stat(final Txn<ByteBuffer> txn) {
        return getDbi().stat(txn);
    }
}
