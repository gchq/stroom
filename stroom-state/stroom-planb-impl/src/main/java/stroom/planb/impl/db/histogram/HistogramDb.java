package stroom.planb.impl.db.histogram;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.lmdb2.KV;
import stroom.planb.impl.db.AbstractDb;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.db.serde.time.TimeSerde;
import stroom.planb.impl.db.serde.time.ZonedDayTimeSerde;
import stroom.planb.impl.db.serde.time.ZonedHourTimeSerde;
import stroom.planb.impl.db.serde.valtime.InsertTimeSerde;
import stroom.planb.shared.HistogramKeySchema;
import stroom.planb.shared.HistogramKeyType;
import stroom.planb.shared.HistogramSettings;
import stroom.planb.shared.HistogramTemporalResolution;
import stroom.planb.shared.HistogramValueMax;
import stroom.planb.shared.HistogramValueSchema;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Format;
import stroom.query.api.UserTimeZone;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ValArrayFunctionFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDuration;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.io.FileUtil;
import stroom.util.shared.NullSafe;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class HistogramDb extends AbstractDb<HistogramKey, Long> {

    private static final String KEY_LOOKUP_DB_NAME = "key";

    private final HistogramSettings settings;
    private final HistogramKeySerde keySerde;
    private final UsedLookupsRecorder keyRecorder;
    private final HistogramSecondValuesSerde valuesSerde;

    private HistogramDb(final PlanBEnv env,
                        final ByteBuffers byteBuffers,
                        final Boolean overwrite,
                        final HistogramSettings settings,
                        final HistogramKeySerde keySerde,
                        final HistogramSecondValuesSerde valuesSerde,
                        final HashClashCommitRunnable hashClashCommitRunnable) {
        super(env, byteBuffers, overwrite, hashClashCommitRunnable);
        this.settings = settings;
        this.keySerde = keySerde;
        this.valuesSerde = valuesSerde;
        this.keyRecorder = keySerde.getUsedLookupsRecorder(env);
    }

    public static HistogramDb create(final Path path,
                                     final ByteBuffers byteBuffers,
                                     final HistogramSettings settings,
                                     final boolean readOnly) {
        final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        final PlanBEnv env = new PlanBEnv(path,
                settings.getMaxStoreSize(),
                20,
                readOnly,
                hashClashCommitRunnable);
        final HistogramKeyType keyType = NullSafe.getOrElse(
                settings,
                HistogramSettings::getKeySchema,
                HistogramKeySchema::getKeyType,
                HistogramKeyType.TAGS);
        final HistogramTemporalResolution temporalResolution = NullSafe.getOrElse(
                settings,
                HistogramSettings::getKeySchema,
                HistogramKeySchema::getTemporalResolution,
                HistogramTemporalResolution.SECOND);
        final UserTimeZone timeZone = NullSafe.getOrElse(
                settings,
                HistogramSettings::getKeySchema,
                HistogramKeySchema::getTimeZone,
                UserTimeZone.utc());

        final HistogramValueMax valueType = NullSafe.getOrElse(
                settings,
                HistogramSettings::getValueSchema,
                HistogramValueSchema::getHistogramValueType,
                HistogramValueMax.TWO);
        // Rows will store hour precision.
        final ZoneId zoneId = getZoneId(timeZone);

        // The key time is always a coarse grained time with rows having multiple values.
        final TimeSerde timeSerde;
        if (temporalResolution.equals(HistogramTemporalResolution.MONTH) ||
            temporalResolution.equals(HistogramTemporalResolution.YEAR)) {
            timeSerde = new ZonedDayTimeSerde(zoneId);
        } else {
            timeSerde = new ZonedHourTimeSerde(zoneId);
        }

        final HistogramKeySerde keySerde = createKeySerde(
                keyType,
                env,
                byteBuffers,
                timeSerde);
        final UnsignedBytes countSerde = getCountSerde(valueType);
        final HistogramSecondValuesSerde valueSerde = new HistogramSecondValuesSerde(
                byteBuffers,
                countSerde,
                new InsertTimeSerde(),
                zoneId);
        return new HistogramDb(
                env,
                byteBuffers,
                settings.overwrite(),
                settings,
                keySerde,
                valueSerde,
                hashClashCommitRunnable);
    }

    private static ZoneId getZoneId(final UserTimeZone userTimeZone) {
        ZoneId zone = ZoneOffset.UTC;
        if (userTimeZone != null) {
            if (UserTimeZone.Use.UTC.equals(userTimeZone.getUse())) {
                zone = ZoneOffset.UTC;
            } else if (UserTimeZone.Use.LOCAL.equals(userTimeZone.getUse())) {
                zone = ZoneId.systemDefault();
            } else if (UserTimeZone.Use.ID.equals(userTimeZone.getUse())) {
                zone = ZoneId.of(userTimeZone.getId());
            } else if (UserTimeZone.Use.OFFSET.equals(userTimeZone.getUse())) {
                zone = ZoneOffset.ofHoursMinutes(
                        NullSafe.getInt(userTimeZone.getOffsetHours()),
                        NullSafe.getInt(userTimeZone.getOffsetMinutes()));
            }
        }
        return zone;
    }

    private static HistogramKeySerde createKeySerde(final HistogramKeyType keyType,
                                                    final PlanBEnv env,
                                                    final ByteBuffers byteBuffers,
                                                    final TimeSerde timeSerde) {
        return switch (keyType) {
            case TAGS -> {
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        KEY_LOOKUP_DB_NAME);
                yield new HistogramTagsKeySerde(uidLookupDb, byteBuffers, timeSerde);
            }
        };
    }

    private static UnsignedBytes getCountSerde(final HistogramValueMax valueType) {
        return switch (valueType) {
            case ONE -> UnsignedBytesInstances.ONE;
            case TWO -> UnsignedBytesInstances.TWO;
            case THREE -> UnsignedBytesInstances.THREE;
            case FOUR -> UnsignedBytesInstances.FOUR;
            case FIVE -> UnsignedBytesInstances.FIVE;
            case SIX -> UnsignedBytesInstances.SIX;
            case SEVEN -> UnsignedBytesInstances.SEVEN;
            case EIGHT -> UnsignedBytesInstances.EIGHT;
        };
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<HistogramKey, Long> kv) {
        final Instant time = kv.key().getTime();
        keySerde.write(writer.getWriteTxn(), kv.key(), keyByteBuffer -> {
            final ByteBuffer existingValueByteBuffer = dbi.get(writer.getWriteTxn(), keyByteBuffer);
            if (existingValueByteBuffer == null) {
                valuesSerde.newSingleValue(time, kv.val(), valueByteBuffer ->
                        dbi.put(writer.getWriteTxn(), keyByteBuffer, valueByteBuffer));
            } else {
                valuesSerde.addSingleValue(existingValueByteBuffer, time, kv.val(), valueByteBuffer ->
                        dbi.put(writer.getWriteTxn(), keyByteBuffer, valueByteBuffer));
            }
        });
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
            try (final HistogramDb sourceDb = HistogramDb.create(source, byteBuffers, settings, true)) {
                sourceDb.env.read(readTxn -> {
                    sourceDb.iterate(readTxn, kv -> {
                        final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
                        final HistogramKey key = sourceDb.keySerde.read(readTxn, kv.key());
                        keySerde.write(writeTxn, key, keyByteBuffer -> {
                            final ByteBuffer existingValueByteBuffer = dbi.get(writeTxn, keyByteBuffer);
                            if (existingValueByteBuffer == null) {
                                dbi.put(writeTxn, keyByteBuffer, kv.val());
                            } else {
                                valuesSerde.merge(kv.val(), existingValueByteBuffer, valueByteBuffer ->
                                        dbi.put(writeTxn, keyByteBuffer, valueByteBuffer));
                            }
                        });
                    });
                    return null;
                });
            }
        });

        // Delete source now we have merged.
        FileUtil.deleteDir(source);
    }

    @Override
    public Long get(final HistogramKey key) {
        return env.read(readTxn -> keySerde.toBufferForGet(readTxn, key, optionalKeyByteBuffer ->
                optionalKeyByteBuffer.map(keyByteBuffer -> {
                    final ByteBuffer valueByteBuffer = dbi.get(readTxn, keyByteBuffer);
                    if (valueByteBuffer == null) {
                        return null;
                    }
                    return valuesSerde.getVal(key.getTime(), valueByteBuffer);
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
        env.read(readTxn -> {

            final ValueFunctionFactories<Val[]> valueFunctionFactories = createValueFunctionFactories(fieldIndex);
            final Optional<Predicate<Val[]>> optionalPredicate = expressionPredicateFactory
                    .createOptional(criteria.getExpression(), valueFunctionFactories, dateTimeSettings);
            final Predicate<Val[]> predicate = optionalPredicate.orElse(vals -> true);
            final HistogramConverter[] histogramConverters = createValuesExtractor(fieldIndex);

            // TODO : It would be faster if we limit the iteration to keys based on the criteria.
            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(readTxn)) {
                for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                    final HistogramKey key = keySerde.read(readTxn, keyVal.key());
                    valuesSerde.getValues(key, keyVal.val(), histogramConverters, vals -> {
                        if (predicate.test(vals)) {
                            consumer.accept(vals);
                        }
                    });
                }
            }

            return null;
        });
    }

    public static ValueFunctionFactories<Val[]> createValueFunctionFactories(final FieldIndex fieldIndex) {
        return fieldName -> {
            final Integer index = fieldIndex.getPos(fieldName);
            if (index == null) {
                throw new RuntimeException("Unexpected field: " + fieldName);
            }
            return new ValArrayFunctionFactory(Column.builder().format(Format.TEXT).build(), index);
        };
    }

    public static HistogramConverter[] createValuesExtractor(final FieldIndex fieldIndex) {
        final String[] fields = fieldIndex.getFields();
        final HistogramConverter[] converters = new HistogramConverter[fields.length];
        for (int i = 0; i < fields.length; i++) {
            converters[i] = switch (fields[i]) {
                case HistogramFields.KEY -> kv -> ValString.create(kv.key().getTags().toString());
                case HistogramFields.TIME -> kv -> ValDate.create(kv.key().getTime());
                case HistogramFields.DURATION -> kv -> ValDuration.create(Duration.ofSeconds(1));
                case HistogramFields.VALUE -> kv -> ValLong.create(kv.val());
                default -> kv -> ValNull.INSTANCE;
            };
        }
        return converters;
    }

    @Override
    public long deleteOldData(final Instant deleteBefore, final boolean useStateTime) {
        return env.readAndWrite((readTxn, writer) -> {
            long changeCount = 0;
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn)) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                while (iterator.hasNext()
                       && !Thread.currentThread().isInterrupted()) {
                    final KeyVal<ByteBuffer> kv = iterator.next();
                    final HistogramKey key = keySerde.read(readTxn, kv.key().duplicate());
                    final Instant time;
                    if (useStateTime) {
                        time = key.getTime();
                    } else {
                        time = valuesSerde.readInsertTime(kv.val());
                    }

                    if (time.isBefore(deleteBefore)) {
                        // If this is data we no longer want to retain then delete it.
                        dbi.delete(writer.getWriteTxn(), kv.key());
                        writer.tryCommit();
                        changeCount++;
                    } else {
                        // Record used lookup keys.
                        keyRecorder.recordUsed(writer, kv.key());
                    }
                }
            }

            // Delete unused lookup keys.
            keyRecorder.deleteUnused(readTxn, writer);

            return changeCount;
        });
    }

    @Override
    public long condense(final Instant condenseBefore) {
        return 0;
    }

    public interface HistogramConverter {

        Val convert(HistogramValue histogramDelta);
    }

    public interface HistogramValuesSerde {

    }

    public static class HistogramSecondValuesSerde implements HistogramValuesSerde {

        private static final int SECONDS_IN_HOUR = 60 * 60;
        private final ByteBuffers byteBuffers;
        private final UnsignedBytes countSerde;
        private final InsertTimeSerde insertTimeSerde;
        private final ZoneId zoneId;
        private final int bufferLength;

        public HistogramSecondValuesSerde(final ByteBuffers byteBuffers,
                                          final UnsignedBytes countSerde,
                                          final InsertTimeSerde insertTimeSerde,
                                          final ZoneId zoneId) {
            this.byteBuffers = byteBuffers;
            this.countSerde = countSerde;
            this.insertTimeSerde = insertTimeSerde;
            this.zoneId = zoneId;
            bufferLength = (SECONDS_IN_HOUR * countSerde.length()) + insertTimeSerde.getSize();
        }

        private void newByteBuffer(final Consumer<ByteBuffer> consumer) {
            byteBuffers.use(bufferLength, byteBuffer -> {
                consumer.accept(byteBuffer);
                return null;
            });
        }

        private void zeroByteBuffer(final ByteBuffer byteBuffer) {
            for (int seconds = 0; seconds < SECONDS_IN_HOUR; seconds++) {
                countSerde.put(byteBuffer, 0);
            }
            insertTimeSerde.write(byteBuffer, Instant.now());
            byteBuffer.flip();
        }

        public void newSingleValue(final Instant instant, final long value, final Consumer<ByteBuffer> consumer) {
            newByteBuffer(valueByteBuffer -> {
                zeroByteBuffer(valueByteBuffer);
                writeValue(valueByteBuffer, instant, value, consumer);
            });
        }

        public void addSingleValue(final ByteBuffer byteBuffer,
                                   final Instant instant,
                                   final long value,
                                   final Consumer<ByteBuffer> consumer) {
            newByteBuffer(valueByteBuffer -> {
                valueByteBuffer.put(byteBuffer);
                valueByteBuffer.flip();
                writeValue(valueByteBuffer, instant, value, consumer);
            });
        }

        private void writeValue(final ByteBuffer valueByteBuffer,
                                final Instant instant,
                                final long value,
                                final Consumer<ByteBuffer> consumer) {
            final int seconds = getSecondOfHour(instant);
            final int position = seconds * countSerde.length();
            final long currentValue = countSerde.get(valueByteBuffer, position);
            final long newValue = currentValue + value;
            valueByteBuffer.position(position);
            countSerde.put(valueByteBuffer, Math.min(countSerde.maxValue(), newValue));
            writeInsertTime(valueByteBuffer);
            valueByteBuffer.position(0);
            consumer.accept(valueByteBuffer);
        }

        public void merge(final ByteBuffer source,
                          final ByteBuffer destination,
                          final Consumer<ByteBuffer> consumer) {
            newByteBuffer(valueByteBuffer -> {
                for (int seconds = 0; seconds < SECONDS_IN_HOUR; seconds++) {
                    final long sourceValue = countSerde.get(source);
                    final long destinationValue = countSerde.get(destination);
                    final long total = sourceValue + destinationValue;
                    countSerde.put(valueByteBuffer, Math.min(countSerde.maxValue(), total));
                }
                writeInsertTime(valueByteBuffer);
                valueByteBuffer.flip();
                consumer.accept(valueByteBuffer);
            });
        }

        public void writeInsertTime(final ByteBuffer byteBuffer) {
            byteBuffer.position(byteBuffer.limit() - insertTimeSerde.getSize());
            insertTimeSerde.write(byteBuffer, Instant.now());
        }

        public Instant readInsertTime(final ByteBuffer byteBuffer) {
            byteBuffer.position(byteBuffer.limit() - insertTimeSerde.getSize());
            return insertTimeSerde.read(byteBuffer);
        }

        public Long getVal(final Instant instant, final ByteBuffer byteBuffer) {
            final int secondOfHour = getSecondOfHour(instant);
            final int position = secondOfHour * countSerde.length();
            byteBuffer.position(position);
            return countSerde.get(byteBuffer);
        }

        public void getValues(final HistogramKey key,
                              final ByteBuffer byteBuffer,
                              final HistogramConverter[] histogramConverters,
                              final Consumer<Val[]> consumer) {
            final ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(key.getTime(), zoneId);
            for (int seconds = 0; seconds < SECONDS_IN_HOUR; seconds++) {
                final long value = countSerde.get(byteBuffer);
                final Instant time = zonedDateTime.plusSeconds(seconds).toInstant();
                final HistogramKey histogramKey = new HistogramKey(key.getTags(), time);
                final HistogramValue histogramDelta = new HistogramValue(histogramKey, value);
                final Val[] vals = new Val[histogramConverters.length];
                for (int i = 0; i < histogramConverters.length; i++) {
                    vals[i] = histogramConverters[i].convert(histogramDelta);
                }
                consumer.accept(vals);
            }
        }

        private int getSecondOfHour(final Instant instant) {
            final ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, zoneId);
            return (zonedDateTime.getMinute() * 60) + zonedDateTime.getSecond();
        }
    }
}
