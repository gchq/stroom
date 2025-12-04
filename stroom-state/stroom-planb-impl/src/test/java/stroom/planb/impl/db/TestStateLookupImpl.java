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

package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.data.TemporalState;
import stroom.planb.impl.db.temporalstate.TemporalStateDb;
import stroom.planb.impl.db.temporalstate.TemporalStateRequest;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.temporalkey.TemporalKey;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.TemporalStateSettings;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.util.io.ByteSize;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestStateLookupImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestStateLookupImpl.class);
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());
    private static final TemporalStateSettings BASIC_SETTINGS = new TemporalStateSettings
            .Builder()
            .maxStoreSize(ByteSize.ofGibibytes(100).getBytes())
            .build();
    private static final PlanBDoc DOC = getDoc(BASIC_SETTINGS);
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
    void perfTest(@TempDir final Path tempDir) {
        final int entryCount = 5_000;
        final int refStreamDefCount = 5;
        final int keyValueMapCount = 20;
        final int batchSize = 1_000;
        final Instant baseTime = Instant.now().truncatedTo(ChronoUnit.DAYS);

        // refStrmIdx => mapNames
        final Map<Integer, List<String>> mapNamesMap = new HashMap<>(refStreamDefCount);
        final List<Instant> lookupTimes = new ArrayList<>(refStreamDefCount);

        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, DOC, false)) {
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
                            final KeyPrefix key = buildKey(keyIdx);
                            final String val = buildKeyStoreValue(mapName, keyIdx, key);

                            final TemporalKey k = TemporalKey.builder().prefix(buildKey(keyIdx)).time(strmTime).build();
                            final Val v = ValString.create(val);

                            db.insert(writer, new TemporalState(k, v));
                        }
                    }
                }
            });
        }

        try (final TemporalStateDb db = TemporalStateDb.create(
                tempDir,
                BYTE_BUFFERS,
                DOC,
                true)) {
            final Random random = new Random(892374809);
            final Runnable work = () -> {
                final int refStrmIdx = random.nextInt(refStreamDefCount);
                final int mapIdx = random.nextInt(keyValueMapCount);
                final int keyIdx = random.nextInt(entryCount);
                final String mapName = mapNamesMap.get(refStrmIdx).get(mapIdx);
                final KeyPrefix key = buildKey(keyIdx);
                final Instant time = lookupTimes.get(refStrmIdx);

                final TemporalStateRequest request = new TemporalStateRequest(
                        new TemporalKey(key, time));

                final TemporalState state = db.getState(request);
                assertThat(state).isNotNull();
                final String val = state.val().toString();
                Objects.requireNonNull(val);
            };

            DurationTimer timer;

            LOGGER.info("Starting multi thread lookups");
            timer = DurationTimer.start();
            final int totalKeyValueEntryCount = refStreamDefCount * keyValueMapCount * entryCount;
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

    private KeyPrefix buildKey(final int k) {
        return KeyPrefix.create("key" + k);
    }

    private String buildKeyStoreValue(final String mapName,
                                      final int i,
                                      final KeyPrefix key) {
        // pad the values out to make them more realistic in length to see impact on writes
        return LogUtil.message("{}-{}-value{}{}", mapName, key, i, PADDING);
    }

    private String buildMapName(
            final String type,
            final int i) {
        return LogUtil.message("{}map{}",
                type, i);
    }

    private static PlanBDoc getDoc(final TemporalStateSettings settings) {
        return PlanBDoc.builder().uuid(UUID.randomUUID().toString()).name("test").settings(settings).build();
    }
}
