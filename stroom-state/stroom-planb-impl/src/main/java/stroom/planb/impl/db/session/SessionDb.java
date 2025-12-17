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

package stroom.planb.impl.db.session;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb.stream.LmdbEntry;
import stroom.lmdb.stream.LmdbIterable;
import stroom.lmdb.stream.LmdbKeyRange;
import stroom.lmdb2.KV;
import stroom.planb.impl.data.Session;
import stroom.planb.impl.db.AbstractDb;
import stroom.planb.impl.db.Count;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.PlanBSearchHelper;
import stroom.planb.impl.db.PlanBSearchHelper.Context;
import stroom.planb.impl.db.PlanBSearchHelper.Converter;
import stroom.planb.impl.db.PlanBSearchHelper.LazyKV;
import stroom.planb.impl.db.PlanBSearchHelper.ValuesExtractor;
import stroom.planb.impl.db.SchemaInfo;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.serde.hash.HashFactory;
import stroom.planb.impl.serde.hash.HashFactoryFactory;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.session.BooleanSessionSerde;
import stroom.planb.impl.serde.session.ByteSessionSerde;
import stroom.planb.impl.serde.session.DoubleSessionSerde;
import stroom.planb.impl.serde.session.FloatSessionSerde;
import stroom.planb.impl.serde.session.HashLookupSessionSerde;
import stroom.planb.impl.serde.session.IntegerSessionSerde;
import stroom.planb.impl.serde.session.LimitedStringSessionSerde;
import stroom.planb.impl.serde.session.LongSessionSerde;
import stroom.planb.impl.serde.session.SessionSerde;
import stroom.planb.impl.serde.session.ShortSessionSerde;
import stroom.planb.impl.serde.session.TagsKeySerde;
import stroom.planb.impl.serde.session.UidLookupSessionSerde;
import stroom.planb.impl.serde.session.VariableSessionSerde;
import stroom.planb.impl.serde.time.DayTimeSerde;
import stroom.planb.impl.serde.time.HourTimeSerde;
import stroom.planb.impl.serde.time.MillisecondTimeSerde;
import stroom.planb.impl.serde.time.MinuteTimeSerde;
import stroom.planb.impl.serde.time.NanoTimeSerde;
import stroom.planb.impl.serde.time.SecondTimeSerde;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.valtime.InsertTimeSerde;
import stroom.planb.impl.serde.valtime.InstantSerde;
import stroom.planb.shared.HashLength;
import stroom.planb.shared.KeyType;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SessionSettings;
import stroom.planb.shared.TemporalPrecision;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionUtil;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.Values;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.io.FileUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LogUtil;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class SessionDb extends AbstractDb<Session, Session> {

    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String KEY_LOOKUP_DB_NAME = "key";

    private final TimeSerde timeSerde;
    private final SessionSerde keySerde;
    private final InstantSerde valueSerde;
    private final UsedLookupsRecorder keyRecorder;
    private final UsedLookupsRecorder valueRecorder;

    private SessionDb(final PlanBEnv env,
                      final ByteBuffers byteBuffers,
                      final PlanBDoc doc,
                      final SessionSettings settings,
                      final TimeSerde timeSerde,
                      final SessionSerde keySerde,
                      final InstantSerde valueSerde,
                      final HashClashCommitRunnable hashClashCommitRunnable) {
        super(env,
                byteBuffers,
                doc,
                settings.overwrite(),
                hashClashCommitRunnable,
                new SchemaInfo(
                        CURRENT_SCHEMA_VERSION,
                        JsonUtil.writeValueAsString(settings.getKeySchema()),
                        ""));
        this.timeSerde = timeSerde;
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        this.keyRecorder = keySerde.getUsedLookupsRecorder(env);
        this.valueRecorder = valueSerde.getUsedLookupsRecorder(env);
    }

    public static SessionDb create(final Path path,
                                   final ByteBuffers byteBuffers,
                                   final PlanBDoc doc,
                                   final boolean readOnly) {
        // Ensure all settings are non null.
        final SessionSettings settings;
        if (doc.getSettings() instanceof final SessionSettings sessionSettings) {
            settings = sessionSettings;
        } else {
            settings = new SessionSettings.Builder().build();
        }

        final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        final PlanBEnv env = new PlanBEnv(path,
                settings.getMaxStoreSize(),
                20,
                readOnly,
                hashClashCommitRunnable);
        try {
            final TimeSerde timeSerde = createTimeSerde(settings.getKeySchema().getTemporalPrecision());
            final SessionSerde keySerde = createKeySerde(
                    settings.getKeySchema().getKeyType(),
                    settings.getKeySchema().getHashLength(),
                    env,
                    byteBuffers,
                    timeSerde,
                    hashClashCommitRunnable);
            final InstantSerde valueSerde = new InstantSerde(new InsertTimeSerde());
            return new SessionDb(
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

    private static SessionSerde createKeySerde(final KeyType keyType,
                                               final HashLength hashLength,
                                               final PlanBEnv env,
                                               final ByteBuffers byteBuffers,
                                               final TimeSerde timeSerde,
                                               final HashClashCommitRunnable hashClashCommitRunnable) {
        return switch (keyType) {
            case BOOLEAN -> new BooleanSessionSerde(byteBuffers, timeSerde);
            case BYTE -> new ByteSessionSerde(byteBuffers, timeSerde);
            case SHORT -> new ShortSessionSerde(byteBuffers, timeSerde);
            case INT -> new IntegerSessionSerde(byteBuffers, timeSerde);
            case LONG -> new LongSessionSerde(byteBuffers, timeSerde);
            case FLOAT -> new FloatSessionSerde(byteBuffers, timeSerde);
            case DOUBLE -> new DoubleSessionSerde(byteBuffers, timeSerde);
            case STRING ->
                    new LimitedStringSessionSerde(byteBuffers, Db.MAX_KEY_LENGTH - timeSerde.getSize(), timeSerde);
            case UID_LOOKUP -> {
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        KEY_LOOKUP_DB_NAME);
                yield new UidLookupSessionSerde(uidLookupDb, byteBuffers, timeSerde);
            }
            case HASH_LOOKUP -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(hashLength);
                final HashLookupDb hashLookupDb = new HashLookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        KEY_LOOKUP_DB_NAME);
                yield new HashLookupSessionSerde(hashLookupDb, byteBuffers, timeSerde);
            }
            case VARIABLE -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(hashLength);
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        KEY_LOOKUP_DB_NAME);
                final HashLookupDb hashLookupDb = new HashLookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        KEY_LOOKUP_DB_NAME);
                yield new VariableSessionSerde(uidLookupDb, hashLookupDb, byteBuffers, timeSerde);
            }
            case TAGS -> {
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        KEY_LOOKUP_DB_NAME);
                yield new TagsKeySerde(uidLookupDb, byteBuffers, timeSerde);
            }
        };
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<Session, Session> kv) {
        insert(writer, kv.key());
    }

    public void insert(final LmdbWriter writer, final Session session) {
        final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
        keySerde.write(writeTxn, session, keyByteBuffer ->
                valueSerde.write(writeTxn, Instant.now(), valueByteBuffer ->
                        dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, putFlags)));
        writer.tryCommit();
    }

    @Override
    public void merge(final Path source) {
        env.write(writer -> {
            try (final SessionDb sourceDb = SessionDb.create(source, byteBuffers, doc, true)) {
                // Validate that the source DB has the same schema.
                validateSchema(schemaInfo, sourceDb.getSchemaInfo());

                // Merge.
                sourceDb.env.read(readTxn -> {
                    sourceDb.iterate(readTxn, (key, val) -> {
                        if (sourceDb.keySerde.usesLookup(key)) {
                            // We need to do a full read and merge.
                            final Session session = sourceDb.keySerde.read(readTxn, key);
                            insert(writer, session);
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
    public Session get(final Session session) {
        return env.read(readTxn -> keySerde.toBufferForGet(readTxn, session, optionalKeyByteBuffer ->
                optionalKeyByteBuffer.map(keyByteBuffer -> {
                    final ByteBuffer valueByteBuffer = dbi.get(readTxn, keyByteBuffer);
                    if (valueByteBuffer == null) {
                        return null;
                    }
                    return session;
                }).orElse(null)));
    }


    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ExpressionPredicateFactory expressionPredicateFactory,
                       final ValuesConsumer consumer) {
        // Ensure we have fields for all expression criteria.
        final List<String> fields = ExpressionUtil.fields(criteria.getExpression());
        fields.forEach(fieldIndex::create);

        final Integer startIndex = fieldIndex.getPos(SessionFields.START);
        final Integer endIndex = fieldIndex.getPos(SessionFields.END);
        final ValueFunctionFactories<Values> valueFunctionFactories =
                PlanBSearchHelper.createValueFunctionFactories(fieldIndex);
        final Optional<Predicate<Values>> optionalPredicate = expressionPredicateFactory
                .createOptional(criteria.getExpression(), valueFunctionFactories, dateTimeSettings);
        final Predicate<Values> predicate = optionalPredicate.orElse(vals -> true);

        env.read(readTxn -> {
            final ValuesExtractor valuesExtractor = createValuesExtractor(fieldIndex,
                    getKeyExtractionFunction(readTxn));

            CurrentSession lastSession = null;

            // TODO : It would be faster if we limit the iteration to keys based on the criteria.
            try (final LmdbIterable iterable = LmdbIterable.create(readTxn, dbi)) {
                for (final LmdbEntry entry : iterable) {
                    final Values vals = valuesExtractor.apply(readTxn, entry.getKey(), entry.getVal());
                    if (predicate.test(vals)) {
                        final Session session = keySerde.read(readTxn, entry.getKey());

                        if (lastSession != null &&
                            lastSession.prefix.equals(session.getPrefix()) &&
                            (lastSession.sessionEnd.isAfter(session.getStart()) ||
                             lastSession.sessionEnd.equals(session.getStart()))) {

                            // Extend the session.
                            lastSession = new CurrentSession(
                                    lastSession.prefix,
                                    lastSession.sessionStart,
                                    session.getEnd(),
                                    vals);

                        } else {
                            // Insert new session.
                            if (lastSession != null) {
                                consumer.accept(extendSession(
                                        lastSession.vals.toArray(),
                                        startIndex,
                                        lastSession.sessionStart,
                                        endIndex,
                                        lastSession.sessionEnd));
                            }

                            lastSession = new CurrentSession(
                                    session.getPrefix(),
                                    session.getStart(),
                                    session.getEnd(),
                                    vals);
                        }
                    }
                }
            }

            if (lastSession != null) {
                consumer.accept(extendSession(
                        lastSession.vals.toArray(),
                        startIndex,
                        lastSession.sessionStart,
                        endIndex,
                        lastSession.sessionEnd));
            }

            return null;
        });
    }

    public Val[] extendSession(final Val[] vals,
                               final Integer startIndex,
                               final Instant sessionStart,
                               final Integer endIndex,
                               final Instant sessionEnd) {
        if (startIndex != null) {
            vals[startIndex] = ValDate.create(sessionStart);
        }
        if (endIndex != null) {
            vals[endIndex] = ValDate.create(sessionEnd);
        }
        return vals;
    }

    private Function<Context, Session> getKeyExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> keySerde.read(readTxn, context.key().duplicate());
    }

    public Session getState(final SessionRequest request) {
        return env.read(readTxn ->
                keySerde.toBufferForGet(readTxn,
                        new Session(request.prefix(), request.time(), request.time()),
                        optionalKeyByteBuffer ->
                                optionalKeyByteBuffer.map(keyByteBuffer -> {
                                    // Pad out the end time in the buffer.
                                    ByteBufferUtils.padMax(keyByteBuffer,
                                            keyByteBuffer.remaining() - timeSerde.getSize(),
                                            timeSerde.getSize());

                                    final ByteBuffer keyPrefix = keyByteBuffer.slice(0,
                                            keyByteBuffer.remaining()
                                            - timeSerde.getSize()
                                            - timeSerde.getSize());

                                    Session result = null;

                                    final LmdbKeyRange keyRange =
                                            LmdbKeyRange.builder().start(keyByteBuffer).reverse().build();
                                    try (final LmdbIterable iterable =
                                            LmdbIterable.create(readTxn, dbi, keyRange)) {
                                        final Iterator<LmdbEntry> iterator = iterable.iterator();

                                        // Move to or beyond the key.
                                        boolean isFound = iterator.hasNext();

                                        // Move previous if not in prefix range.
                                        if (isFound) {
                                            final LmdbEntry entry = iterator.next();
                                            if (!ByteBufferUtils.containsPrefix(entry.getKey(), keyPrefix)) {
                                                isFound = iterator.hasNext();
                                            }
                                        }

                                        while (isFound) {
                                            final LmdbEntry entry = iterator.next();
                                            if (!ByteBufferUtils.containsPrefix(entry.getKey(), keyPrefix)) {
                                                return result;
                                            }
                                            final Session session = keySerde.read(readTxn, entry.getKey());
                                            if (session.getEnd().isBefore(request.time())) {
                                                return result;
                                            } else if ((session.getStart().isBefore(request.time()) ||
                                                        session.getStart().equals(request.time()))) {
                                                result = session;
                                            }
                                            isFound = iterator.hasNext();
                                        }
                                    }
                                    return result;
                                }).orElse(null)));
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
                final Instant time;
                if (useStateTime) {
                    final Session session = keySerde.read(writer.getWriteTxn(), key.duplicate());
                    time = session.getEnd();
                } else {
                    time = valueSerde.read(writer.getWriteTxn(), val);
                }

                if (time.isBefore(deleteBefore)) {
                    // If this is data we no longer want to retain then delete it.
                    dbi.delete(writer.getWriteTxn(), key, val);
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
            Session lastSession = null;
            Session newSession = null;

            try (final LmdbIterable iterable = LmdbIterable.create(readTxn, dbi)) {
                for (final LmdbEntry entry : iterable) {
                    Session session = keySerde.read(writer.getWriteTxn(), entry.getKey().duplicate());

                    if (lastSession != null &&
                        lastSession.getPrefix().equals(session.getPrefix()) &&
                        session.getStart().isBefore(condenseBefore) &&
                        (lastSession.getEnd().isAfter(session.getStart()) ||
                         lastSession.getEnd().equals(session.getStart()))) {

                        // Extend the session.
                        newSession = new Session(lastSession.getPrefix(),
                                lastSession.getStart(),
                                session.getEnd());

                        // Delete the previous session as we are extending it.
                        deleteSession(writer, lastSession);
                        changeCount++;

                        // We might be forced to insert if we have reached the commit limit.
                        if (writer.shouldCommit()) {
                            deleteSession(writer, session);
                            changeCount++;

                            // Insert new session.
                            insert(writer, newSession);
                            newSession = null;
                            session = null;
                        }

                    } else if (newSession != null) {
                        // Delete the previous session as we are extending it.
                        deleteSession(writer, lastSession);
                        changeCount++;

                        // Insert new session.
                        insert(writer, newSession);
                        newSession = null;
                    }

                    lastSession = session;
                }
            }

            // Insert new session.
            if (newSession != null) {
                // Delete the previous session as we are extending it.
                deleteSession(writer, lastSession);
                changeCount++;

                // Insert the new session.
                insert(writer, newSession);
            }

            return changeCount;
        });
    }

    private void deleteSession(final LmdbWriter writer, final Session session) {
        keySerde.write(writer.getWriteTxn(), session, keyByteBuffer -> {
            dbi.delete(writer.getWriteTxn(), keyByteBuffer);
            writer.incrementChangeCount();
        });
    }

    private record CurrentSession(KeyPrefix prefix,
                                  Instant sessionStart,
                                  Instant sessionEnd,
                                  Values vals) {

    }

    public static ValuesExtractor createValuesExtractor(final FieldIndex fieldIndex,
                                                        final Function<Context, Session> keyFunction) {
        final String[] fields = fieldIndex.getFields();
        final SessionConverter[] converters = new SessionConverter[fields.length];
        for (int i = 0; i < fields.length; i++) {
            converters[i] = switch (fields[i]) {
                case SessionFields.KEY -> kv -> kv.getKey().getPrefix().getVal();
                case SessionFields.START -> kv -> ValDate.create(kv.getKey().getStart());
                case SessionFields.END -> kv -> ValDate.create(kv.getKey().getEnd());
                default -> kv -> ValNull.INSTANCE;
            };
        }
        return (readTxn, key, val) -> {
            final Context context = new Context(readTxn, key, val);
            final LazyKV<Session, Session> lazyKV = new LazyKV<>(context, keyFunction, keyFunction);
            final Val[] values = new Val[fields.length];
            for (int i = 0; i < fields.length; i++) {
                values[i] = converters[i].convert(lazyKV);
            }
            return Values.of(values);
        };
    }

    public interface SessionConverter extends Converter<Session, Session> {

    }
}
