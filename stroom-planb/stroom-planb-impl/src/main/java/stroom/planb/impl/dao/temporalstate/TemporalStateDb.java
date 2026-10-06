/*
 * Copyright 2025 Crown Copyright
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

package stroom.planb.impl.dao.temporalstate;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb.stream.LmdbEntry;
import stroom.lmdb.stream.LmdbIterable;
import stroom.lmdb.stream.LmdbKeyRange;
import stroom.lmdb2.KV;
import stroom.planb.impl.dao.AbstractDb;
import stroom.planb.impl.dao.Count;
import stroom.planb.impl.dao.HashClashCommitRunnable;
import stroom.planb.impl.dao.LmdbWriter;
import stroom.planb.impl.dao.PlanBEnv;
import stroom.planb.impl.dao.PlanBSearchHelper;
import stroom.planb.impl.dao.PlanBSearchHelper.Context;
import stroom.planb.impl.dao.PlanBSearchHelper.Converter;
import stroom.planb.impl.dao.PlanBSearchHelper.LazyKV;
import stroom.planb.impl.dao.PlanBSearchHelper.ValuesExtractor;
import stroom.planb.impl.dao.SchemaInfo;
import stroom.planb.impl.dao.UsedLookupsRecorder;
import stroom.planb.impl.data.value.TemporalState;
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
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.TemporalPrecision;
import stroom.planb.shared.TemporalStateSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionUtil;
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
import java.util.function.Predicate;

public class TemporalStateDb extends AbstractDb<TemporalKey, Val> {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final TimeSerde timeSerde;
    private final TemporalKeySerde keySerde;
    private final ValTimeSerde valueSerde;
    private final UsedLookupsRecorder keyRecorder;
    private final UsedLookupsRecorder valueRecorder;

    private TemporalStateDb(final PlanBEnv env,
                            final ByteBuffers byteBuffers,
                            final PlanBDocument doc,
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
                                         final PlanBDocument doc,
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

    // STROOMWORKS-LOCAL: KEEP LOCAL ON MERGE FROM master. Everything from here to
    // getKeyExtractionFunction is this fork's, added for the FloorMap Event Store. origin/master has
    // no equivalent, so an incoming version of this file will not contain it and must not be allowed
    // to remove it. Nothing upstream calls it: search() above is untouched.

    /// Emits the newest entry at or before `asAt` for each key, ignoring keys whose newest
    /// entry in scope predates `notBefore`.
    ///
    /// **The caller states the read it wants.** Nothing here is inferred from the shape of the
    /// expression: [#search] decides nothing, and this method is reached only by a caller that
    /// asked for a snapshot and supplied the instant to take it at. That is the whole point of it
    /// being separate — a read mode guessed from whether a time term happens to be `<` rather
    /// than `>` is a guess that silently changes what a query means.
    ///
    /// **Precondition: the store's key encoding must be prefix-free.** Advancing past a key
    /// jumps beyond `prefix + 0xFF...`, which is greater than every key sharing that prefix —
    /// but **also greater than every longer key beginning with those bytes**. Where keys can
    /// extend one another, as they can under `VARIABLE` or `STRING`, the longer key is
    /// skipped and silently never emitted. `KeyType.TERMINATED_STRING` exists to satisfy this;
    /// see `TerminatedStringKeySerde`. This class also serves stores with other encodings
    /// through [#search], so the precondition belongs to the caller, not to the store.
    ///
    /// **Why seek rather than scan.** Entries are stored under `prefix + time`, so LMDB's
    /// ordering groups every entry for one key together in ascending time order. A key's answer is
    /// found by seeking straight to `prefix + asAt` and stepping back, rather than by reading
    /// the key's whole history. The scan costs O(rows in the store); this costs O(keys × log n),
    /// and the rows in between are never deserialised — so cost stops growing with retention.
    ///
    /// Two details worth knowing. **A predicate makes it a short backward walk, not a single
    /// seek**, because the contract is the newest row that *satisfies* the expression; with
    /// no predicate, the common case, it is one step. And **a coarse `TemporalPrecision` does
    /// not affect the seek**, because stored times are truncated by the same serde that encodes
    /// `asAt` here — though note `notBefore` is compared against those truncated stored
    /// times without being truncated itself, so at a coarse precision a key can fall on the wrong
    /// side of the floor by up to one tick. Moot at `MILLISECOND`.
    ///
    /// Unlike the inferred path this replaces, the expression is applied **whole**. Time terms
    /// are not stripped: with the mode explicit there is no framework-injected range to remove, so
    /// stripping could only discard a filter the user wrote themselves.
    ///
    /// @param asAt      the instant to take the snapshot at; required
    /// @param notBefore the floor below which a key is considered to have nothing in scope, or
    ///                  `null` for no floor. A key whose newest entry at or before `asAt`
    ///                  predates this is omitted rather than returned stale, which is what makes
    ///                  expiry a property of the read rather than a filter over its result
    public void searchSnapshot(final ExpressionCriteria criteria,
                               final FieldIndex fieldIndex,
                               final DateTimeSettings dateTimeSettings,
                               final ExpressionPredicateFactory expressionPredicateFactory,
                               final ValuesConsumer consumer,
                               final Instant asAt,
                               final Instant notBefore) {
        Objects.requireNonNull(asAt, "asAt is required for a snapshot read");

        env.read(readTxn -> {
            // Ensure we have fields for all expression criteria, and do so before the extractor
            // snapshots the field list.
            ExpressionUtil.fields(criteria.getExpression()).forEach(fieldIndex::create);

            final ValuesExtractor valuesExtractor = createValuesExtractor(
                    fieldIndex,
                    getKeyExtractionFunction(readTxn),
                    getValExtractionFunction(readTxn));
            final Predicate<Values> predicate = expressionPredicateFactory
                    .createOptional(
                            criteria.getExpression(),
                            PlanBSearchHelper.createValueFunctionFactories(fieldIndex),
                            dateTimeSettings)
                    .orElse(vals -> true);

            // Walk the distinct key prefixes, seeking each one's answer.
            ByteBuffer prefix = null;
            while (true) {
                final ByteBuffer next = nextPrefix(readTxn, prefix);
                if (next == null) {
                    break;
                }
                emitLatestAsAt(readTxn, next, asAt, notBefore, valuesExtractor, predicate, consumer);
                prefix = next;
            }

            return null;
        });
    }

    /// The key prefix of the first entry belonging to a key after `afterPrefix`, or
    /// `null` when none remains.
    ///
    /// A prefix followed by `0xFF` across the time field is the greatest key that prefix can
    /// take — the comparator is unsigned — so the first key beyond it belongs to another key. That
    /// avoids needing to know anything about the time encoding's range.
    ///
    /// @param afterPrefix the prefix just handled, or `null` to start at the first entry
    private ByteBuffer nextPrefix(final Txn<ByteBuffer> readTxn, final ByteBuffer afterPrefix) {
        if (afterPrefix == null) {
            return firstPrefix(readTxn, LmdbKeyRange.all());
        }
        // Pooled and direct: LMDB will not accept a heap buffer as a cursor bound.
        return byteBuffers.use(afterPrefix.remaining() + timeSerde.getSize(), buffer -> {
            buffer.put(afterPrefix.duplicate());
            for (int i = 0; i < timeSerde.getSize(); i++) {
                buffer.put((byte) 0xFF);
            }
            buffer.flip();
            return firstPrefix(readTxn, LmdbKeyRange.builder().start(buffer, false).build());
        });
    }

    /// The key prefix of the first entry in `keyRange`, copied to the heap so it outlives the
    /// cursor, or `null` where the range is empty.
    private ByteBuffer firstPrefix(final Txn<ByteBuffer> readTxn, final LmdbKeyRange keyRange) {
        try (final LmdbIterable iterable = LmdbIterable.create(readTxn, dbi, keyRange)) {
            for (final LmdbEntry entry : iterable) {
                final ByteBuffer key = entry.getKey();
                final int prefixLength = key.remaining() - timeSerde.getSize();
                final ByteBuffer prefix = ByteBuffer.allocate(prefixLength);
                prefix.put(key.slice(key.position(), prefixLength));
                return prefix.flip();
            }
        }
        return null;
    }

    /// The effective time of a stored key, read from its trailing time field.
    ///
    /// Keys are `prefix + time` with the time last and of fixed width, which is what makes
    /// this a slice rather than a decode.
    private Instant timeAt(final ByteBuffer key) {
        return timeSerde.read(key.slice(
                key.position() + key.remaining() - timeSerde.getSize(),
                timeSerde.getSize()));
    }

    /// Emits the newest entry for `prefix` at or before `asAt` that satisfies
    /// `predicate`, if there is one in scope.
    private void emitLatestAsAt(final Txn<ByteBuffer> readTxn,
                                final ByteBuffer prefix,
                                final Instant asAt,
                                final Instant notBefore,
                                final ValuesExtractor valuesExtractor,
                                final Predicate<Values> predicate,
                                final ValuesConsumer consumer) {
        // Pooled and direct: LMDB will not accept a heap buffer as a cursor bound.
        byteBuffers.use(prefix.remaining() + timeSerde.getSize(), seekTo -> {
            seekTo.put(prefix.duplicate());
            timeSerde.write(seekTo, asAt);
            seekTo.flip();

            // Reversed, so `start` is the upper bound: iteration begins at the greatest key at or
            // below seekTo and walks down. Leaving the prefix means this key had no entry at or
            // before asAt.
            final LmdbKeyRange keyRange = LmdbKeyRange.builder().start(seekTo).reverse().build();
            try (final LmdbIterable iterable = LmdbIterable.create(readTxn, dbi, keyRange)) {
                for (final LmdbEntry entry : iterable) {
                    if (!ByteBufferUtils.containsPrefix(entry.getKey(), prefix)) {
                        return;
                    }
                    // Below the caller's floor, and entries only get older from here, so this key
                    // has nothing in scope at all. The time is sliced off the end of the key rather
                    // than decoded through keySerde, which would allocate the key's value to read a
                    // field that is already in hand - and this runs once per key per read.
                    if (notBefore != null && timeAt(entry.getKey()).isBefore(notBefore)) {
                        return;
                    }
                    final Values values = valuesExtractor.apply(readTxn, entry.getKey(), entry.getVal());
                    if (predicate.test(values)) {
                        consumer.accept(values.toArray());
                        return;
                    }
                }
            }
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
    public long runRetention(final Instant deleteBefore, final boolean useStateTime) {
        return env.write(writer -> {
            final long count = runRetention(writer, deleteBefore, useStateTime);

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

    private long runRetention(final LmdbWriter writer,
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

                // Insert the new state.
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
