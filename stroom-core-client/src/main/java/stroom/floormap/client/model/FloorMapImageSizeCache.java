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

package stroom.floormap.client.model;

import java.util.LinkedHashMap;
import java.util.Map;

/// The natural `{width, height}` of images the canvas has measured, keyed by URL, holding at
/// most a fixed number of entries and evicting the least recently used.
///
/// Image URLs come from document and fact data, so the set a long-lived canvas sees has no
/// natural bound; an unbounded map grew for as long as the tab stayed open. Evicting an entry
/// costs only a re-measure the next time that image is drawn, which the browser serves from
/// its own cache.
public final class FloorMapImageSizeCache {

    /// Far more images than one floor map draws, so in normal use nothing is ever evicted.
    static final int DEFAULT_MAX_ENTRIES = 500;

    private final Map<String, double[]> sizes;

    /// Creates a cache holding at most [#DEFAULT_MAX_ENTRIES] entries.
    public FloorMapImageSizeCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    /// Creates a cache holding at most `maxEntries` entries.
    ///
    /// @param maxEntries the most entries to hold; must be at least one
    public FloorMapImageSizeCache(final int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be at least 1");
        }
        // Access order, so a size that is read stays and one that is not is the first to go.
        this.sizes = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, double[]> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /// The measured `{width, height}` for an image, or `null` if it has not been measured or
    /// has been evicted.
    ///
    /// @param url the image URL
    /// @return the size, or `null`
    public double[] get(final String url) {
        return sizes.get(url);
    }

    /// Records the measured size of an image, evicting the least recently used entry if the cache
    /// is full.
    ///
    /// @param url  the image URL
    /// @param size the natural `{width, height}`; `{0, 0}` if the browser reported none
    public void put(final String url, final double[] size) {
        sizes.put(url, size);
    }

    /// The number of images currently held.
    ///
    /// @return the entry count
    public int size() {
        return sizes.size();
    }
}
