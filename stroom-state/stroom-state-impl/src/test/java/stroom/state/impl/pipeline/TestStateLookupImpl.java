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

package stroom.state.impl.pipeline;

import org.junit.jupiter.api.Disabled;

@Disabled
class TestStateLookupImpl {
//
//    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestStateLookupImpl.class);
//
//    private static final String KV_TYPE = "KV";
//    private static final String PADDING = IntStream.rangeClosed(1, 300)
//            .boxed()
//            .map(i -> "-")
//            .collect(Collectors.joining());
//
//    /**
//     * This is meant to be compared to
//     * stroom.pipeline.refdata.store.offheapstore.TestRefDataOffHeapStore#testLookupPerf()
//     */
//    @Disabled // Manual run only
//    @Test
//    void perfTest() {
//        ScyllaDbUtil.test((sessionProvider, keyspace) -> {
//            final TemporalStateDao stateDao = new TemporalStateDao(sessionProvider, "test");
//
//            int entryCount = 5_000;
//            int refStreamDefCount = 5;
//            int keyValueMapCount = 20;
//            int batchSize = 1_000;
//
//            final List<TemporalState> batch = new ArrayList<>(batchSize);
//            final List<ByteBuffer> bufferPool = new ArrayList<>(batchSize);
//            for (int i = 0; i < batchSize; i++) {
//                bufferPool.add(ByteBuffer.allocate(500));
//            }
//
//            final Instant baseTime = Instant.now().truncatedTo(ChronoUnit.DAYS);
//
//            // refStrmIdx => mapNames
//            final Map<Integer, List<String>> mapNamesMap = new HashMap<>(refStreamDefCount);
//            final List<Instant> lookupTimes = new ArrayList<>(refStreamDefCount);
//
//            for (int refStrmIdx = 0; refStrmIdx < refStreamDefCount; refStrmIdx++) {
//                final List<String> mapNames = mapNamesMap.computeIfAbsent(refStrmIdx,
//                        k -> new ArrayList<>(keyValueMapCount));
//
//                // The time loaded into the store
//                final Instant strmTime = baseTime.minus(refStrmIdx, ChronoUnit.DAYS);
//                // The time we use to lookup with, i.e. a smidge after the strm time in the store
//                final Instant lookupTime = strmTime.plus(5, ChronoUnit.SECONDS);
//                lookupTimes.add(lookupTime);
//
//                int idxInBatch = 0;
//                for (int mapIdx = 0; mapIdx < keyValueMapCount; mapIdx++) {
//                    final String mapName = buildMapName(KV_TYPE, mapIdx);
//                    mapNames.add(mapName);
//                    for (int keyIdx = 0; keyIdx < entryCount; keyIdx++) {
//
//                        final String key = buildKey(keyIdx);
//                        final String val = buildKeyStoreValue(mapName, keyIdx, key);
//
//                        final ByteBuffer byteBuffer = bufferPool.get(idxInBatch);
//                        byteBuffer.clear();
//                        byteBuffer.put(val.getBytes(StandardCharsets.UTF_8));
//                        byteBuffer.flip();
//                        final TemporalState state = new TemporalState(
//                                buildKey(keyIdx),
//                                strmTime,
//                                StringValue.TYPE_ID,
//                                byteBuffer);
//                        batch.add(state);
//                        if (idxInBatch++ >= batchSize - 1) {
//                            stateDao.insert(batch);
//                            idxInBatch = 0;
//                            batch.clear();
//                        }
//                    }
//                }
//                if (!batch.isEmpty()) {
//                    stateDao.insert(batch);
//                }
//            }
//
//            final Random random = new Random(892374809);
//
//            final Runnable work = () -> {
//                final int refStrmIdx = random.nextInt(refStreamDefCount);
//                final int mapIdx = random.nextInt(keyValueMapCount);
//                final int keyIdx = random.nextInt(entryCount);
//                final String mapName = mapNamesMap.get(refStrmIdx).get(mapIdx);
//                final String key = buildKey(keyIdx);
//                final Instant time = lookupTimes.get(refStrmIdx);
//
//                final TemporalStateRequest request = new TemporalStateRequest(mapName, key, time);
//                final TemporalState state = stateDao.getState(request)
//                        .orElseThrow(() -> new RuntimeException(LogUtil.message(
//                                "No entry found for map: {}, key: {}, time: {}",
//                                mapName, key, time)));
//                final String val = state.getValueAsString();
//                Objects.requireNonNull(val);
//            };
//
//            DurationTimer timer;
//
//            LOGGER.info("Starting multi thread lookups");
//            timer = DurationTimer.start();
//            int totalKeyValueEntryCount = refStreamDefCount * keyValueMapCount * entryCount;
//            IntStream.rangeClosed(0, totalKeyValueEntryCount)
//                    .boxed()
//                    .parallel()
//                    .forEach(i -> work.run());
//
//            LOGGER.info("Completed {} multi thread lookups in {}",
//                    ModelStringUtil.formatCsv(totalKeyValueEntryCount),
//                    timer);
//
//            LOGGER.info("Starting single thread lookups");
//            timer = DurationTimer.start();
//            for (int i = 0; i < totalKeyValueEntryCount; i++) {
//                work.run();
//            }
//
//            LOGGER.info("Completed {} single thread lookups in {}",
//                    ModelStringUtil.formatCsv(totalKeyValueEntryCount),
//                    timer);
//        });
//    }
//
//    private String buildKey(final int k) {
//        return "key" + k;
//    }
//
//    private String buildKeyStoreValue(final String mapName,
//                                      final int i,
//                                      final String key) {
//        // pad the values out to make them more realistic in length to see impact on writes
//        return LogUtil.message("{}-{}-value{}{}", mapName, key, i, PADDING);
//    }
//
//    private String buildMapName(
//            final String type,
//            final int i) {
//        return LogUtil.message("{}map{}",
//                type, i);
//    }
}
