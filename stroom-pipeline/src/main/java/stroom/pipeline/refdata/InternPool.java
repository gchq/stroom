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

package stroom.pipeline.refdata;

public class InternPool<T> {
    // WeakPool is not thread safe
    private final WeakPool<T> pool = new WeakPool<>();

    // This is only called when loading an item into the eff strm cache so the sync
    // is not a massive hit
    public synchronized T intern(final T object) {
        T res = pool.get(object);
        if (res == null) {
            pool.put(object);
            res = object;
        }
        return res;
    }

    public int size() {
        return pool.size();
    }
}
