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

package stroom.planb.impl.db.metric;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.lmdb2.KV;
import stroom.planb.impl.db.AbstractDb;
import stroom.planb.impl.db.Count;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.SchemaInfo;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.serde.count.CountSerde;
import stroom.planb.impl.serde.count.CountValuesSerde;
import stroom.planb.impl.serde.count.CountValuesSerdeImpl;
import stroom.planb.impl.serde.count.DayOfYearTemporalIndex;
import stroom.planb.impl.serde.count.HourOfDayTemporalIndex;
import stroom.planb.impl.serde.count.MinuteOfHourTemporalIndex;
import stroom.planb.impl.serde.count.MonthOfYearTemporalIndex;
import stroom.planb.impl.serde.count.SecondOfHourTemporalIndex;
import stroom.planb.impl.serde.count.TemporalIndex;
import stroom.planb.impl.serde.count.ValConverter;
import stroom.planb.impl.serde.count.YearTemporalIndex;
import stroom.planb.impl.serde.temporalkey.TemporalKey;
import stroom.planb.impl.serde.temporalkey.TemporalKeySerde;
import stroom.planb.impl.serde.temporalkey.TemporalKeySerdeFactory;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.time.ZonedDayTimeSerde;
import stroom.planb.impl.serde.time.ZonedHourTimeSerde;
import stroom.planb.impl.serde.time.ZonedYearTimeSerde;
import stroom.planb.impl.serde.valtime.InsertTimeSerde;
import stroom.planb.shared.MaxValueSize;
import stroom.planb.shared.MetricSettings;
import stroom.planb.shared.MetricValueSchema;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.TemporalResolution;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Format;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ValuesFunctionFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.UserTimeZoneUtil;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.Values;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.io.FileUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LogUtil;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class MetricDb extends AbstractDb<TemporalKey, Long> {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final TemporalResolution temporalResolution;
    private final TemporalKeySerde keySerde;
    private final UsedLookupsRecorder keyRecorder;
    private final CountValuesSerde<Metric> valuesSerde;

    private MetricDb(final PlanBEnv env,
                     final ByteBuffers byteBuffers,
                     final PlanBDoc doc,
                     final MetricSettings settings,
                     final TemporalResolution temporalResolution,
                     final TemporalKeySerde keySerde,
                     final CountValuesSerde<Metric> valuesSerde,
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
        this.temporalResolution = temporalResolution;
        this.keySerde = keySerde;
        this.valuesSerde = valuesSerde;
        this.keyRecorder = keySerde.getUsedLookupsRecorder(env);
    }

    public static MetricDb create(final Path path,
                                  final ByteBuffers byteBuffers,
                                  final PlanBDoc doc,
                                  final boolean readOnly) {
        // Ensure all settings are non null.
        final MetricSettings settings;
        if (doc.getSettings() instanceof final MetricSettings metricSettings) {
            settings = metricSettings;
        } else {
            settings = new MetricSettings.Builder().build();
        }

        final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        final PlanBEnv env = new PlanBEnv(path,
                settings.getMaxStoreSize(),
                20,
                readOnly,
                hashClashCommitRunnable);
        try {
            // Rows will store hour precision.
            final ZoneId zoneId = UserTimeZoneUtil.getZoneId(settings.getKeySchema().getTimeZone(), null);

            // The key time is always a coarse grained time with rows having multiple values.
            final TimeSerde keyTimeSerde = getKeyTimeSerde(settings.getKeySchema().getTemporalResolution(), zoneId);
            final InsertTimeSerde insertTimeSerde = new InsertTimeSerde();
            final CountSerde<Metric> countSerde = getCountSerde(settings.getValueSchema().getValueType(),
                    settings.getValueSchema());
            final TemporalIndex temporalIndex = getTemporalIndex(settings.getKeySchema().getTemporalResolution());
            final CountValuesSerde<Metric> valueSerde = new CountValuesSerdeImpl<>(
                    byteBuffers,
                    countSerde,
                    insertTimeSerde,
                    zoneId,
                    temporalIndex);

            final TemporalKeySerde keySerde = TemporalKeySerdeFactory.createKeySerde(
                    doc,
                    settings.getKeySchema().getKeyType(),
                    settings.getKeySchema().getHashLength(),
                    env,
                    byteBuffers,
                    keyTimeSerde,
                    hashClashCommitRunnable);

            return new MetricDb(
                    env,
                    byteBuffers,
                    doc,
                    settings,
                    settings.getKeySchema().getTemporalResolution(),
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

    private static TimeSerde getKeyTimeSerde(final TemporalResolution temporalResolution,
                                             final ZoneId zoneId) {
        return switch (temporalResolution) {
            case SECOND, MINUTE -> new ZonedHourTimeSerde(zoneId);
            case HOUR -> new ZonedDayTimeSerde(zoneId);
            case DAY, MONTH, YEAR -> new ZonedYearTimeSerde(zoneId);
        };
    }

    private static TemporalIndex getTemporalIndex(final TemporalResolution temporalResolution) {
        return switch (temporalResolution) {
            case SECOND -> new SecondOfHourTemporalIndex();
            case MINUTE -> new MinuteOfHourTemporalIndex();
            case HOUR -> new HourOfDayTemporalIndex();
            case DAY -> new DayOfYearTemporalIndex();
            case MONTH -> new MonthOfYearTemporalIndex();
            case YEAR -> new YearTemporalIndex();
        };
    }

    private static CountSerde<Metric> getCountSerde(final MaxValueSize valueType, final MetricValueSchema valueSchema) {
        final UnsignedBytes unsignedBytes = switch (valueType) {
            case ONE -> UnsignedBytesInstances.ONE;
            case TWO -> UnsignedBytesInstances.TWO;
            case THREE -> UnsignedBytesInstances.THREE;
            case FOUR -> UnsignedBytesInstances.FOUR;
            case FIVE -> UnsignedBytesInstances.FIVE;
            case SIX -> UnsignedBytesInstances.SIX;
            case SEVEN -> UnsignedBytesInstances.SEVEN;
            case EIGHT -> UnsignedBytesInstances.EIGHT;
        };
        return new MetricCountSerde(
                unsignedBytes,
                valueSchema.getStoreLatestValue(),
                valueSchema.getStoreMin(),
                valueSchema.getStoreMax(),
                valueSchema.getStoreCount(),
                valueSchema.getStoreSum());
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<TemporalKey, Long> kv) {
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

    @Override
    public void merge(final Path source) {
        env.write(writer -> {
            try (final MetricDb sourceDb = MetricDb.create(source, byteBuffers, doc, true)) {
                // Validate that the source DB has the same schema.
                validateSchema(schemaInfo, sourceDb.getSchemaInfo());

                // Merge.
                sourceDb.env.read(readTxn -> {
                    sourceDb.iterate(readTxn, (key, val) -> {
                        final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
                        final TemporalKey temporalKey = sourceDb.keySerde.read(readTxn, key);
                        keySerde.write(writeTxn, temporalKey, keyByteBuffer -> {
                            final ByteBuffer existingValueByteBuffer = dbi.get(writeTxn, keyByteBuffer);
                            if (existingValueByteBuffer == null) {
                                dbi.put(writeTxn, keyByteBuffer, val);
                            } else {
                                valuesSerde.merge(val, existingValueByteBuffer, valueByteBuffer ->
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
    public Long get(final TemporalKey key) {
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

            final ValueFunctionFactories<Values> valueFunctionFactories = createValueFunctionFactories(fieldIndex);
            final Optional<Predicate<Values>> optionalPredicate = expressionPredicateFactory
                    .createOptional(criteria.getExpression(), valueFunctionFactories, dateTimeSettings);
            final Predicate<Values> predicate = optionalPredicate.orElse(vals -> true);
            final List<ValConverter<Metric>> converters = createValuesExtractor(fieldIndex);

            // TODO : It would be faster if we limit the iteration to keys based on the criteria.
            iterate(readTxn, (key, val) -> {
                final TemporalKey temporalKey = keySerde.read(readTxn, key);
                valuesSerde.getValues(temporalKey, val, converters, vals -> {
                    if (predicate.test(vals)) {
                        consumer.accept(vals.toArray());
                    }
                });
            });

            return null;
        });
    }

    public static ValueFunctionFactories<Values> createValueFunctionFactories(final FieldIndex fieldIndex) {
        return fieldName -> {
            final Integer index = fieldIndex.getPos(fieldName);
            if (index == null) {
                throw new RuntimeException("Unexpected field: " + fieldName);
            }
            return new ValuesFunctionFactory(Column.builder().format(Format.TEXT).build(), index);
        };
    }

    public List<ValConverter<Metric>> createValuesExtractor(final FieldIndex fieldIndex) {
        final String[] fields = fieldIndex.getFields();
        final List<ValConverter<Metric>> converters = new ArrayList<>();
        for (final String field : fields) {
            final ValConverter<Metric> valConverter = switch (field) {
                case MetricFields.KEY -> (k, v) -> ValString.create(k.getPrefix().toString());
                case MetricFields.TIME -> (k, v) -> ValDate.create(k.getTime());
                case MetricFields.RESOLUTION -> (k, v) -> ValString.create(temporalResolution.getDisplayValue());
                case MetricFields.VALUE -> (k, v) -> ValLong.create(v.value());
                case MetricFields.MIN -> (k, v) -> ValLong.create(v.min());
                case MetricFields.MAX -> (k, v) -> ValLong.create(v.max());
                case MetricFields.COUNT -> (k, v) -> ValLong.create(v.count());
                case MetricFields.SUM -> (k, v) -> ValLong.create(v.sum());
                case MetricFields.AVERAGE -> (k, v) -> {
                    if (v.count() > 0) {
                        return ValLong.create(v.sum() / v.count());
                    } else {
                        return ValLong.create(0);
                    }
                };
                default -> (k, v) -> ValNull.INSTANCE;
            };
            converters.add(valConverter);
        }
        return converters;
    }

    @Override
    public long deleteOldData(final Instant deleteBefore, final boolean useStateTime) {
        return env.write(writer -> {
            final long count = deleteOldData(writer, deleteBefore, useStateTime);

            // Delete unused lookup keys.
            if (!Thread.currentThread().isInterrupted()) {
                env.read(readTxn -> {
                    keyRecorder.deleteUnused(readTxn, writer);
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
                    time = valuesSerde.readInsertTime(val);
                }

                if (time.isBefore(deleteBefore)) {
                    // If this is data we no longer want to retain then delete it.
                    dbi.delete(writer.getWriteTxn(), key);
                    changeCount.increment();
                } else {
                    // Record used lookup keys.
                    keyRecorder.recordUsed(writer, key);
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
}
