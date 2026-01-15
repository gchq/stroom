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

package stroom.pipeline.refdata.test;

import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.lmdb.PutOutcome;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.NullValue;
import stroom.pipeline.refdata.store.RefDataLoader;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.StagingValueOutputStream;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.ValueStoreHashAlgorithm;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Range;

import org.assertj.core.api.Assertions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class RefTestUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RefTestUtil.class);

    private RefTestUtil() {
    }

    public static void doPut(final ValueStoreHashAlgorithm valueStoreHashAlgorithm,
                             final PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory,
                             final RefDataLoader refDataLoader,
                             final MapDefinition mapDefinition,
                             final String key,
                             final RefDataValue refDataValue) {
        try (final StagingValueOutputStream stagingValueOutputStream = new StagingValueOutputStream(
                valueStoreHashAlgorithm, pooledByteBufferOutputStreamFactory)) {

            writeValue(stagingValueOutputStream, refDataValue);
            refDataLoader.put(mapDefinition, key, stagingValueOutputStream);
        }
    }

    public static void doPut(final ValueStoreHashAlgorithm valueStoreHashAlgorithm,
                             final PooledByteBufferOutputStream.Factory pooledByteBufferOutputStreamFactory,
                             final RefDataLoader refDataLoader,
                             final MapDefinition mapDefinition,
                             final Range<Long> range,
                             final RefDataValue refDataValue) {
        try (final StagingValueOutputStream stagingValueOutputStream = new StagingValueOutputStream(
                valueStoreHashAlgorithm, pooledByteBufferOutputStreamFactory)) {

            writeValue(stagingValueOutputStream, refDataValue);
            refDataLoader.put(mapDefinition, range, stagingValueOutputStream);
        }
    }

    public static void writeValue(final StagingValueOutputStream stagingValueOutputStream,
                                  final RefDataValue refDataValue) {
        stagingValueOutputStream.clear();
        try {
            if (refDataValue instanceof StringValue) {
                final StringValue stringValue = (StringValue) refDataValue;
                stagingValueOutputStream.write(stringValue.getValue());
                stagingValueOutputStream.setTypeId(StringValue.TYPE_ID);
            } else if (refDataValue instanceof FastInfosetValue) {
                stagingValueOutputStream.write(((FastInfosetValue) refDataValue).getByteBuffer());
                stagingValueOutputStream.setTypeId(FastInfosetValue.TYPE_ID);
            } else if (refDataValue instanceof NullValue) {
                stagingValueOutputStream.setTypeId(NullValue.TYPE_ID);
            } else {
                throw new RuntimeException("Unexpected type " + refDataValue.getClass().getSimpleName());
            }
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error writing value: {}", e.getMessage()), e);
        }
    }

    public static KeyOutcomeMap handleKeyOutcomes(final RefDataLoader refDataLoader) {

        final KeyOutcomeMap keyOutcomeMap = new KeyOutcomeMap();
        refDataLoader.setKeyPutOutcomeHandler((mapDefinitionSupplier, key, putOutcome) -> {
            final MapDefinition mapDefinition = mapDefinitionSupplier.get();
            LOGGER.debug(() -> LogUtil.message("Got outcome: {}, map: {}, key: {}",
                    putOutcome,
                    mapDefinition.getMapName(),
                    key));
            keyOutcomeMap.put(mapDefinition, key, putOutcome);
        });
        return keyOutcomeMap;
    }

    public static RangeOutcomeMap handleRangeOutcomes(final RefDataLoader refDataLoader) {
        final RangeOutcomeMap rangeOutcomeMap = new RangeOutcomeMap();
        refDataLoader.setRangePutOutcomeHandler((mapDefinitionSupplier, range, putOutcome) -> {
            final MapDefinition mapDefinition = mapDefinitionSupplier.get();
            LOGGER.debug(() -> LogUtil.message("Got outcome: {}, map: {}, key: {}",
                    putOutcome,
                    mapDefinition.getMapName(),
                    range));
            rangeOutcomeMap.put(mapDefinition, range, putOutcome);
        });
        return rangeOutcomeMap;
    }

    public static void assertPutOutcome(final PutOutcome putOutcome,
                                        final boolean expectedIsSuccess,
                                        final boolean expectedIsDuplicate) {
        assertThat(putOutcome.isSuccess())
                .isEqualTo(expectedIsSuccess);
        assertThat(putOutcome.isDuplicate())
                .hasValue(expectedIsDuplicate);
    }

    public static void assertPutOutcome(final PutOutcome putOutcome,
                                        final boolean expectedIsSuccess,
                                        final Optional<Boolean> expectedIsDuplicate) {
        assertThat(putOutcome.isSuccess())
                .isEqualTo(expectedIsSuccess);
        assertThat(putOutcome.isDuplicate())
                .isEqualTo(expectedIsDuplicate);
    }


    // --------------------------------------------------------------------------------


    public static class KeyOutcomeMap {

        // mapDef => key => List<PutOutcome>
        private final Map<MapDefinition, Map<String, List<PutOutcome>>> map = new HashMap<>();

        public void put(final MapDefinition mapDefinition, final String key, final PutOutcome putOutcome) {
            map.computeIfAbsent(mapDefinition, k -> new HashMap<>())
                    .computeIfAbsent(key, k -> new ArrayList<>())
                    .add(putOutcome);
        }

        public Optional<PutOutcome> getOutcome(final MapDefinition mapDefinition, final String key, final int idx) {
            return NullSafe.getAsOptional(
                    map.get(mapDefinition),
                    map2 -> map2.get(key),
                    list -> list.get(idx));
        }

        public void assertPutOutcome(final MapDefinition mapDefinition,
                                     final String key,
                                     final int idx,
                                     final boolean expectedIsSuccess,
                                     final boolean expectedIsDuplicate) {
            final PutOutcome putOutcome = getOutcome(mapDefinition, key, idx)
                    .orElseGet(() -> Assertions.fail(
                            LogUtil.message("Expecting an outcome for map: {}, key: {}, idx: {}",
                                    mapDefinition, key, idx)));
            RefTestUtil.assertPutOutcome(putOutcome, expectedIsSuccess, expectedIsDuplicate);
        }

        public void assertPutOutcome(final MapDefinition mapDefinition,
                                     final String key,
                                     final int idx,
                                     final boolean expectedIsSuccess,
                                     final Optional<Boolean> expectedIsDuplicate) {
            final PutOutcome putOutcome = getOutcome(mapDefinition, key, idx)
                    .orElseGet(() -> Assertions.fail(
                            LogUtil.message("Expecting an outcome for map: {}, key: {}, idx: {}",
                                    mapDefinition, key, idx)));
            RefTestUtil.assertPutOutcome(putOutcome, expectedIsSuccess, expectedIsDuplicate);
        }
    }


    // --------------------------------------------------------------------------------


    public static class RangeOutcomeMap {

        // mapDef => range => List<PutOutcome>
        private final Map<MapDefinition, Map<Range<Long>, List<PutOutcome>>> map = new HashMap<>();

        public void put(final MapDefinition mapDefinition, final Range<Long> range, final PutOutcome putOutcome) {
            map.computeIfAbsent(mapDefinition, k -> new HashMap<>())
                    .computeIfAbsent(range, k -> new ArrayList<>())
                    .add(putOutcome);
        }

        public Optional<PutOutcome> getOutcome(final MapDefinition mapDefinition,
                                               final Range<Long> range,
                                               final int idx) {
            return NullSafe.getAsOptional(
                    map.get(mapDefinition),
                    map2 -> map2.get(range),
                    list -> list.get(idx));
        }

        public void assertPutOutcome(final MapDefinition mapDefinition,
                                     final Range<Long> range,
                                     final int idx,
                                     final boolean expectedIsSuccess,
                                     final boolean expectedIsDuplicate) {
            final PutOutcome putOutcome = getOutcome(mapDefinition, range, idx)
                    .orElseGet(() -> Assertions.fail(
                            LogUtil.message("Expecting an outcome for map: {}, key: {}, idx: {}",
                                    mapDefinition, range, idx)));
            RefTestUtil.assertPutOutcome(putOutcome, expectedIsSuccess, expectedIsDuplicate);
        }

        public void assertPutOutcome(final MapDefinition mapDefinition,
                                     final Range<Long> range,
                                     final int idx,
                                     final boolean expectedIsSuccess,
                                     final Optional<Boolean> expectedIsDuplicate) {
            final PutOutcome putOutcome = getOutcome(mapDefinition, range, idx)
                    .orElseGet(() -> Assertions.fail(
                            LogUtil.message("Expecting an outcome for map: {}, key: {}, idx: {}",
                                    mapDefinition, range, idx)));
            RefTestUtil.assertPutOutcome(putOutcome, expectedIsSuccess, expectedIsDuplicate);
        }
    }
}
