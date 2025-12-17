/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.planb.impl.db.rangestate;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb.stream.LmdbEntry;
import stroom.lmdb.stream.LmdbIterable;
import stroom.lmdb.stream.LmdbKeyRange;
import stroom.lmdb2.KV;
import stroom.planb.impl.data.RangeState;
import stroom.planb.impl.data.RangeState.Key;
import stroom.planb.impl.db.AbstractDb;
import stroom.planb.impl.db.Count;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.PlanBSearchHelper;
import stroom.planb.impl.db.PlanBSearchHelper.Context;
import stroom.planb.impl.db.PlanBSearchHelper.Converter;
import stroom.planb.impl.db.PlanBSearchHelper.LazyKV;
import stroom.planb.impl.db.PlanBSearchHelper.ValuesExtractor;
import stroom.planb.impl.db.SchemaInfo;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.serde.rangestate.ByteRangeKeySerde;
import stroom.planb.impl.serde.rangestate.IntegerRangeKeySerde;
import stroom.planb.impl.serde.rangestate.LongRangeKeySerde;
import stroom.planb.impl.serde.rangestate.RangeKeySerde;
import stroom.planb.impl.serde.rangestate.ShortRangeKeySerde;
import stroom.planb.impl.serde.valtime.ValTime;
import stroom.planb.impl.serde.valtime.ValTimeSerde;
import stroom.planb.impl.serde.valtime.ValTimeSerdeFactory;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RangeStateSettings;
import stroom.planb.shared.RangeType;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.Values;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.io.FileUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Function;

public class RangeStateDb extends AbstractDb<Key, Val> {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final RangeKeySerde keySerde;
    private final ValTimeSerde valueSerde;
    private final UsedLookupsRecorder keyRecorder;
    private final UsedLookupsRecorder valueRecorder;

    private RangeStateDb(final PlanBEnv env,
                         final ByteBuffers byteBuffers,
                         final PlanBDoc doc,
                         final RangeStateSettings settings,
                         final RangeKeySerde keySerde,
                         final ValTimeSerde valueSerde,
                         final HashClashCommitRunnable hashClashCommitRunnable) {
        super(env,
                byteBuffers,
                doc,
                settings.overwrite(),
                hashClashCommitRunnable,
                new SchemaInfo(
                        CURRENT_SCHEMA_VERSION,
                        JsonUtil.writeValueAsString(settings.getKeySchema()),
                        JsonUtil.writeValueAsString(settings.getValueSchema())));
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        this.keyRecorder = keySerde.getUsedLookupsRecorder(env);
        this.valueRecorder = valueSerde.getUsedLookupsRecorder(env);
    }

    public static RangeStateDb create(final Path path,
                                      final ByteBuffers byteBuffers,
                                      final PlanBDoc doc,
                                      final boolean readOnly) {
        // Ensure all settings are non null.
        final RangeStateSettings settings;
        if (doc.getSettings() instanceof final RangeStateSettings rangeStateSettings) {
            settings = rangeStateSettings;
        } else {
            settings = new RangeStateSettings.Builder().build();
        }

        final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        final PlanBEnv env = new PlanBEnv(path,
                settings.getMaxStoreSize(),
                20,
                readOnly,
                hashClashCommitRunnable);
        try {
            final RangeKeySerde keySerde = createKeySerde(
                    settings.getKeySchema().getRangeType(),
                    byteBuffers);
            final ValTimeSerde valueSerde = ValTimeSerdeFactory.createValueSerde(
                    settings.getValueSchema().getStateValueType(),
                    settings.getValueSchema().getHashLength(),
                    env,
                    byteBuffers,
                    hashClashCommitRunnable);
            return new RangeStateDb(
                    env,
                    byteBuffers,
                    doc,
                    settings,
                    keySerde,
                    valueSerde,
                    hashClashCommitRunnable);
        } catch (final RuntimeException e) {
            // Close the env if we get any exceptions to prevent them staying open.
            try {
                env.close();
            } catch (final Exception e2) {
                LOGGER.debug(LogUtil.message("store={}, message={}", doc.getName(), e.getMessage()), e);
            }
            throw e;
        }
    }

    private static RangeKeySerde createKeySerde(final RangeType rangeType,
                                                final ByteBuffers byteBuffers) {
        return switch (rangeType) {
            case BYTE -> new ByteRangeKeySerde(byteBuffers);
            case SHORT -> new ShortRangeKeySerde(byteBuffers);
            case INT -> new IntegerRangeKeySerde(byteBuffers);
            case LONG -> new LongRangeKeySerde(byteBuffers);
        };
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<Key, Val> kv) {
        final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
        keySerde.write(writeTxn, kv.key(), keyByteBuffer ->
                valueSerde.write(writeTxn, new ValTime(kv.val(), Instant.now()), valueByteBuffer ->
                        dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, putFlags)));
        writer.tryCommit();
    }

    @Override
    public void merge(final Path source) {
        env.write(writer -> {
            try (final RangeStateDb sourceDb = RangeStateDb.create(source, byteBuffers, doc, true)) {
                // Validate that the source DB has the same schema.
                validateSchema(schemaInfo, sourceDb.getSchemaInfo());

                // Merge.
                sourceDb.env.read(readTxn -> {
                    sourceDb.iterate(readTxn, (key, val) -> {
                        if (sourceDb.keySerde.usesLookup(key) || sourceDb.valueSerde.usesLookup(val)) {
                            // We need to do a full read and merge.
                            final Key k = sourceDb.keySerde.read(readTxn, key);
                            final Val v = sourceDb.valueSerde.read(readTxn, val).val();
                            insert(writer, new RangeState(k, v));
                        } else {
                            // Quick merge.
                            if (dbi.put(writer.getWriteTxn(), key, val, putFlags)) {
                                writer.tryCommit();
                            }
                        }
                    });
                    return null;
                });
            }
        });

        // Delete source now we have merged.
        FileUtil.deleteDir(source);
    }

    @Override
    public Val get(final Key key) {
        return env.read(readTxn -> keySerde.toBufferForGet(readTxn, key, optionalKeyByteBuffer ->
                optionalKeyByteBuffer.map(keyByteBuffer -> {
                    final ByteBuffer valueByteBuffer = dbi.get(readTxn, keyByteBuffer);
                    if (valueByteBuffer == null) {
                        return null;
                    }
                    return NullSafe.get(valueSerde.read(readTxn, valueByteBuffer), ValTime::val);
                }).orElse(null)));
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ExpressionPredicateFactory expressionPredicateFactory,
                       final ValuesConsumer consumer) {
        env.read(readTxn -> {
            final ValuesExtractor valuesExtractor = createValuesExtractor(
                    fieldIndex,
                    getKeyExtractionFunction(readTxn),
                    getValExtractionFunction(readTxn));
            PlanBSearchHelper.search(
                    readTxn,
                    criteria,
                    fieldIndex,
                    dateTimeSettings,
                    expressionPredicateFactory,
                    consumer,
                    valuesExtractor,
                    dbi);
            return null;
        });
    }

    private Function<Context, Key> getKeyExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> keySerde.read(readTxn, context.key().duplicate());
    }

    private Function<Context, Val> getValExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> NullSafe.get(valueSerde.read(readTxn, context.val().duplicate()), ValTime::val);
    }

    public RangeState getState(final RangeStateRequest request) {
        return env.read(readTxn ->
                keySerde.toKeyStart(request.key(), start -> {
                    RangeState result = null;
                    final LmdbKeyRange keyRange = LmdbKeyRange.builder().start(start).reverse().build();
                    try (final LmdbIterable iterable = LmdbIterable.create(readTxn, dbi, keyRange)) {
                        for (final LmdbEntry entry : iterable) {
                            final Key k = keySerde.read(readTxn, entry.getKey());
                            if (k.getKeyEnd() < request.key()) {
                                return result;
                            } else if (k.getKeyStart() <= request.key()) {
                                final Val value = valueSerde.read(readTxn, entry.getVal()).val();
                                result = new RangeState(k, value);
                            }
                        }
                    }
                    return result;
                }));
    }

    public static ValuesExtractor createValuesExtractor(final FieldIndex fieldIndex,
                                                        final Function<Context, Key> keyFunction,
                                                        final Function<Context, Val> valFunction) {
        final String[] fields = fieldIndex.getFields();
        final RangeStateConverter[] converters = new RangeStateConverter[fields.length];
        for (int i = 0; i < fields.length; i++) {
            converters[i] = switch (fields[i]) {
                case RangeStateFields.KEY_START -> kv -> ValLong.create(kv.getKey().getKeyStart());
                case RangeStateFields.KEY_END -> kv -> ValLong.create(kv.getKey().getKeyEnd());
                case RangeStateFields.VALUE_TYPE -> kv -> ValString.create(kv.getValue().type().toString());
                case RangeStateFields.VALUE -> LazyKV::getValue;
                default -> kv -> ValNull.INSTANCE;
            };
        }
        return (readTxn, key, val) -> {
            final Context context = new Context(readTxn, key, val);
            final LazyKV<Key, Val> lazyKV = new LazyKV<>(context, keyFunction, valFunction);
            final Val[] values = new Val[fields.length];
            for (int i = 0; i < fields.length; i++) {
                values[i] = converters[i].convert(lazyKV);
            }
            return Values.of(values);
        };
    }

    @Override
    public long deleteOldData(final Instant deleteBefore, final boolean useStateTime) {
        return env.write(writer -> {
            final long count = deleteOldData(writer, deleteBefore);

            // Delete unused lookup keys.
            if (!Thread.currentThread().isInterrupted()) {
                env.read(readTxn -> {
                    keyRecorder.deleteUnused(readTxn, writer);
                    valueRecorder.deleteUnused(readTxn, writer);
                    return null;
                });
            }

            return count;
        });
    }

    private long deleteOldData(final LmdbWriter writer,
                               final Instant deleteBefore) {
        return env.read(readTxn -> {
            final Count changeCount = new Count();
            iterate(readTxn, (key, val) -> {
                final ValTime value = valueSerde.read(readTxn, val.duplicate());

                if (value.insertTime().isBefore(deleteBefore)) {
                    // If this is data we no longer want to retain then delete it.
                    dbi.delete(writer.getWriteTxn(), key);
                    changeCount.increment();
                } else {
                    // Record used lookup keys.
                    keyRecorder.recordUsed(writer, key);
                    valueRecorder.recordUsed(writer, val);
                }
                writer.tryCommit();
            });
            writer.commit();
            return changeCount.get();
        });
    }

    @Override
    public long condense(final Instant condenseBefore) {
        return 0;
    }

    public interface RangeStateConverter extends Converter<Key, Val> {

    }
}
