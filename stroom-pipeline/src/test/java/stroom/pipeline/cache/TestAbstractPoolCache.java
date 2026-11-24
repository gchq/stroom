/*
 * Copyright 2026 Crown Copyright
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

package stroom.pipeline.cache;

import stroom.cache.api.CacheManager;
import stroom.cache.impl.CacheManagerImpl;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.PropertyPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAbstractPoolCache {

    private static final String CACHE_NAME = "MyPoolCache";
    private static final String KEY = "foo";

    private final CacheManagerImpl cacheManager = new CacheManagerImpl();
    private final CacheConfig cacheConfig = CacheConfig.builder()
            .build();
    private final AtomicInteger valueCreationCounter = new AtomicInteger();

    private MyPoolCache pool;

    @BeforeEach
    void setUp() {
        cacheConfig.setBasePath(PropertyPath.fromParts("test", CACHE_NAME));
        pool = new MyPoolCache(cacheManager, () -> cacheConfig);
    }

    @Test
    void testBorrowReturn_reusesItem() {
        final PoolItem<String> poolItem1 = pool.borrow(KEY);
        pool.giveBack(poolItem1);

        final PoolItem<String> poolItem2 = pool.borrow(KEY);

        assertThat(poolItem2.getValue())
                .isEqualTo(poolItem1.getValue());
        assertThat(valueCreationCounter)
                .hasValue(1);
    }

    @Test
    void testInvalidate_idleItem() {
        final PoolItem<String> poolItem1 = pool.borrow(KEY);
        pool.giveBack(poolItem1);

        pool.invalidate(KEY);

        assertThat(pool.getKeys())
                .isEmpty();
        final PoolItem<String> poolItem2 = pool.borrow(KEY);
        assertThat(poolItem2.getValue())
                .isNotEqualTo(poolItem1.getValue());
        assertThat(valueCreationCounter)
                .hasValue(2);
    }

    @Test
    void testInvalidate_borrowedItemNotReturnedToPool() {
        final PoolItem<String> poolItem1 = pool.borrow(KEY);

        // e.g. an XSLT that poolItem1 imports is saved while poolItem1 is in use
        pool.invalidate(KEY);
        pool.giveBack(poolItem1);

        final PoolItem<String> poolItem2 = pool.borrow(KEY);
        assertThat(poolItem2.getValue())
                .isNotEqualTo(poolItem1.getValue());
        assertThat(valueCreationCounter)
                .hasValue(2);
    }

    @Test
    void testInvalidate_itemBeingCreatedNotReturnedToPool() {
        // e.g. an XSLT that the item imports is saved after the item has read the old version of it
        pool.setOnCreate(key -> pool.invalidate(key));
        final PoolItem<String> poolItem1 = pool.borrow(KEY);
        pool.setOnCreate(null);

        pool.giveBack(poolItem1);

        final PoolItem<String> poolItem2 = pool.borrow(KEY);
        assertThat(poolItem2.getValue())
                .isNotEqualTo(poolItem1.getValue());
        assertThat(valueCreationCounter)
                .hasValue(2);
    }

    @Test
    void testInvalidate_oldItemIgnoredByNewPool() {
        final PoolItem<String> poolItem1 = pool.borrow(KEY);
        pool.invalidate(KEY);

        // poolItem2 comes from a new pool for the same key
        final PoolItem<String> poolItem2 = pool.borrow(KEY);
        pool.giveBack(poolItem1);
        pool.giveBack(poolItem2);

        // Only poolItem2 is in the pool, so a second concurrent borrow needs a new item
        final PoolItem<String> poolItem3 = pool.borrow(KEY);
        final PoolItem<String> poolItem4 = pool.borrow(KEY);

        assertThat(poolItem3.getValue())
                .isEqualTo(poolItem2.getValue());
        assertThat(poolItem4.getValue())
                .isNotIn(poolItem1.getValue(), poolItem2.getValue());
        assertThat(valueCreationCounter)
                .hasValue(3);
    }

    @Test
    void testGetKeys_includesBorrowedItems() {
        assertThat(pool.getKeys())
                .isEmpty();

        // Must be visible to whatever decides which keys to invalidate, before the first return
        final PoolItem<String> poolItem1 = pool.borrow(KEY);

        assertThat(pool.getKeys())
                .containsExactly(KEY);

        pool.giveBack(poolItem1);

        assertThat(pool.getKeys())
                .containsExactly(KEY);
    }

    @Test
    void testGetKeys_keyNotHeldOnceInvalidatedItemsReturned() {
        final PoolItem<String> poolItem1 = pool.borrow(KEY);
        pool.invalidate(KEY);
        pool.giveBack(poolItem1);

        assertThat(pool.getKeys())
                .isEmpty();
    }

    @Test
    void testGetKeys_keyNotHeldAfterFailedCreate() {
        pool.setOnCreate(key -> {
            throw new RuntimeException("Create failed");
        });

        assertThatThrownBy(() -> pool.borrow(KEY))
                .hasMessageContaining("Create failed");
        assertThat(pool.getKeys())
                .isEmpty();
    }

    @Test
    void testClear_borrowedItemNotReturnedToPool() {
        final PoolItem<String> poolItem1 = pool.borrow(KEY);
        pool.clear();
        pool.giveBack(poolItem1);

        final PoolItem<String> poolItem2 = pool.borrow(KEY);
        assertThat(poolItem2.getValue())
                .isNotEqualTo(poolItem1.getValue());
        assertThat(valueCreationCounter)
                .hasValue(2);
    }

    @Test
    void testBorrowReturn_notPooled() {
        final PoolItem<String> poolItem1 = pool.internalBorrowObject(KEY, false);
        pool.internalReturnObject(poolItem1, false);

        assertThat(pool.getKeys())
                .isEmpty();
        final PoolItem<String> poolItem2 = pool.borrow(KEY);
        assertThat(poolItem2.getValue())
                .isNotEqualTo(poolItem1.getValue());
        assertThat(valueCreationCounter)
                .hasValue(2);
    }


    // --------------------------------------------------------------------------------


    private class MyPoolCache extends AbstractPoolCache<String, String> {

        private volatile Consumer<String> onCreate;

        private MyPoolCache(final CacheManager cacheManager,
                            final Supplier<CacheConfig> cacheConfigSupplier) {
            super(cacheManager, CACHE_NAME, cacheConfigSupplier);
        }

        private void setOnCreate(final Consumer<String> onCreate) {
            this.onCreate = onCreate;
        }

        private PoolItem<String> borrow(final String key) {
            return internalBorrowObject(key, true);
        }

        private void giveBack(final PoolItem<String> poolItem) {
            internalReturnObject(poolItem, true);
        }

        @Override
        protected String internalCreateValue(final String key) {
            final String value = key + "-" + valueCreationCounter.incrementAndGet();
            if (onCreate != null) {
                onCreate.accept(key);
            }
            return value;
        }

        @Override
        Object mapKeyForSystemInfo(final String key) {
            return key;
        }
    }
}
