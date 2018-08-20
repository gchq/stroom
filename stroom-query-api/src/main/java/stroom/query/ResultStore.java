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

package stroom.query;

import java.util.List;
import java.util.Map;

public class ResultStore {
    private final Map<String, List<Item>> childMap;
    private final long size;
    private final long totalSize;

    public ResultStore(final Map<String, List<Item>> childMap, final long size, final long totalSize) {
        this.childMap = childMap;
        this.size = size;
        this.totalSize = totalSize;
    }

    public Map<String, List<Item>> getChildMap() {
        return childMap;
    }

    public long getSize() {
        return size;
    }

    public long getTotalSize() {
        return totalSize;
    }
}
