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

package stroom.externaldatasource.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.externaldatasource.shared.ExternalDataSourceDoc;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.function.Supplier;

/// Caches the rows read from the data held in each document, such as CSV or an Excel sheet, so a
/// dashboard refresh does not read the data again.
///
/// A table is cached under the document's version and a key standing for what its rows are read
/// from: the data and the settings for reading it. A save gives a document a new version, but an
/// import keeps the version in the pack, so the data itself must be part of the key. A document with no
/// version has not been saved, and is read each time.
@Singleton
class DataTableCache implements Clearable {

    private static final String CACHE_NAME = "External Data Source Table Cache";

    private final StroomCache<Key, DataTable> cache;

    /// Creates the cache. Guice supplies every argument.
    @Inject
    DataTableCache(final CacheManager cacheManager,
                   final Provider<ExternalDataSourceConfig> configProvider) {
        this.cache = cacheManager.create(CACHE_NAME, () -> configProvider.get().getTableCache());
    }

    /// Gets the rows of a document's data, reading them if they are not cached.
    ///
    /// @param doc     An External Data Source holding data.
    /// @param dataKey Stands for the data and the settings for reading it, e.g. their lengths and hashes.
    /// @param maxRows The most rows to keep after the header. Tables are cached per row limit.
    /// @param reader  Reads the rows.
    /// @return The rows.
    /// @throws IllegalArgumentException If the data cannot be read.
    DataTable get(final ExternalDataSourceDoc doc,
                  final Object dataKey,
                  final int maxRows,
                  final Supplier<DataTable> reader) {
        if (doc.getVersion() == null) {
            return reader.get();
        }
        return cache.get(new Key(doc.getUuid(), doc.getVersion(), dataKey, maxRows), key -> reader.get());
    }

    @Override
    public void clear() {
        cache.clear();
    }


    // --------------------------------------------------------------------------------


    private record Key(String uuid, String version, Object dataKey, int maxRows) {

    }
}
