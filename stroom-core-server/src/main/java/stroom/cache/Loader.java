/*
 * Copyright 2017 Crown Copyright
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

package stroom.cache;

import org.ehcache.spi.loaderwriter.BulkCacheLoadingException;
import org.ehcache.spi.loaderwriter.BulkCacheWritingException;
import org.ehcache.spi.loaderwriter.CacheLoaderWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class Loader<K, V> implements CacheLoaderWriter<K, V> {
    /**
     * Loads a single value.
     * <p>
     * When used with a cache any exception thrown by this method will be thrown
     * back to the user as a {@link CacheLoadingException}.
     *
     * @param key the key for which to load the value
     * @return the loaded value
     * @throws Exception if the value cannot be loaded
     */
    @Override
    public V load(final K key) throws Exception {
        return null;
    }

    /**
     * Loads multiple values.
     * <p>
     * The returned {@link Map} should contain {@code null} values for the keys
     * that could not be found.
     * <p>
     * When used with a cache the mappings that will be installed are the keys as found in {@code keys}
     * mapped to the results of {@code loadAllResult.get(key)}. Any other mappings will be ignored.
     * <p>
     * By using a {@link BulkCacheLoadingException} implementors can report partial success. Any other exceptions will
     * be thrown back to the {@code Cache} user through a {@link BulkCacheLoadingException} indicating a complete failure.
     *
     * @param keys the keys to load
     *             <p>
     *             //Which null or not present?
     * @return the {@link Map Map} of values for each key passed in, where no mapping means no value to map.
     * @throws BulkCacheLoadingException in case of partial success
     * @throws Exception                 in case no values could be loaded
     */
    @Override
    public Map<K, V> loadAll(final Iterable<? extends K> keys) throws BulkCacheLoadingException, Exception {
        final Map<K, V> map = new HashMap<>();
        keys.forEach(k -> {
            try {
                final V v = load(k);
                if (v != null) {
                    map.put(k, v);
                }
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
        return map;
    }

    /**
     * Writes a single mapping.
     * <p>
     * The write may represent a brand new value or an update to an existing value.
     * <p>
     * When used with a {@code Cache} any exception thrown by this method will
     * be thrown back to the user through a {@link CacheWritingException}.
     *
     * @param key   the key to write
     * @param value the value to write
     * @throws Exception if the write operation failed
     */
    @Override
    public void write(final K key, final V value) throws Exception {

    }

    /**
     * Writes multiple mappings.
     * <p>
     * The writes may represent a mix of brand new values and updates to existing values.
     * <p>
     * By using a {@link BulkCacheWritingException} implementors can report partial success. Any other exception will
     * be thrown back to the {@code Cache} user through a {@link BulkCacheWritingException} indicating a complete failure.
     *
     * @param entries the mappings to write
     * @throws BulkCacheWritingException in case of partial success
     * @throws Exception                 in case no values could be written
     */
    @Override
    public void writeAll(final Iterable<? extends Entry<? extends K, ? extends V>> entries) throws BulkCacheWritingException, Exception {
        entries.forEach(entry -> {
            try {
                write(entry.getKey(), entry.getValue());
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    /**
     * Deletes a single mapping.
     *
     * @param key the key to delete
     * @throws Exception if the write operation failed
     */
    @Override
    public void delete(final K key) throws Exception {

    }

    /**
     * Deletes multiple mappings.
     * <p>
     * By using a {@link BulkCacheWritingException} implementors can report partial success. Any other exception will
     * be thrown back to the {@code Cache} user through a {@link BulkCacheWritingException} indicating all deletes failed.
     *
     * @param keys the keys to delete
     * @throws BulkCacheWritingException in case of partial success
     * @throws Exception                 in case no values can be loaded
     */
    @Override
    public void deleteAll(final Iterable<? extends K> keys) throws BulkCacheWritingException, Exception {
        keys.forEach(k -> {
            try {
                delete(k);
            } catch (final RuntimeException e) {
                throw e;
            } catch (final Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }
}
