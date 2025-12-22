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

package stroom.meta.impl.db;

import stroom.cache.impl.CacheModule;
import stroom.cluster.lock.mock.MockClusterLockModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.MockMetricsModule;
import stroom.test.common.util.db.DbTestModule;

import com.google.inject.Guice;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaTypeDaoImpl {

    private static final String BANANA = "Banana";
    private static final String MANGO = "Mango";
    private static final String CUSTARD_APPLE = "Custard_apple";
    private static final String KUMQUAT = "Kumquat";

    private Map<String, Integer> testTypeToIdMap = new HashMap<>();

    @Inject
    private Cleanup cleanup;
    @Inject
    private MetaTypeDaoImpl metaTypeDao;
    @Inject
    private MetaDbConnProvider metaDbConnProvider;

    @BeforeEach
    void setup() {
        Guice.createInjector(
                        new MetaTestModule(),
                        new MetaDbModule(),
                        new MetaDaoModule(),
                        new MockClusterLockModule(),
                        new MockSecurityContextModule(),
                        new MockTaskModule(),
                        new MockCollectionModule(),
                        new MockDocRefInfoModule(),
                        new MockWordListProviderModule(),
                        new MockMetricsModule(),
                        new CacheModule(),
                        new DbTestModule())
                .injectMembers(this);
        // Delete everything`
        cleanup.cleanup();
    }

    @Test
    void test() {
        assertThat(metaTypeDao.list().size()).isEqualTo(0);

        String typeName = "TEST";
        Integer id1 = metaTypeDao.getOrCreate(typeName);
        Integer id2 = metaTypeDao.getOrCreate(typeName);

        assertThat(id1).isEqualTo(id2);

        typeName = "TEST2";
        id1 = metaTypeDao.getOrCreate(typeName);
        id2 = metaTypeDao.getOrCreate(typeName);

        assertThat(id1).isEqualTo(id2);

        assertThat(metaTypeDao.list().size()).isEqualTo(2);
    }

    @Test
    void testGet() {
        final String typeName = "Foo";
        Optional<Integer> optId = metaTypeDao.get(typeName);

        assertThat(optId)
                .isEmpty();

        final Integer id = metaTypeDao.getOrCreate(typeName);

        assertThat(id)
                .isNotNull();

        optId = metaTypeDao.get(typeName);

        assertThat(optId)
                .hasValue(id);
    }

    @Test
    void testFind_empty() {

        setupFruitTypes();

        // No conditions so returns everything
        final Map<String, Integer> typeToIdsMap = metaTypeDao.find(Collections.emptyList());

        assertThat(typeToIdsMap)
                .hasSize(0);
    }

    @Test
    void testFind_single() {
        setupFruitTypes();

        final Map<String, Integer> typeToIdsMap = metaTypeDao.find(List.of(BANANA));
        assertThat(typeToIdsMap)
                .hasSize(1)
                .containsEntry(BANANA, testTypeToIdMap.get(BANANA));
    }

    @Test
    void testFind_single_wildCarded() {
        setupFruitTypes();

        final Map<String, Integer> typeToIdsMap = metaTypeDao.find(List.of("BANA*"));
        assertThat(typeToIdsMap)
                .hasSize(1)
                .containsEntry(BANANA, testTypeToIdMap.get(BANANA));
    }

    @Test
    void testFind_multiple() {
        setupFruitTypes();

        final Map<String, Integer> typeToIdsMap = metaTypeDao.find(List.of(
                BANANA,
                MANGO,
                CUSTARD_APPLE));

        assertThat(typeToIdsMap)
                .hasSize(3)
                .containsEntry(BANANA, testTypeToIdMap.get(BANANA))
                .containsEntry(MANGO, testTypeToIdMap.get(MANGO))
                .containsEntry(CUSTARD_APPLE, testTypeToIdMap.get(CUSTARD_APPLE));
    }

    @Test
    void testFind_multiple_wildCarded() {
        setupFruitTypes();

        final Map<String, Integer> typeToIdsMap = metaTypeDao.find(List.of(
                "BANA*",
                "*GO",
                "*TARD_APP*"));

        assertThat(typeToIdsMap)
                .hasSize(3)
                .containsEntry(BANANA, testTypeToIdMap.get(BANANA))
                .containsEntry(MANGO, testTypeToIdMap.get(MANGO))
                .containsEntry(CUSTARD_APPLE, testTypeToIdMap.get(CUSTARD_APPLE));
    }

    private void setupFruitTypes() {
        testTypeToIdMap.clear();
        List.of(
                        BANANA,
                        CUSTARD_APPLE,
                        KUMQUAT,
                        MANGO)
                .forEach(type -> {
                    metaTypeDao.getOrCreate(type);
                    final Optional<Integer> optId = metaTypeDao.get(type);
                    assertThat(optId)
                            .isPresent();
                    testTypeToIdMap.put(type, optId.orElseThrow());
                });
    }
}
