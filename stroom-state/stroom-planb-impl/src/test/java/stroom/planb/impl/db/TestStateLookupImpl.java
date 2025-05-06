package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.pipeline.refdata.store.StringValue;
import stroom.planb.impl.db.TemporalState.Key;
import stroom.planb.impl.db.state.StateValue;
import stroom.planb.shared.TemporalStateSettings;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestStateLookupImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestStateLookupImpl.class);
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());

    private static final String KV_TYPE = "KV";
    private static final String PADDING = IntStream.rangeClosed(1, 300)
            .boxed()
            .map(i -> "-")
            .collect(Collectors.joining());

    /**
     * This is meant to be compared to
     * stroom.pipeline.refdata.store.offheapstore.TestRefDataOffHeapStore#testLookupPerf()
     */
//    @Disabled // Manual run only
    @Test
    void perfTest(@TempDir Path tempDir) {
        int entryCount = 5_000;
        int refStreamDefCount = 5;
        int keyValueMapCount = 20;
        int batchSize = 1_000;
        final Instant baseTime = Instant.now().truncatedTo(ChronoUnit.DAYS);

        // refStrmIdx => mapNames
        final Map<Integer, List<String>> mapNamesMap = new HashMap<>(refStreamDefCount);
        final List<Instant> lookupTimes = new ArrayList<>(refStreamDefCount);

        try (final TemporalStateDb db = new TemporalStateDb(tempDir, BYTE_BUFFERS)) {
            db.write(writer -> {
                for (int refStrmIdx = 0; refStrmIdx < refStreamDefCount; refStrmIdx++) {
                    final List<String> mapNames = mapNamesMap.computeIfAbsent(refStrmIdx,
                            k -> new ArrayList<>(keyValueMapCount));

                    // The time loaded into the store
                    final Instant strmTime = baseTime.minus(refStrmIdx, ChronoUnit.DAYS);
                    // The time we use to lookup with, i.e. a smidge after the strm time in the store
                    final Instant lookupTime = strmTime.plus(5, ChronoUnit.SECONDS);
                    lookupTimes.add(lookupTime);

                    for (int mapIdx = 0; mapIdx < keyValueMapCount; mapIdx++) {
                        final String mapName = buildMapName(KV_TYPE, mapIdx);
                        mapNames.add(mapName);
                        for (int keyIdx = 0; keyIdx < entryCount; keyIdx++) {
                            final String key = buildKey(keyIdx);
                            final String val = buildKeyStoreValue(mapName, keyIdx, key);
                            final ByteBuffer byteBuffer = ByteBuffer.wrap(val.getBytes(StandardCharsets.UTF_8));

                            final Key k = Key.builder().name(buildKey(keyIdx)).effectiveTime(strmTime).build();
                            final StateValue v = StateValue
                                    .builder()
                                    .typeId(StringValue.TYPE_ID)
                                    .byteBuffer(byteBuffer)
                                    .build();

                            db.insert(writer, k, v);
                        }
                    }
                }
            });
        }

        try (final TemporalStateDb db = new TemporalStateDb(
                tempDir,
                BYTE_BUFFERS,
                TemporalStateSettings.builder().build(),
                true)) {
            final Random random = new Random(892374809);
            final Runnable work = () -> {
                final int refStrmIdx = random.nextInt(refStreamDefCount);
                final int mapIdx = random.nextInt(keyValueMapCount);
                final int keyIdx = random.nextInt(entryCount);
                final String mapName = mapNamesMap.get(refStrmIdx).get(mapIdx);
                final String key = buildKey(keyIdx);
                final Instant time = lookupTimes.get(refStrmIdx);

                final TemporalStateRequest request = new TemporalStateRequest(
                        key.getBytes(StandardCharsets.UTF_8),
                        time.toEpochMilli());

                final TemporalState state = db.getState(request);
                assertThat(state).isNotNull();
                final String val = state.val().toString();
                Objects.requireNonNull(val);
            };

            DurationTimer timer;

            LOGGER.info("Starting multi thread lookups");
            timer = DurationTimer.start();
            int totalKeyValueEntryCount = refStreamDefCount * keyValueMapCount * entryCount;
            IntStream.rangeClosed(0, totalKeyValueEntryCount)
                    .boxed()
                    .parallel()
                    .forEach(i -> work.run());

            LOGGER.info("Completed {} multi thread lookups in {}",
                    ModelStringUtil.formatCsv(totalKeyValueEntryCount),
                    timer);

            LOGGER.info("Starting single thread lookups");
            timer = DurationTimer.start();
            for (int i = 0; i < totalKeyValueEntryCount; i++) {
                work.run();
            }

            LOGGER.info("Completed {} single thread lookups in {}",
                    ModelStringUtil.formatCsv(totalKeyValueEntryCount),
                    timer);
        }
    }

    private String buildKey(final int k) {
        return "key" + k;
    }

    private String buildKeyStoreValue(final String mapName,
                                      final int i,
                                      final String key) {
        // pad the values out to make them more realistic in length to see impact on writes
        return LogUtil.message("{}-{}-value{}{}", mapName, key, i, PADDING);
    }

    private String buildMapName(
            final String type,
            final int i) {
        return LogUtil.message("{}map{}",
                type, i);
    }
}
