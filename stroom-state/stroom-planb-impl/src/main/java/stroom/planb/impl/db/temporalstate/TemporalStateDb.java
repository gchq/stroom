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

package stroom.planb.impl.db.temporalstate;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb.stream.LmdbEntry;
import stroom.lmdb.stream.LmdbIterable;
import stroom.lmdb.stream.LmdbKeyRange;
import stroom.lmdb2.KV;
import stroom.planb.impl.data.TemporalState;
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
import stroom.planb.impl.serde.temporalkey.TemporalKey;
import stroom.planb.impl.serde.temporalkey.TemporalKeySerde;
import stroom.planb.impl.serde.temporalkey.TemporalKeySerdeFactory;
import stroom.planb.impl.serde.time.DayTimeSerde;
import stroom.planb.impl.serde.time.HourTimeSerde;
import stroom.planb.impl.serde.time.MillisecondTimeSerde;
import stroom.planb.impl.serde.time.MinuteTimeSerde;
import stroom.planb.impl.serde.time.NanoTimeSerde;
import stroom.planb.impl.serde.time.SecondTimeSerde;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.valtime.ValTime;
import stroom.planb.impl.serde.valtime.ValTimeSerde;
import stroom.planb.impl.serde.valtime.ValTimeSerdeFactory;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.TemporalPrecision;
import stroom.planb.shared.TemporalStateSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
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
import java.util.Objects;
import java.util.function.Function;

public class TemporalStateDb extends AbstractDb<TemporalKey, Val> {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final TimeSerde timeSerde;
    private final TemporalKeySerde keySerde;
    private final ValTimeSerde valueSerde;
    private final UsedLookupsRecorder keyRecorder;
    private final UsedLookupsRecorder valueRecorder;

    private TemporalStateDb(final PlanBEnv env,
                            final ByteBuffers byteBuffers,
                            final PlanBDoc doc,
                            final TemporalStateSettings settings,
                            final TimeSerde timeSerde,
                            final TemporalKeySerde keySerde,
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
        this.timeSerde = timeSerde;
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        this.keyRecorder = keySerde.getUsedLookupsRecorder(env);
        this.valueRecorder = valueSerde.getUsedLookupsRecorder(env);
    }

    public static TemporalStateDb create(final Path path,
                                         final ByteBuffers byteBuffers,
                                         final PlanBDoc doc,
                                         final boolean readOnly) {
        // Ensure all settings are non null.
        final TemporalStateSettings settings;
        if (doc.getSettings() instanceof final TemporalStateSettings temporalStateSettings) {
            settings = temporalStateSettings;
        } else {
            settings = new TemporalStateSettings.Builder().build();
        }

        final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        final PlanBEnv env = new PlanBEnv(path,
                settings.getMaxStoreSize(),
                20,
                readOnly,
                hashClashCommitRunnable);
        try {
            final TimeSerde timeSerde = createTimeSerde(settings.getKeySchema().getTemporalPrecision());
            final TemporalKeySerde keySerde = TemporalKeySerdeFactory.createKeySerde(
                    doc,
                    settings.getKeySchema().getKeyType(),
                    settings.getKeySchema().getHashLength(),
                    env,
                    byteBuffers,
                    timeSerde,
                    hashClashCommitRunnable);
            final ValTimeSerde valueSerde = ValTimeSerdeFactory.createValueSerde(
                    settings.getValueSchema().getStateValueType(),
                    settings.getValueSchema().getHashLength(),
                    env,
                    byteBuffers,
                    hashClashCommitRunnable);
            return new TemporalStateDb(
                    env,
                    byteBuffers,
                    doc,
                    settings,
                    timeSerde,
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

    private static TimeSerde createTimeSerde(final TemporalPrecision temporalPrecision) {
        return switch (temporalPrecision) {
            case NANOSECOND -> new NanoTimeSerde();
            case MILLISECOND -> new MillisecondTimeSerde();
            case SECOND -> new SecondTimeSerde();
            case MINUTE -> new MinuteTimeSerde();
            case HOUR -> new HourTimeSerde();
            case DAY -> new DayTimeSerde();
        };
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<TemporalKey, Val> kv) {
        final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
        keySerde.write(writeTxn, kv.key(), keyByteBuffer ->
                valueSerde.write(writeTxn, new ValTime(kv.val(), Instant.now()), valueByteBuffer ->
                        dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, putFlags)));
        writer.tryCommit();
    }

    @Override
    public void merge(final Path source) {
        env.write(writer -> {
            try (final TemporalStateDb sourceDb = TemporalStateDb.create(source, byteBuffers, doc, true)) {
                // Validate that the source DB has the same schema.
                validateSchema(schemaInfo, sourceDb.getSchemaInfo());

                // Merge.
                sourceDb.env.read(readTxn -> {
                    sourceDb.iterate(readTxn, (key, val) -> {
                        if (sourceDb.keySerde.usesLookup(key) || sourceDb.valueSerde.usesLookup(val)) {
                            // We need to do a full read and merge.
                            final TemporalKey temporalKey = sourceDb.keySerde.read(readTxn, key);
                            final Val value = sourceDb.valueSerde.read(readTxn, val).val();
                            insert(writer, new TemporalState(temporalKey, value));
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
    public Val get(final TemporalKey key) {
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

    private Function<Context, TemporalKey> getKeyExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> keySerde.read(readTxn, context.key().duplicate());
    }

    private Function<Context, Val> getValExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> NullSafe.get(valueSerde.read(readTxn, context.val().duplicate()), ValTime::val);
    }

    public TemporalState getState(final TemporalStateRequest request) {
        return env.read(readTxn ->
                keySerde.toBufferForGet(readTxn, request.key(), optionalKeyByteBuffer ->
                        optionalKeyByteBuffer.map(keyByteBuffer -> {
                            final ByteBuffer prefix = keyByteBuffer.slice(0,
                                    keyByteBuffer.remaining() - timeSerde.getSize());
                            final LmdbKeyRange keyRange =
                                    LmdbKeyRange.builder().start(keyByteBuffer).reverse().build();
                            try (final LmdbIterable iterable = LmdbIterable.create(readTxn, dbi, keyRange)) {
                                for (final LmdbEntry entry : iterable) {
                                    if (!ByteBufferUtils.containsPrefix(entry.getKey(), prefix)) {
                                        return null;
                                    }

                                    final TemporalKey key = keySerde.read(readTxn, entry.getKey());
                                    final Val val = valueSerde.read(readTxn, entry.getVal()).val();
                                    return new TemporalState(key, val);
                                }
                            }
                            return null;

                        }).orElse(null)));
    }

    public static ValuesExtractor createValuesExtractor(final FieldIndex fieldIndex,
                                                        final Function<Context, TemporalKey> keyFunction,
                                                        final Function<Context, Val> valFunction) {
        final String[] fields = fieldIndex.getFields();
        final TemporalStateConverter[] converters = new TemporalStateConverter[fields.length];
        for (int i = 0; i < fields.length; i++) {
            converters[i] = switch (fields[i]) {
                case TemporalStateFields.KEY -> kv -> ValString.create(kv.getKey().getPrefix().toString());
                case TemporalStateFields.EFFECTIVE_TIME -> kv -> ValDate.create(kv.getKey().getTime());
                case TemporalStateFields.VALUE_TYPE -> kv -> ValString.create(kv.getValue().type().toString());
                case TemporalStateFields.VALUE -> LazyKV::getValue;
                default -> kv -> ValNull.INSTANCE;
            };
        }
        return (readTxn, key, val) -> {
            final Context context = new Context(readTxn, key, val);
            final LazyKV<TemporalKey, Val> lazyKV = new LazyKV<>(context, keyFunction, valFunction);
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
            final long count = deleteOldData(writer, deleteBefore, useStateTime);

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
                               final Instant deleteBefore,
                               final boolean useStateTime) {
        return env.read(readTxn -> {
            final Count changeCount = new Count();
            iterate(readTxn, (key, val) -> {
                final TemporalKey temporalKey = keySerde.read(readTxn, key.duplicate());
                final Instant time;
                if (useStateTime) {
                    time = temporalKey.getTime();
                } else {
                    final ValTime valTime = valueSerde.read(readTxn, val.duplicate());
                    time = valTime.insertTime();
                }

                if (time.isBefore(deleteBefore)) {
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
        return env.readAndWrite((readTxn, writer) -> {
            long changeCount = 0;
            TemporalState lastState = null;
            TemporalState newState = null;
            try (final LmdbIterable iterable = LmdbIterable.create(readTxn, dbi)) {
                for (final LmdbEntry entry : iterable) {
                    final TemporalKey key = keySerde.read(readTxn, entry.getKey().duplicate());
                    final ValTime valTime = valueSerde.read(readTxn, entry.getVal().duplicate());
                    TemporalState state = new TemporalState(key, valTime.val());
                    final Instant time = key.getTime();

                    if (lastState != null &&
                        Objects.equals(lastState.key().getPrefix(), key.getPrefix()) &&
                        Objects.equals(lastState.val(), state.val()) &&
                        time.isBefore(condenseBefore)) {

                        // Remember the last state to insert it again later.
                        if (newState == null) {
                            newState = lastState;
                        }

                        // Delete the last state.
                        deleteState(writer, lastState);
                        changeCount++;

                        // We might be forced to insert if we have reached the commit limit.
                        if (writer.shouldCommit()) {
                            deleteState(writer, state);
                            changeCount++;

                            // Insert new state.
                            insert(writer, newState);
                            newState = null;
                            state = null;
                        }

                    } else if (newState != null) {
                        // Delete the last state.
                        deleteState(writer, lastState);
                        changeCount++;

                        // Insert new state.
                        insert(writer, newState);
                        newState = null;
                    }

                    lastState = state;
                }
            }

            // Insert new state.
            if (newState != null) {
                // Delete the previous state as we are extending it.
                deleteState(writer, lastState);
                changeCount++;

                // Insert the new session.
                insert(writer, newState);
            }

            return changeCount;
        });
    }

    private void deleteState(final LmdbWriter writer, final TemporalState state) {
        keySerde.write(writer.getWriteTxn(), state.key(), keyByteBuffer -> {
            dbi.delete(writer.getWriteTxn(), keyByteBuffer);
            writer.incrementChangeCount();
        });
    }

    public interface TemporalStateConverter extends Converter<TemporalKey, Val> {

    }
}
