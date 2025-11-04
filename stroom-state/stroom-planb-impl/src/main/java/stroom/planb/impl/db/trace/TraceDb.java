package stroom.planb.impl.db.trace;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb.stream.LmdbEntry;
import stroom.lmdb.stream.LmdbIterable;
import stroom.lmdb.stream.LmdbKeyRange;
import stroom.lmdb.stream.LmdbStream;
import stroom.lmdb2.KV;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracesResultPage;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.data.SpanKV;
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
import stroom.planb.impl.serde.KeySerde;
import stroom.planb.impl.serde.Serde;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.impl.serde.trace.LookupSerdeImpl;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanKeySerde;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.impl.serde.trace.SpanValueSerde;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.Values;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.io.FileUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageResponse;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TraceDb extends AbstractDb<SpanKey, SpanValue> {

    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final ByteBuffer VALUE = ByteBuffer.allocateDirect(0);

    private final ByteBufferFactory byteBufferFactory;
    private final KeySerde<SpanKey> keySerde;
    private final Serde<SpanValue> valueSerde;
    private final UsedLookupsRecorder keyRecorder;
    private final UsedLookupsRecorder valueRecorder;
    private final Dbi<ByteBuffer> traceRootsDbi;
    //    private final Dbi<ByteBuffer> traceUpdateTimeDbi;
//    private final Dbi<ByteBuffer> updateTimeDbi;
    private final TraceRootKeySerde traceRootKeySerde;
    private final TraceRootValueSerde traceRootValueSerde;
//    private final TimeSerde updateTimeSerde;

    private TraceDb(final PlanBEnv env,
                    final ByteBuffers byteBuffers,
                    final ByteBufferFactory byteBufferFactory,
                    final PlanBDoc doc,
                    final StateSettings settings,
                    final KeySerde<SpanKey> keySerde,
                    final Serde<SpanValue> valueSerde,
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
        this.byteBufferFactory = byteBufferFactory;
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        this.keyRecorder = keySerde.getUsedLookupsRecorder(env);
        this.valueRecorder = valueSerde.getUsedLookupsRecorder(env);

        traceRootKeySerde = new TraceRootKeySerde(byteBuffers);
        traceRootValueSerde = new TraceRootValueSerde(byteBufferFactory);
        traceRootsDbi = env.openDbi("trace-roots", DbiFlags.MDB_CREATE);

//        traceUpdateTimeDbi = env.openDbi("trace-update-time", DbiFlags.MDB_CREATE);
//        updateTimeDbi = env.openDbi("update-time", DbiFlags.MDB_CREATE);
//
//        updateTimeSerde = new MillisecondTimeSerde();
    }

    public static TraceDb create(final Path path,
                                 final ByteBuffers byteBuffers,
                                 final ByteBufferFactory byteBufferFactory,
                                 final PlanBDoc doc,
                                 final boolean readOnly) {
        final StateSettings settings;
        if (doc.getSettings() instanceof final StateSettings stateSettings) {
            settings = stateSettings;
        } else {
            settings = new StateSettings.Builder().build();
        }

        final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        final PlanBEnv env = new PlanBEnv(path,
                settings.getMaxStoreSize(),
                20,
                readOnly,
                hashClashCommitRunnable);
        try {
            final KeySerde<SpanKey> keySerde = new SpanKeySerde(byteBuffers);
            final LookupSerdeImpl lookupSerde = new LookupSerdeImpl(env, hashClashCommitRunnable, byteBuffers);
            final Serde<SpanValue> valueSerde = new SpanValueSerde(byteBufferFactory, lookupSerde);
            return new TraceDb(
                    env,
                    byteBuffers,
                    byteBufferFactory,
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

    public void insert(final LmdbWriter writer, final Span span) {
        final SpanKey spanKey = SpanKey.create(span);
        final SpanValue spanValue = SpanValue.create(span);
        insert(writer, new SpanKV(spanKey, spanValue));
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<SpanKey, SpanValue> kv) {
        final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
        keySerde.write(writeTxn, kv.key(), keyByteBuffer ->
                valueSerde.write(writeTxn, kv.val(), valueByteBuffer ->
                        dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, putFlags)));

        // Add trace root if this is one.
        final byte[] traceIdBytes = HexStringUtil.decode(kv.key().getTraceId());
        if (NullSafe.isEmptyString(kv.key().getParentSpanId())) {
            try {
                // TODO : We are currently assuming that we get the root last but we might want to reevaluate depth etc
                //  later.
                final Trace trace = getTrace(writeTxn, kv.key().getTraceId());
                final TraceRootKey key = new TraceRootKey(traceIdBytes);
                final TraceRoot value = new TraceRoot(trace);

                traceRootKeySerde.write(key, keyBuffer ->
                        traceRootValueSerde.write(value, valueBuffer ->
                                traceRootsDbi.put(writeTxn, keyBuffer, valueBuffer)));

//            // Write update time for processing new traces.
//            updateInsertOrder(writeTxn, traceIdBytes);
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
            }
        }

        writer.tryCommit();
    }

//    private void updateInsertOrder( final Txn<ByteBuffer> writeTxn,
//                                    final byte[] traceIdBytes) {
//        // Write update time for processing new traces.
//        final Instant updateTime =  Instant.now();
//        byteBuffers.useBytes(traceIdBytes, idBuffer -> {
//            byteBuffers.use(updateTimeSerde.getSize(), timeBuffer -> {
//                updateTimeSerde.write(timeBuffer, updateTime);
//                timeBuffer.flip();
//
//                traceUpdateTimeDbi.put(writeTxn, idBuffer, timeBuffer);
//                byteBuffers.use(traceIdBytes.length + updateTimeSerde.getSize(), keyBuffer -> {
//                    keyBuffer.put(traceIdBytes);
//                    updateTimeSerde.write(keyBuffer, updateTime);
//                    keyBuffer.flip();
//                    updateTimeDbi.put(writeTxn, keyBuffer, VALUE);
//                });
//            });
//        });
//    }

//    public void iterateTraceRoots(final BiConsumer<TraceRoot, Function<TraceRoot, Trace>> consumer) {
//        env.read(txn -> {
//            iterate(txn, kv -> {
//                final TraceRoot traceRoot = traceRootValueSerde.read(kv.val());
//                consumer.accept(traceRoot, root -> getTrace(txn, root.getTraceId()));
//            }, traceRootsDbi);
//            return null;
//        });
//    }

    public void iterateTraces(final BiConsumer<byte[], Function<byte[], Trace>> consumer) {
        env.read(txn -> {
            try (final Stream<LmdbEntry> stream = LmdbStream.stream(txn, traceRootsDbi)) {
                stream.forEach(entry -> {
                    try {
                        final byte[] traceId = ByteBufferUtils.toBytes(entry.getKey());
                        consumer.accept(traceId, id -> getTrace(txn, id));
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e::getMessage, e);
                    }
                });
            }
            return null;
        });
    }

    @Override
    public void merge(final Path source) {
        env.write(writer -> {
            try (final TraceDb sourceDb = TraceDb.create(source, byteBuffers, byteBufferFactory, doc, true)) {
                // Validate that the source DB has the same schema.
                validateSchema(schemaInfo, sourceDb.getSchemaInfo());

                // Merge.
                sourceDb.env.read(readTxn -> {
                    try (final Stream<LmdbEntry> stream = LmdbStream.stream(readTxn, sourceDb.dbi)) {
                        stream.forEach(entry -> {
                            if (sourceDb.keySerde.usesLookup(entry.getKey()) ||
                                sourceDb.valueSerde.usesLookup(entry.getVal())) {
                                // We need to do a full read and merge.
                                final SpanKey spanKey = sourceDb.keySerde.read(readTxn, entry.getKey());
                                final SpanValue spanValue = sourceDb.valueSerde.read(readTxn, entry.getVal());
                                insert(writer, new SpanKV(spanKey, spanValue));
                            } else {
                                // Quick merge.
                                if (dbi.put(writer.getWriteTxn(), entry.getKey(), entry.getVal(), putFlags)) {
                                    writer.tryCommit();
                                }
                            }
                        });
                    }

                    // Merge trace roots.
                    LmdbIterable.iterate(readTxn, sourceDb.traceRootsDbi, (key, val) -> {
                        if (traceRootsDbi.put(writer.getWriteTxn(), key, val, putFlags)) {
                            writer.tryCommit();
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
    public SpanValue get(final SpanKey key) {
        return env.read(readTxn -> keySerde.toBufferForGet(readTxn, key, optionalKeyByteBuffer ->
                optionalKeyByteBuffer.map(keyByteBuffer -> {
                    final ByteBuffer valueByteBuffer = dbi.get(readTxn, keyByteBuffer);
                    if (valueByteBuffer == null) {
                        return null;
                    }
                    return valueSerde.read(readTxn, valueByteBuffer);
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

    private Function<Context, SpanKey> getKeyExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> keySerde.read(readTxn, context.key().duplicate());
    }

    private Function<Context, SpanValue> getValExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> valueSerde.read(readTxn, context.val().duplicate());
    }

//    public Trace getTrace(final TraceRequest request) {
//        final SpanValue value = get(request.key());
//        if (value == null) {
//            return null;
//        }
//        return new Trace(request.key(), value);
//    }

    public static ValuesExtractor createValuesExtractor(final FieldIndex fieldIndex,
                                                        final Function<Context, SpanKey> keyFunction,
                                                        final Function<Context, SpanValue> valFunction) {
        final String[] fields = fieldIndex.getFields();
        final TraceConverter[] converters = new TraceConverter[fields.length];
        for (int i = 0; i < fields.length; i++) {
            converters[i] = switch (fields[i]) {
                case TraceFields.TRACE_ID -> kv ->
                        ValString.create(kv.getKey().getTraceId());
                case TraceFields.PARENT_SPAN_ID -> kv ->
                        ValString.create(kv.getKey().getParentSpanId());
                case TraceFields.SPAN_ID -> kv ->
                        ValString.create(kv.getKey().getSpanId());
                default -> kv -> ValNull.INSTANCE;
            };
        }
        return (readTxn, key, value) -> {
            final Context context = new Context(readTxn, key, value);
            final LazyKV<SpanKey, SpanValue> lazyKV = new LazyKV<>(context, keyFunction, valFunction);
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
            final NanoTime nanoTime = NanoTimeUtil.fromInstant(deleteBefore);
            final long count = deleteOldData(writer, nanoTime);

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
                               final NanoTime deleteBefore) {
        return env.read(readTxn -> {
            final Count changeCount = new Count();

            // Delete old spans.
            LmdbIterable.iterate(readTxn, dbi, (key, val) -> {
                final SpanValue value = valueSerde.read(readTxn, val.duplicate());

                if (value.getInsertTime().isBefore(deleteBefore)) {
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

            // Delete old trace roots.
            LmdbIterable.iterate(readTxn, traceRootsDbi, (key, val) -> {
                final TraceRoot value = traceRootValueSerde.read(val.duplicate());
                if (value.getStartTime().isBefore(deleteBefore)) {
                    // If this is data we no longer want to retain then delete it.
                    traceRootsDbi.delete(writer.getWriteTxn(), key);
                    changeCount.increment();
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

    public TracesResultPage findTraces(final FindTraceCriteria criteria) {
        final List<TraceRoot> list = new ArrayList<>();
        final PageResponse.Builder builder = PageResponse.builder();

        final Comparator<Span> spanComparator = new CloseSpanComparator(criteria.getTemporalOrderingTolerance());
        final PathKeyFactory pathKeyFactory = new PathKeyFactoryImpl();
        if (criteria.getPathway() != null) {
            final TracePredicate tracePredicate = new TracePredicate(
                    spanComparator,
                    pathKeyFactory,
                    Map.of(criteria.getPathway().getPathKey(), criteria.getPathway().getRoot()));

            // Just find traces in the requested range.
            env.read(readTxn -> {
                final Count count = new Count();
                LmdbIterable.iterate(readTxn, traceRootsDbi, (key, val) -> {
                    try {
                        final TraceRootKey traceRootKey = traceRootKeySerde.read(key);
                        final TraceRoot root = traceRootValueSerde.read(val);
                        final TraceBuilder traceBuilder = new TraceBuilder(root.getTraceId());
                        // Get all the spans.
                        byteBuffers.useBytes(traceRootKey.getTraceId(), prefixBuffer -> {
                            findSpans(readTxn, traceRootKey.getTraceId(), traceBuilder::addSpan);
                        });
                        final Trace trace = traceBuilder.build();

                        final long pos = count.getAndIncrement();
                        if (criteria.getPageRequest().getOffset() <= pos &&
                            criteria.getPageRequest().getLength() > list.size() &&
                            tracePredicate.test(trace)) {
                            list.add(root);
                        }
                    } catch (final RuntimeException e) {
                        // Expected exception if no trace root.
                        LOGGER.debug(e.getMessage(), e);
                    }
                });
                builder.offset(criteria.getPageRequest().getOffset());
                builder.length(list.size());
                builder.total(count.get());
                builder.exact(true);
                return list;
            });

        } else {
            // Just find traces in the requested range.
            env.read(readTxn -> {
                final Count count = new Count();

                LmdbIterable.iterate(readTxn, traceRootsDbi, (key, val) -> {
                    final long pos = count.getAndIncrement();
                    if (criteria.getPageRequest().getOffset() <= pos &&
                        criteria.getPageRequest().getLength() > list.size()) {
                        final TraceRoot root = traceRootValueSerde.read(val);
                        list.add(root);
                    }
                });

                builder.offset(criteria.getPageRequest().getOffset());
                builder.length(list.size());
                builder.total(count.get());
                builder.exact(true);
                return list;
            });
        }

        return new TracesResultPage(list, builder.build());
    }

    public Trace getTrace(final GetTraceRequest request) {
        return env.read(readTxn -> getTrace(readTxn, request.getTraceId()));
    }

    public Trace getTrace(final Txn<ByteBuffer> txn, final byte[] traceId) {
        final TraceBuilder traceBuilder = new TraceBuilder(HexStringUtil.encode(traceId));
        // Get all the spans.
        byteBuffers.useBytes(traceId, prefixBuffer -> {
            findSpans(txn, traceId, traceBuilder::addSpan);
        });
        return traceBuilder.build();
    }

    public Trace getTrace(final Txn<ByteBuffer> txn, final String traceIdString) {
        final byte[] traceId = HexStringUtil.decode(traceIdString);
        final TraceBuilder traceBuilder = new TraceBuilder(traceIdString);
        // Get all the spans.
        byteBuffers.useBytes(traceId, prefixBuffer -> {
            findSpans(txn, traceId, traceBuilder::addSpan);
        });
        return traceBuilder.build();
    }

    private void findSpans(final Txn<ByteBuffer> txn,
                           final byte[] traceId,
                           final Consumer<Span> consumer) {
        byteBuffers.useBytes(traceId, prefixBuffer -> {
            // Get all the spans.
            final LmdbKeyRange keyRange = LmdbKeyRange.builder().prefix(prefixBuffer).build();
            LmdbIterable.iterate(txn, dbi, keyRange, (key, val) -> {
                final SpanKey spanKey = keySerde.read(txn, key);
                final SpanValue spanValue = valueSerde.read(txn, val);
                final Span span = createSpan(spanKey, spanValue);
                consumer.accept(span);
            });
        });
    }

    private static class TraceBuilder {

        private final String traceId;
        private final Map<String, Map<String, Span>> traceMap = new ConcurrentHashMap<>();

        public TraceBuilder(final String traceId) {
            this.traceId = traceId;
        }

        public void addSpan(final Span span) {
            traceMap.computeIfAbsent(NullSafe.getOrElse(span, Span::getParentSpanId, ""),
                            k -> new ConcurrentHashMap<>())
                    .put(span.getSpanId(), span);
        }

        public Trace build() {
            final Map<String, List<Span>> parentSpanIdMap = traceMap
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Entry::getKey,
                            entry -> entry
                                    .getValue()
                                    .values()
                                    .stream()
                                    .sorted(Comparator.comparing(Span::start))
                                    .toList()));
            return Trace.builder().traceId(traceId).parentSpanIdMap(parentSpanIdMap).build();
        }
    }

    private Span createSpan(final SpanKey spanKey, final SpanValue spanValue) {
        return Span.builder()
                .traceId(spanKey.getTraceId())
                .spanId(spanKey.getSpanId())
                .parentSpanId(spanKey.getParentSpanId())
                .traceState(spanValue.getTraceState())
                .flags(spanValue.getFlags())
                .name(spanValue.getName())
                .kind(spanValue.getKind())
                .startTimeUnixNano(spanValue.getStartTimeUnixNano())
                .endTimeUnixNano(spanValue.getEndTimeUnixNano())
                .attributes(spanValue.getAttributes())
                .droppedAttributesCount(spanValue.getDroppedAttributesCount())
                .events(spanValue.getEvents())
                .droppedEventsCount(spanValue.getDroppedEventsCount())
                .links(spanValue.getLinks())
                .droppedLinksCount(spanValue.getDroppedLinksCount())
                .status(spanValue.getStatus())
                .build();
    }

    public interface TraceConverter extends Converter<SpanKey, SpanValue> {

    }
}
