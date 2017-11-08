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

public class CacheInfo implements SharedObject {
    private static final long serialVersionUID = 463047159587522512L;

    private String name;
    private String config;
    private String stats;
    private long size;

    public CacheInfo() {
        // Default constructor necessary for GWT serialisation.
    }

    public CacheInfo(final String name,
                     final String config,
                     final String stats,
                     final long size) {
        this.name = name;
        this.config = config;
        this.stats = stats;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public String getConfig() {
        return config;
    }

    public String getStats() {
        return stats;
    }

    public long getSize() {
        return size;
    }
}
