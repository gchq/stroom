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

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

public class WeakPool<T> {
    private final WeakHashMap<T, WeakReference<T>> pool = new WeakHashMap<>();

    public T get(final T object) {
        final WeakReference<T> ref = pool.get(object);
        if (ref != null) {
            return ref.get();
        }

        return null;
    }

    public void put(final T object) {
        pool.put(object, new WeakReference<>(object));
    }

    public int size() {
        return pool.size();
    }
}
