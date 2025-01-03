package stroom.planb.impl.dao;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb2.LmdbEnvDir;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class AbstractStateDao<T> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractStateDao.class);

    final ByteBufferFactory byteBufferFactory;
    final Env<ByteBuffer> env;
    final Dbi<ByteBuffer> dbi;
    Txn<ByteBuffer> txn;

    public AbstractStateDao(final LmdbEnvDir lmdbEnvDir,
                            final ByteBufferFactory byteBufferFactory,
                            final String name) {
        this.byteBufferFactory = byteBufferFactory;
        LOGGER.info(() -> "Creating: " + name);


        final Env.Builder<ByteBuffer> builder = Env.create()
                .setMapSize(LmdbConfig.DEFAULT_MAX_STORE_SIZE.getBytes())
                .setMaxDbs(1)
                .setMaxReaders(1);

//        LOGGER.debug("Creating LMDB environment in dir {}, maxSize: {}, maxDbs {}, maxReaders {}, "
//                     + "envFlags {}",
//                lmdbEnvDir.toString(),
//                maxStoreSize,
//                maxDbs,
//                maxReaders,
//                envFlags);

        env = builder.open(lmdbEnvDir.getEnvDir().toFile(), EnvFlags.MDB_NOTLS);

        final byte[] nameBytes = name == null
                ? null
                : name.getBytes(UTF_8);
        dbi = env.openDbi(nameBytes, DbiFlags.MDB_CREATE, DbiFlags.MDB_DUPSORT);




//        this.env = LmdbEnv
//                .builder()
//                .lmdbEnvDir(lmdbEnvDir)
//                .maxDbs(1)
//                .addEnvFlag(EnvFlags.MDB_NOTLS)
//                .maxReaders(1)
//                .errorHandler(this::error)
//                .build();
//        this.db = env.openDb(key, DbiFlags.MDB_CREATE, DbiFlags.MDB_DUPSORT);
    }

//    private void error(final Throwable e) {
//        LOGGER.debug(e::getMessage, e);
//        if (e instanceof Env.MapFullException) {
//            throw new RuntimeException("Unable to add ad LMDB has reached max capacity of " +
//                                       env.getMaxStoreSize(), e);
//        } else if (e instanceof LmdbException) {
//            String message = e.getMessage();
//            if (message != null) {
//                // Remove native LMDB error code as it means nothing to users.
//                if (message.endsWith(")")) {
//                    final int index = message.lastIndexOf(" (");
//                    if (index != -1) {
//                        message = message.substring(0, index);
//                    }
//                }
//                final String msg = message;
//                throw new RuntimeException(msg, e);
//            }
//        } else {
//            throw new RuntimeException(e.getMessage(), e);
//        }
//    }

    public abstract void insert(final T state);

    public void write(final ByteBuffer keyByteBuffer,
                      final ByteBuffer valueByteBuffer) {
        if (txn == null) {
            txn = env.txnWrite();
        }
        dbi.put(txn, keyByteBuffer, valueByteBuffer);
    }

    void commit() {
        if (txn != null) {
            try {
                txn.commit();
            } finally {
                try {
                    txn.close();
                } finally {
                    txn = null;
                }
            }
        }
    }

    public void close() {
        commit();
//        LOGGER.info(() -> "Inserted " + db.count() + " rows");
        env.close();
    }

//

//
//    static final CqlDuration TEN_SECONDS = CqlDuration.from("PT10S");
//
//    final Provider<CqlSession> sessionProvider;
//    final CqlIdentifier table;
//
//    public AbstractStateDao(final Provider<CqlSession> sessionProvider,
//                            final CqlIdentifier table) {
//        this.sessionProvider = sessionProvider;
//        this.table = table;
//    }
//
//    abstract void createTables();
//
//    final void dropTables() {
//        final SimpleStatement statement = dropTable(table)
//                .ifExists()
//                .build();
//        sessionProvider.get().execute(statement);
//    }
//
//    public abstract void insert(List<T> rows);
//
//    public abstract void delete(List<T> rows);
//
//    public void doDelete(final List<T> rows,
//                         final SimpleStatement deleteStatement,
//                         final Function<T, Object[]> valuesFunction) {
//        Objects.requireNonNull(rows, "Null state list");
//
//        final PreparedStatement preparedStatement = sessionProvider.get().prepare(deleteStatement);
//
//        try (final BatchStatementExecutor executor = new BatchStatementExecutor(sessionProvider)) {
//            for (final T row : rows) {
//                final Object[] values = valuesFunction.apply(row);
//                executor.addStatement(preparedStatement.bind(values));
//            }
//        }
//    }
//
//    public abstract void search(ExpressionCriteria criteria,
//                                FieldIndex fieldIndex,
//                                DateTimeSettings dateTimeSettings,
//                                ValuesConsumer valuesConsumer);
//
//    public long count() {
//        final SimpleStatement statement = selectFrom(table).countAll().build();
//        return sessionProvider.get().execute(statement).one().getLong(0);
//    }
//
//    public void condense(Instant oldest) {
//        // Not all implementations condense data.
//    }
//
//    public abstract void removeOldData(Instant oldest);
//
//    PreparedStatement prepare(final SimpleStatement statement) {
//        PreparedStatement preparedStatement;
//        try {
//            preparedStatement = sessionProvider.get().prepare(statement);
//        } catch (final InvalidQueryException e) {
//            LOGGER.debug(e::getMessage, e);
//            if (e.getMessage().contains("unconfigured table")) {
//                createTables();
//            }
//            preparedStatement = sessionProvider.get().prepare(statement);
//        }
//        return preparedStatement;
//    }
}
