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
abstract class SimpleKeySchema extends AbstractSchema<String, StateValue> {

    private static final byte[] NAME = "db".getBytes(UTF_8);

    private final PutFlags[] putFlags;
    private final StateValueSerde stateValueSerde;

    SimpleKeySchema(final PlanBEnv envSupport,
                    final ByteBuffers byteBuffers,
                    final Boolean overwrite,
                    final StateValueSerde stateValueSerde) {
        super(envSupport, byteBuffers);
        this.stateValueSerde = stateValueSerde;
        this.putFlags = overwrite
                ? new PutFlags[]{}
                : new PutFlags[]{PutFlags.MDB_NOOVERWRITE};
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<String, StateValue> kv) {
        useKey(kv.key(), keyByteBuffer -> {
            stateValueSerde.write(kv.val(), valueByteBuffer -> {
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
    public StateValue get(final String key) {
        return useKey(key, keyByteBuffer -> env.read(readTxn -> {
            final ByteBuffer valueByteBuffer = dbi.get(readTxn, keyByteBuffer);
            if (valueByteBuffer == null) {
                return null;
            }
            return stateValueSerde.read(valueByteBuffer);
        }));
    }

    abstract <R> R useKey(final String key, Function<ByteBuffer, R> function);

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ExpressionPredicateFactory expressionPredicateFactory,
                       final ValuesConsumer consumer) {
        final ValuesExtractor valuesExtractor = StateSearchHelper.createValuesExtractor(
                fieldIndex,
                getKeyExtractionFunction(),
                getStateValueExtractionFunction());
        StateSearchHelper.search(
                criteria,
                fieldIndex,
                dateTimeSettings,
                expressionPredicateFactory,
                consumer,
                valuesExtractor,
                env,
                dbi);
    }

    private Function<Context, Val> getKeyExtractionFunction() {
        return context -> {
            final ByteBuffer byteBuffer = context.kv().key().duplicate();
            return createKeyVal(byteBuffer);
        };
    }

    private Function<Context, StateValue> getStateValueExtractionFunction() {
        return context -> stateValueSerde.read(context.kv().val());
    }

    abstract Val createKeyVal(ByteBuffer byteBuffer);
}
