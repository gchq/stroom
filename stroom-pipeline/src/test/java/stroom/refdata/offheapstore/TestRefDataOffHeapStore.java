/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple3;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Range;
import stroom.properties.MockStroomPropertyService;
import stroom.properties.StroomPropertyService;
import stroom.refdata.RefDataModule;
import stroom.refdata.offheapstore.databases.AbstractLmdbDbTest;
import stroom.refdata.offheapstore.serdes.StringValueSerde;
import stroom.util.ByteSizeUnit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class TestRefDataOffHeapStore extends AbstractLmdbDbTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestRefDataOffHeapStore.class);

    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();

    @Inject
    private RefDataStore refDataStore;

    private final MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();

    @Before
    public void setup() {

        Path dbDir = tmpDir.getRoot().toPath();
        LOGGER.debug("Creating LMDB environment in dbDir {}", dbDir.toAbsolutePath().toString());

        mockStroomPropertyService.setProperty(RefDataStoreProvider.OFF_HEAP_STORE_DIR_PROP_KEY,
                dbDir.toAbsolutePath().toString());
        mockStroomPropertyService.setProperty(RefDataStoreProvider.MAX_STORE_SIZE_BYTES_PROP_KEY,
                Long.toString(ByteSizeUnit.KIBIBYTE.longBytes(100)));

        final Injector injector = Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(StroomPropertyService.class).toInstance(mockStroomPropertyService);
                        install(new RefDataModule());
                    }
                });
        injector.injectMembers(this);
    }

    @Test
    public void getProcessingInfo() {
    }

    @Test
    public void isDataLoaded_true() {
    }

    @Test
    public void isDataLoaded_false() {
        byte version = 0;
        final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                UUID.randomUUID().toString(),
                version,
                123456L,
                1);

        boolean isLoaded = refDataStore.isDataLoaded(refStreamDefinition);

        assertThat(isLoaded).isFalse();
    }

    @Test
    public void getValueProxy() {
    }

    @Test
    public void loader_noOverwriteBigCommitInterval() throws Exception {
        boolean overwriteExisting = false;
        int commitInterval = Integer.MAX_VALUE;

        bulkLoadAndAssert(overwriteExisting, commitInterval);
    }

    @Test
    public void loader_noOverwriteSmallCommitInterval() throws Exception {
        boolean overwriteExisting = false;
        int commitInterval = 2;

        bulkLoadAndAssert(overwriteExisting, commitInterval);
    }

    private void bulkLoadAndAssert(final boolean overwriteExisting, final int commitInterval) {
        List<RefStreamDefinition> refStreamDefinitions = Arrays.asList(
                new RefStreamDefinition(
                        UUID.randomUUID().toString(),
                        (byte) 0,
                        123456L,
                        1),
                new RefStreamDefinition(
                        UUID.randomUUID().toString(),
                        (byte) 0,
                        123456L,
                        1));

        long effectiveTimeMs = System.currentTimeMillis();

        List<String> mapNames = Arrays.asList("map1", "map2");

        AtomicInteger counter = new AtomicInteger();
        List<Tuple3<MapDefinition, String, StringValue>> keyValueLoadedData = new ArrayList<>();
        List<Tuple3<MapDefinition, Range<Long>, StringValue>> keyRangeValueLoadedData = new ArrayList<>();

        refStreamDefinitions.forEach(refStreamDefinition -> {
            try {
                loadData(refStreamDefinition,
                        effectiveTimeMs,
                        commitInterval,
                        mapNames,
                        overwriteExisting,
                        counter,
                        keyValueLoadedData,
                        keyRangeValueLoadedData);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            ((RefDataOffHeapStore) refDataStore).logAllContents();

            RefDataProcessingInfo refDataProcessingInfo = refDataStore.getProcessingInfo(refStreamDefinition).get();

            assertThat(refDataProcessingInfo.getProcessingState()).isEqualTo(RefDataProcessingInfo.ProcessingState.COMPLETE);

        });
        assertLoadedKeyValueData(keyValueLoadedData);
        assertLoadedKeyRangeValueData(keyRangeValueLoadedData);
    }

    private void assertLoadedKeyValueData(final List<Tuple3<MapDefinition, String, StringValue>> keyValueLoadedData) {
        // query all values from the key/value store
        keyValueLoadedData.forEach(tuple3 -> {
            // get the proxy object
            Optional<RefDataValueProxy> optValueProxy = refDataStore.getValueProxy(tuple3._1, tuple3._2);

            assertThat(optValueProxy).isNotEmpty();

            RefDataValue refDataValue = optValueProxy
                    .flatMap(RefDataValueProxy::supplyValue)
                    .get();

            assertThat(refDataValue).isInstanceOf(StringValue.class);
            assertThat((StringValue) refDataValue).isEqualTo(tuple3._3);

            // now consume the proxied value in a txn
            optValueProxy.ifPresent(proxy -> {
                proxy.consumeBytes(byteBuffer -> {
                    RefDataValue refDataValue2 = new StringValueSerde().deserialize(byteBuffer);
                    assertThat(refDataValue2).isEqualTo(tuple3._3);
                });
            });
        });
    }

    private void assertLoadedKeyRangeValueData(final List<Tuple3<MapDefinition, Range<Long>, StringValue>> keyRangeValueLoadedData) {
        keyRangeValueLoadedData.forEach(tuple3 -> {

            // build a variety of keys from the supplied range
            String keyAtStartOfRange = tuple3._2.getFrom().toString();
            String keyAtEndOfRange = Long.toString(tuple3._2.getTo() - 1);
            String keyInsideRange = Long.toString(tuple3._2.getFrom() + 5);
            String keyBelowRange = Long.toString(tuple3._2.getFrom() - 1);
            String keyAboveRange = Long.toString(tuple3._2.getTo() + 1);

            // define the expected result for each key
            List<Tuple2<String, Boolean>> keysAndExpectedResults = Arrays.asList(
                    Tuple.of(keyAtStartOfRange, true),
                    Tuple.of(keyAtEndOfRange, true),
                    Tuple.of(keyInsideRange, true),
                    Tuple.of(keyBelowRange, false),
                    Tuple.of(keyAboveRange, false));

            keysAndExpectedResults.forEach(tuple2 -> {
                LOGGER.debug("range {}, key {}, expected {}", tuple3._2, tuple2._1, tuple2._2);

                // get the proxy object
                Optional<RefDataValueProxy> optValueProxy = refDataStore.getValueProxy(tuple3._1, tuple2._1);

                boolean isValueExpected = tuple2._2;
                assertThat(optValueProxy.isPresent()).isEqualTo(isValueExpected);

                if (optValueProxy.isPresent()) {
                    RefDataValue refDataValue = optValueProxy
                            .flatMap(RefDataValueProxy::supplyValue)
                            .get();

                    assertThat(refDataValue).isInstanceOf(StringValue.class);
                    assertThat((StringValue) refDataValue).isEqualTo(tuple3._3);

                    // now consume the proxied value in a txn
                    optValueProxy.ifPresent(proxy -> {
                        proxy.consumeBytes(byteBuffer -> {
                            RefDataValue refDataValue2 = new StringValueSerde().deserialize(byteBuffer);
                            assertThat(refDataValue2).isEqualTo(tuple3._3);
                        });
                    });
                }
            });
        });
    }

    private void loadData(
            final RefStreamDefinition refStreamDefinition,
            final long effectiveTimeMs,
            final int commitInterval,
            final List<String> mapNames,
            final boolean overwriteExisting,
            final AtomicInteger counter,
            final List<Tuple3<MapDefinition, String, StringValue>> keyValueLoadedData,
            final List<Tuple3<MapDefinition, Range<Long>, StringValue>> keyRangeValueLoadedData) throws Exception {

        try (RefDataLoader loader = refDataStore.loader(refStreamDefinition, effectiveTimeMs)) {
            loader.initialise(overwriteExisting);
            loader.setCommitInterval(commitInterval);

            for (int i = 0; i < 10; i++) {
                // put key/values into two mapDefs
                mapNames.stream()
                        .map(name -> new MapDefinition(refStreamDefinition, name))
                        .forEach(mapDefinition -> {
                            int cnt = counter.incrementAndGet();
                            String key = "key" + cnt;
                            StringValue value = StringValue.of("value" + cnt);
                            LOGGER.debug("Putting cnt {}, key {}, value {}", cnt, key, value);
                            loader.put(mapDefinition, key, value, overwriteExisting);

                            keyValueLoadedData.add(Tuple.of(mapDefinition, key, value));
                        });

                // put keyrange/values into two mapDefs
                mapNames.stream()
                        .map(name -> new MapDefinition(refStreamDefinition, name))
                        .forEach(mapDefinition -> {
                            int cnt = counter.incrementAndGet();
                            Range<Long> keyRange = new Range<>((long) (cnt * 10), (long) ((cnt * 10) + 10));
                            StringValue value = StringValue.of("value" + cnt);
                            LOGGER.debug("Putting cnt {}, key-range {}, value {}", cnt, keyRange, value);
                            loader.put(mapDefinition, keyRange, value, overwriteExisting);
                            keyRangeValueLoadedData.add(Tuple.of(mapDefinition, keyRange, value));
                        });
            }

            loader.completeProcessing();
        }
    }
}