package stroom.planb.impl.db.session;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.KV;
import stroom.planb.impl.db.AbstractDb;
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
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.hash.HashFactory;
import stroom.planb.impl.db.hash.HashFactoryFactory;
import stroom.planb.impl.db.serde.time.DayTimeSerde;
import stroom.planb.impl.db.serde.time.HourTimeSerde;
import stroom.planb.impl.db.serde.time.MillisecondTimeSerde;
import stroom.planb.impl.db.serde.time.MinuteTimeSerde;
import stroom.planb.impl.db.serde.time.NanoTimeSerde;
import stroom.planb.impl.db.serde.time.SecondTimeSerde;
import stroom.planb.impl.db.serde.time.TimeSerde;
import stroom.planb.shared.HashLength;
import stroom.planb.shared.SessionSettings;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.StateValueType;
import stroom.planb.shared.TimePrecision;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionUtil;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.io.FileUtil;
import stroom.util.shared.NullSafe;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class SessionDb extends AbstractDb<Session, Session> {

    private static final String KEY_LOOKUP_DB_NAME = "key";
    private static final ByteBuffer VALUE = ByteBuffer.allocateDirect(0);

    private final SessionSettings settings;
    private final SessionSerde keySerde;
    private final TimeSerde timeSerde;

    private SessionDb(final PlanBEnv env,
                      final ByteBuffers byteBuffers,
                      final Boolean overwrite,
                      final SessionSettings settings,
                      final SessionSerde keySerde,
                      final TimeSerde timeSerde,
                      final HashClashCommitRunnable hashClashCommitRunnable) {
        super(env, byteBuffers, overwrite, hashClashCommitRunnable);
        this.settings = settings;
        this.keySerde = keySerde;
        this.timeSerde = timeSerde;
    }

    public static SessionDb create(final Path path,
                                   final ByteBuffers byteBuffers,
                                   final SessionSettings settings,
                                   final boolean readOnly) {
        final StateValueType stateValueType = NullSafe.getOrElse(
                settings,
                SessionSettings::getStateValueSchema,
                StateValueSchema::getStateValueType,
                StateValueType.VARIABLE);
        final HashLength valueHashLength = NullSafe.getOrElse(
                settings,
                SessionSettings::getStateValueSchema,
                StateValueSchema::getHashLength,
                HashLength.LONG);
        final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        final PlanBEnv env = new PlanBEnv(path,
                settings.getMaxStoreSize(),
                10,
                readOnly,
                hashClashCommitRunnable);
        final TimeSerde timeSerde = createTimeSerde(NullSafe.getOrElse(
                settings,
                SessionSettings::getTimePrecision,
                TimePrecision.MILLISECOND));
        final SessionSerde serde = createKeySerde(
                stateValueType,
                valueHashLength,
                env,
                byteBuffers,
                timeSerde,
                hashClashCommitRunnable);
        return new SessionDb(
                env,
                byteBuffers,
                settings.overwrite(),
                settings,
                serde,
                timeSerde,
                hashClashCommitRunnable);
    }

    private static TimeSerde createTimeSerde(final TimePrecision timePrecision) {
        return switch (timePrecision) {
            case NANOSECOND -> new NanoTimeSerde();
            case MILLISECOND -> new MillisecondTimeSerde();
            case SECOND -> new SecondTimeSerde();
            case MINUTE -> new MinuteTimeSerde();
            case HOUR -> new HourTimeSerde();
            case DAY -> new DayTimeSerde();
        };
    }

    private static SessionSerde createKeySerde(final StateValueType stateValueType,
                                               final HashLength hashLength,
                                               final PlanBEnv env,
                                               final ByteBuffers byteBuffers,
                                               final TimeSerde timeSerde,
                                               final HashClashCommitRunnable hashClashCommitRunnable) {
        return switch (stateValueType) {
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
        };
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<Session, Session> kv) {
        insert(writer, kv.key());
    }

    public void insert(final LmdbWriter writer, final Session session) {
        final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
        keySerde.write(writeTxn, session, keyByteBuffer ->
                dbi.put(writeTxn, keyByteBuffer, VALUE, putFlags));
        writer.tryCommit();
    }

    private void iterate(final Txn<ByteBuffer> txn,
                         final Consumer<KeyVal<ByteBuffer>> consumer) {
        try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(txn)) {
            for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                consumer.accept(keyVal);
            }
        }
    }

    @Override
    public void merge(final Path source) {
        env.write(writer -> {
            try (final SessionDb sourceDb = SessionDb.create(source, byteBuffers, settings, true)) {
                sourceDb.env.read(readTxn -> {
                    sourceDb.iterate(readTxn, kv -> {
                        if (keySerde.usesLookup(kv.key())) {
                            // We need to do a full read and merge.
                            final Session session = keySerde.read(readTxn, kv.key());
                            insert(writer, session);
                        } else {
                            // Quick merge.
                            if (dbi.put(writer.getWriteTxn(), kv.key(), kv.val(), putFlags)) {
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

        final ValueFunctionFactories<Val[]> valueFunctionFactories =
                PlanBSearchHelper.createValueFunctionFactories(fieldIndex);
        final Optional<Predicate<Val[]>> optionalPredicate = expressionPredicateFactory
                .createOptional(criteria.getExpression(), valueFunctionFactories, dateTimeSettings);
        final Predicate<Val[]> predicate = optionalPredicate.orElse(vals -> true);

        // We keep a map of sessions to cope with key hash clashes.
        final Map<Val, CurrentSession> currentSessionMap = new HashMap<>();

        env.read(readTxn -> {
            final ValuesExtractor valuesExtractor = createValuesExtractor(fieldIndex,
                    getKeyExtractionFunction(readTxn));


            Val lastKey = null;


            // TODO : It would be faster if we limit the iteration to keys based on the criteria.
            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(readTxn)) {
                for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                    final Val[] vals = valuesExtractor.apply(readTxn, keyVal);
                    if (predicate.test(vals)) {
                        Session session = keySerde.read(readTxn, keyVal.key());


                        // We have a matching row so extend the current session if we have one.

                        // If the key hash changes then we want to dump out all current sessions.
                        if (lastKey != null && !lastKey.equals(session.getKey())) {
                            currentSessionMap.values().forEach(currentSession -> consumer.accept(extendSession(
                                    currentSession.vals,
                                    fieldIndex,
                                    currentSession.sessionStart,
                                    currentSession.sessionEnd)));
                            currentSessionMap.clear();
                        }

                        // See if we currently have a session for the value.
                        final CurrentSession currentSession = currentSessionMap.get(session.getKey());

                        if (currentSession != null) {
                            if (currentSession.sessionEnd.isBefore(session.getStart())) {

                                // We are entering a new session so deliver the current one.
                                consumer.accept(extendSession(
                                        currentSession.vals,
                                        fieldIndex,
                                        currentSession.sessionStart,
                                        currentSession.sessionEnd));

                                // Add a new session to the current session map.
                                currentSessionMap.put(session.getKey(),
                                        new CurrentSession(session.getStart(), session.getEnd(), vals));

                            } else {
                                // Update the session
                                currentSessionMap.put(session.getKey(), new CurrentSession(
                                        currentSession.sessionStart,
                                        session.getEnd(),
                                        currentSession.vals));
                            }
                        } else {
                            // Create a new session.
                            currentSessionMap.put(session.getKey(),
                                    new CurrentSession(session.getStart(), session.getEnd(), vals));

                            // We are using a map to deal with interleaved clashing key hashes. This still represents a
                            // risk if we have large numbers of such clashes so guard against this to prevent a
                            // potential OOME.
                            if (currentSessionMap.size() > 1000) {
                                throw new RuntimeException("Too many hash clashes detected for: " + session.getKey());
                            }
                        }

                        lastKey = session.getKey();


                    }
                }
            }

            if (lastKey != null) {
                // Send the final sessions.
                currentSessionMap.values().forEach(currentSession -> consumer.accept(extendSession(
                        currentSession.vals,
                        fieldIndex,
                        currentSession.sessionStart,
                        currentSession.sessionEnd)));
                currentSessionMap.clear();
            }
            return null;
        });
    }


    public Val[] extendSession(final Val[] vals,
                               final FieldIndex fieldIndex,
                               final Instant sessionStart,
                               final Instant sessionEnd) {
        final Integer startIndex = fieldIndex.getPos(SessionFields.START);
        if (startIndex != null) {
            vals[startIndex] = ValDate.create(sessionStart);
        }
        final Integer endIndex = fieldIndex.getPos(SessionFields.END);
        if (endIndex != null) {
            vals[endIndex] = ValDate.create(sessionEnd);
        }
        return vals;
    }


//    @Override
//    public void search(final ExpressionCriteria criteria,
//                       final FieldIndex fieldIndex,
//                       final DateTimeSettings dateTimeSettings,
//                       final ExpressionPredicateFactory expressionPredicateFactory,
//                       final ValuesConsumer consumer) {
//        env.read(readTxn -> {
//            final ValuesExtractor valuesExtractor = createValuesExtractor(
//                    fieldIndex,
//                    getKeyExtractionFunction(readTxn),
//                    getValExtractionFunction(readTxn));
//            PlanBSearchHelper.search(
//                    readTxn,
//                    criteria,
//                    fieldIndex,
//                    dateTimeSettings,
//                    expressionPredicateFactory,
//                    consumer,
//                    valuesExtractor,
//                    env,
//                    dbi);
//            return null;
//        });
//    }

    private Function<Context, Session> getKeyExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> keySerde.read(readTxn, context.kv().key().duplicate());
    }

    public Session getState(final SessionRequest request) {
        return env.read(readTxn ->
                keySerde.toBufferForGet(readTxn,
                        new Session(request.key(), request.time(), request.time()),
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

                                    final KeyRange<ByteBuffer> keyRange = KeyRange.atLeastBackward(keyByteBuffer);
                                    Session result = null;
                                    try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn, keyRange)) {
                                        final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                                        while (iterator.hasNext()
                                               && !Thread.currentThread().isInterrupted()) {
                                            final KeyVal<ByteBuffer> kv = iterator.next();
                                            if (!ByteBufferUtils.containsPrefix(kv.key(), keyPrefix)) {
                                                return result;
                                            }
                                            final Session session = keySerde.read(readTxn, kv.key());
                                            if (session.getEnd().isBefore(request.time())) {
                                                return result;
                                            } else if ((session.getStart().isBefore(request.time()) ||
                                                        session.getStart().equals(request.time()))) {
                                                result = session;
                                            }
                                        }
                                    }
                                    return result;
                                }).orElse(null)));
    }

    // TODO: Note that LMDB does not free disk space just because you delete entries, instead it just frees pages for
    //  reuse. We might want to create a new compacted instance instead of deleting in place.
    @Override
    public void condense(final long condenseBeforeMs, final long deleteBeforeMs) {
        condense(Instant.ofEpochMilli(condenseBeforeMs), Instant.ofEpochMilli(deleteBeforeMs));
    }
//
//    public void condense(final Instant condenseBefore,
//                         final Instant deleteBefore) {
//        env.read(readTxn -> {
//            env.write(writer -> {
//                Key lastKey = null;
//                Val lastValue = null;
//                try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn)) {
//                    final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
//                    while (iterator.hasNext()
//                           && !Thread.currentThread().isInterrupted()) {
//                        final KeyVal<ByteBuffer> kv = iterator.next();
//                        final Key key = keySerde.read(readTxn, kv.key().duplicate());
//                        final Val value = valueSerde.read(readTxn, kv.val().duplicate());
//
//                        if (key.getEffectiveTime().isBefore(deleteBefore)) {
//                            // If this is data we no longer want to retain then delete it.
//                            dbi.delete(writer.getWriteTxn(), kv.key());
//                            writer.tryCommit();
//
//                        } else {
//                            if (lastKey != null &&
//                                Objects.equals(lastKey.getName(), key.getName()) &&
//                                lastValue.equals(value)) {
//                                if (key.getEffectiveTime().isBefore(condenseBefore)) {
//                                    // If the key and value are the same then delete the duplicate entry.
//                                    dbi.delete(writer.getWriteTxn(), kv.key());
//                                    writer.tryCommit();
//                                }
//                            }
//
//                            lastKey = key;
//                            lastValue = value;
//                        }
//                    }
//                }
//            });
//            return null;
//        });
//    }


    public void condense(final Instant condenseBefore,
                         final Instant deleteBefore) {
        env.read(readTxn -> {
            write(writer -> {
                Session lastSession = null;
                Session newSession = null;

                try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn)) {
                    final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                    while (iterator.hasNext() && !Thread.currentThread().isInterrupted()) {
                        final KeyVal<ByteBuffer> kv = iterator.next();
                        final Session session = keySerde.read(writer.getWriteTxn(), kv.key());

                        if (session.getEnd().isBefore(deleteBefore)) {
                            // If this is data we no longer want to retain then delete it.
                            dbi.delete(writer.getWriteTxn(), kv.key(), kv.val());
                            writer.tryCommit();

                        } else {
                            if (lastSession != null &&
                                lastSession.getKey().equals(session.getKey()) &&
                                session.getStart().isBefore(condenseBefore) &&
                                (lastSession.getEnd().isAfter(session.getStart()) ||
                                 lastSession.getEnd().equals(session.getStart()))) {

                                // Extend the session.
                                newSession = new Session(lastSession.getKey(),
                                        lastSession.getStart(),
                                        session.getEnd());

                                // Delete the previous session as we are extending it.
                                keySerde.write(writer.getWriteTxn(), lastSession, keyByteBuffer -> {
                                    dbi.delete(writer.getWriteTxn(), keyByteBuffer);
                                    writer.tryCommit();
                                });
                            } else {
                                // Insert new session.
                                if (newSession != null) {
                                    insert(writer, newSession);
                                    newSession = null;
                                }
                            }

                            lastSession = session;
                        }
                    }
                }

                // Insert new session.
                if (newSession != null) {
                    // Delete the last session if it will be merged into the new one.
                    if (lastSession.getKey().equals(newSession.getKey()) &&
                        newSession.getStart().isBefore(condenseBefore) &&
                        (lastSession.getEnd().isAfter(newSession.getStart()) ||
                         lastSession.getEnd().equals(newSession.getStart()))) {

                        // Delete the previous session as we are extending it.
                        keySerde.write(writer.getWriteTxn(), lastSession, keyByteBuffer -> {
                            dbi.delete(writer.getWriteTxn(), keyByteBuffer);
                            writer.tryCommit();
                        });
                    }

                    // Insert the new session.
                    insert(writer, newSession);
                }
            });
            return null;
        });
    }

    private record CurrentSession(Instant sessionStart,
                                  Instant sessionEnd,
                                  Val[] vals) {

    }

    public static ValuesExtractor createValuesExtractor(final FieldIndex fieldIndex,
                                                        final Function<Context, Session> keyFunction) {
        final String[] fields = fieldIndex.getFields();
        final SessionConverter[] converters = new SessionConverter[fields.length];
        for (int i = 0; i < fields.length; i++) {
            converters[i] = switch (fields[i]) {
                case SessionFields.KEY -> kv -> kv.getKey().getKey();
                case SessionFields.START -> kv -> ValDate.create(kv.getKey().getStart());
                case SessionFields.END -> kv -> ValDate.create(kv.getKey().getEnd());
                default -> kv -> ValNull.INSTANCE;
            };
        }
        return (readTxn, kv) -> {
            final Context context = new Context(readTxn, kv);
            final LazyKV<Session, Session> lazyKV = new LazyKV<>(context, keyFunction, keyFunction);
            final Val[] values = new Val[fields.length];
            for (int i = 0; i < fields.length; i++) {
                values[i] = converters[i].convert(lazyKV);
            }
            return values;
        };
    }

    public interface SessionConverter extends Converter<Session, Session> {

    }
}
