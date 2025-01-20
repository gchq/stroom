package stroom.planb.impl.db;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.expression.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValuesConsumer;

import net.openhft.hashing.LongHashFunction;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class SessionDb extends AbstractLmdb<Session, Session> {

    public SessionDb(final Path path,
                     final ByteBufferFactory byteBufferFactory) {
        this(path, byteBufferFactory, true, false);
    }

    public SessionDb(final Path path,
                     final ByteBufferFactory byteBufferFactory,
                     final boolean overwrite,
                     final boolean readOnly) {
        super(path, byteBufferFactory, new SessionSerde(byteBufferFactory), overwrite, readOnly);
    }


    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ExpressionPredicateFactory expressionPredicateFactory,
                       final ValuesConsumer consumer) {
        final ValueFunctionFactories<Val[]> valueFunctionFactories = createValueFunctionFactories(fieldIndex);
        final Optional<Predicate<Val[]>> optionalPredicate = expressionPredicateFactory
                .create(criteria.getExpression(), valueFunctionFactories, dateTimeSettings);
        final Predicate<Val[]> predicate = optionalPredicate.orElse(vals -> true);
        final Function<KeyVal<ByteBuffer>, Val>[] valExtractors = serde.getValExtractors(fieldIndex);

        // We keep a map of sessions to cope with key hash clashes.
        final Map<String, CurrentSession> currentSessionMap = new HashMap<>();

        read(readTxn -> {
            // TODO : It would be faster if we limit the iteration to keys based on the criteria.

            Long lastKeyHash = null;

            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(readTxn)) {
                for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                    final Val[] vals = new Val[valExtractors.length];
                    for (int i = 0; i < vals.length; i++) {
                        vals[i] = valExtractors[i].apply(keyVal);
                    }
                    if (predicate.test(vals)) {
                        // We have a matching row so extend the current session if we have one.
                        final long keyHash = keyVal.key().getLong(0);
                        final long startTimeMs = keyVal.key().getLong(Long.BYTES);
                        final long endTimeMs = keyVal.key().getLong(Long.BYTES + Long.BYTES);
                        final String value = new String(ByteBufferUtils.toBytes(keyVal.val()), StandardCharsets.UTF_8);

                        // If the keyhash changes then we want to dump out all current sessions.
                        if (lastKeyHash != null && lastKeyHash != keyHash) {
                            currentSessionMap.values().forEach(currentSession -> consumer.accept(extendSession(
                                    currentSession.vals,
                                    fieldIndex,
                                    currentSession.sessionStart,
                                    currentSession.sessionEnd)));
                            currentSessionMap.clear();
                        }

                        // See if we currently have a session for the value.
                        final CurrentSession currentSession = currentSessionMap.get(value);

                        if (currentSession != null) {
                            if (currentSession.sessionEnd < startTimeMs) {

                                // We are entering a new session so deliver the current one.
                                consumer.accept(extendSession(
                                        currentSession.vals,
                                        fieldIndex,
                                        currentSession.sessionStart,
                                        currentSession.sessionEnd));

                                // Add a new session to the current session map.
                                currentSessionMap.put(value, new CurrentSession(startTimeMs, endTimeMs, vals));

                            } else {
                                // Update the session
                                currentSessionMap.put(value, new CurrentSession(
                                        currentSession.sessionStart,
                                        endTimeMs,
                                        currentSession.vals));
                            }
                        } else {
                            // Create a new session.
                            currentSessionMap.put(value, new CurrentSession(startTimeMs, endTimeMs, vals));

                            // We are using a map to deal with interleaved clashing key hashes. This still represents a
                            // risk if we have large numbers of such clashes so guard against this to prevent a
                            // potential OOME.
                            if (currentSessionMap.size() > 1000) {
                                throw new RuntimeException("Too many hash clashes detected for: " + keyHash);
                            }
                        }

                        lastKeyHash = keyHash;
                    }
                }
            }

            if (lastKeyHash != null) {
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
                               final long sessionStart,
                               final long sessionEnd) {
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

    public Optional<Session> getState(final SessionRequest request) {
        // Hash the value.
        final long nameHash = LongHashFunction.xx3().hashBytes(request.name());
        final long time = request.time();
        final ByteBuffer start = byteBufferFactory.acquire(Long.BYTES + Long.BYTES);
        final ByteBuffer end = byteBufferFactory.acquire(Long.BYTES);
        try {
            start.putLong(nameHash);
            start.putLong(time);
            start.flip();

            end.putLong(nameHash);
            end.flip();

            final KeyRange<ByteBuffer> keyRange = KeyRange.closedBackward(start, end);
            return read(readTxn -> {
                try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn, keyRange)) {
                    final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                    while (iterator.hasNext()
                           && !Thread.currentThread().isInterrupted()) {
                        final KeyVal<ByteBuffer> keyVal = iterator.next();
                        final long startTimeMs = keyVal.key().getLong(Long.BYTES);
                        final long endTimeMs = keyVal.key().getLong(Long.BYTES + Long.BYTES);
                        if (startTimeMs <= time && endTimeMs >= time) {
                            final byte[] bytes = ByteBufferUtils.toBytes(keyVal.val());
                            // We might have had a hash collision so test the key equality.
                            if (Arrays.equals(bytes, request.name())) {
                                return Optional.of(new Session(bytes, startTimeMs, endTimeMs));
                            }
                        } else if (endTimeMs < startTimeMs) {
                            final byte[] bytes = ByteBufferUtils.toBytes(keyVal.val());
                            // We might have had a hash collision so test the key equality.
                            if (Arrays.equals(bytes, request.name())) {
                                // We have found a session that ends before the requested time so return nothing.
                                return Optional.empty();
                            }
                        }
                    }
                }
                return Optional.empty();
            });
        } finally {
            byteBufferFactory.release(start);
            byteBufferFactory.release(end);
        }
    }

    // TODO: Note that LMDB does not free disk space just because you delete entries, instead it just fees pages for
    //  reuse. We might want to create a new compacted instance instead of deleting in place.
    @Override
    public void condense(final long condenseBeforeMs,
                         final long deleteBeforeMs) {
        write(writer -> {
            Session lastSession = null;
            Session newSession = null;

            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(writer.getWriteTxn())) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                while (iterator.hasNext()
                       && !Thread.currentThread().isInterrupted()) {
                    final KeyVal<ByteBuffer> keyVal = iterator.next();
                    final Session session = serde.getKey(keyVal);

                    if (session.end() <= deleteBeforeMs) {
                        // If this is data we no longer want to retain then delete it.
                        dbi.delete(writer.getWriteTxn(), keyVal.key(), keyVal.val());
                        writer.tryCommit();

                    } else {
                        if (lastSession != null &&
                            Arrays.equals(lastSession.key(), session.key()) &&
                            session.start() < condenseBeforeMs &&
                            lastSession.end() >= session.start()) {

                            // Extend the session.
                            newSession = new Session(lastSession.key(), lastSession.start(), session.end());

                            // Delete the previous session as we are extending it.
                            serde.createKeyByteBuffer(lastSession, keyByteBuffer -> {
                                dbi.delete(writer.getWriteTxn(), keyByteBuffer);
                                writer.tryCommit();
                                return null;
                            });
                        } else {
                            // Insert new session.
                            if (newSession != null) {
                                insert(writer, newSession, newSession);
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
                if (lastSession != null &&
                    Arrays.equals(lastSession.key(), newSession.key()) &&
                    newSession.start() < condenseBeforeMs &&
                    lastSession.end() >= newSession.start()) {

                    // Delete the previous session as we are extending it.
                    serde.createKeyByteBuffer(lastSession, keyByteBuffer -> {
                        dbi.delete(writer.getWriteTxn(), keyByteBuffer);
                        writer.tryCommit();
                        return null;
                    });
                }

                // Insert the new session.
                insert(writer, newSession, newSession);
            }
        });
    }

    private record CurrentSession(Long sessionStart,
                                  Long sessionEnd,
                                  Val[] vals) {

    }
}
