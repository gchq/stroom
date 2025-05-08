package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.KV;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.state.StateSearchHelper.Context;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.io.FileUtil;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.Env.Builder;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The simplest schema that just stores the key and value directly.
 */
abstract class SimpleKeySchema extends AbstractSchema<String, Val> {

    private static final byte[] NAME = "db".getBytes(UTF_8);

    private final PutFlags[] putFlags;
    private final ValSerde valSerde;

    SimpleKeySchema(final PlanBEnv envSupport,
                    final ByteBuffers byteBuffers,
                    final Boolean overwrite,
                    final ValSerde valSerde) {
        super(envSupport, byteBuffers);
        this.valSerde = valSerde;
        this.putFlags = overwrite
                ? new PutFlags[]{}
                : new PutFlags[]{PutFlags.MDB_NOOVERWRITE};
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<String, Val> kv) {
        useKey(kv.key(), keyByteBuffer -> {
            final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
            valSerde.write(writeTxn, kv.val(), valueByteBuffer -> {
                if (dbi.put(writer.getWriteTxn(), keyByteBuffer, valueByteBuffer, putFlags)) {
                    writer.tryCommit();
                }
            });
            return null;
        });
    }

    @Override
    public void merge(final Path source) {
        env.write(writer -> {
            final Builder<ByteBuffer> builder = Env.create()
                    .setMaxDbs(1)
                    .setMaxReaders(1);
            try (final Env<ByteBuffer> sourceEnv = builder.open(source.toFile(),
                    EnvFlags.MDB_NOTLS,
                    EnvFlags.MDB_NOLOCK,
                    EnvFlags.MDB_RDONLY_ENV)) {
                final Dbi<ByteBuffer> sourceDbi = sourceEnv.openDbi(NAME);
                try (final Txn<ByteBuffer> readTxn = sourceEnv.txnRead()) {
                    try (final CursorIterable<ByteBuffer> cursorIterable = sourceDbi.iterate(readTxn)) {
                        for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                            if (dbi.put(writer.getWriteTxn(), keyVal.key(), keyVal.val(), putFlags)) {
                                writer.tryCommit();
                            }
                        }
                    }
                }
            }
        });

        // Delete source now we have merged.
        FileUtil.deleteDir(source);
    }

    @Override
    public Val get(final String key) {
        return useKey(key, keyByteBuffer -> env.read(readTxn -> {
            final ByteBuffer valueByteBuffer = dbi.get(readTxn, keyByteBuffer);
            if (valueByteBuffer == null) {
                return null;
            }
            return valSerde.read(readTxn, valueByteBuffer);
        }));
    }

    abstract <R> R useKey(final String key, Function<ByteBuffer, R> function);

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ExpressionPredicateFactory expressionPredicateFactory,
                       final ValuesConsumer consumer) {
        env.read(readTxn -> {
            final ValuesExtractor valuesExtractor = StateSearchHelper.createValuesExtractor(
                    fieldIndex,
                    getKeyExtractionFunction(),
                    getValExtractionFunction(readTxn));
            StateSearchHelper.search(
                    readTxn,
                    criteria,
                    fieldIndex,
                    dateTimeSettings,
                    expressionPredicateFactory,
                    consumer,
                    valuesExtractor,
                    env,
                    dbi);
            return null;
        });
    }

    private Function<Context, Val> getKeyExtractionFunction() {
        return context -> {
            final ByteBuffer byteBuffer = context.kv().key().duplicate();
            return createKeyVal(byteBuffer);
        };
    }

    private Function<Context, Val> getValExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> valSerde.read(readTxn, context.kv().val());
    }

    abstract Val createKeyVal(ByteBuffer byteBuffer);
}
