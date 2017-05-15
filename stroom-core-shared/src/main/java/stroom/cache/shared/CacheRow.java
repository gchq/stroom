/*
 * Copyright 2016 Crown Copyright
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

package stroom.cache.shared;

import stroom.util.shared.SharedObject;

public class CacheRow implements SharedObject {
    public static final String MANAGE_CACHE_PERMISSION = "Manage Cache";
    private static final long serialVersionUID = -7367500560554774611L;
    private String cacheName;

    public CacheRow() {
        // Default constructor necessary for GWT serialisation.
    }

    public CacheRow(final String cacheName) {
        this.cacheName = cacheName;
    }

    public String getCacheName() {
        return cacheName;
    }

    @Override
    public int hashCode() {
        return cacheName.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof CacheRow)) {
            return false;
        }

        return ((CacheRow) obj).cacheName.equals(cacheName);
    }
}
