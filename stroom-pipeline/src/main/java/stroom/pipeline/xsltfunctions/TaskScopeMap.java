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

package stroom.pipeline.xsltfunctions;

import stroom.util.pipeline.scope.PipelineScoped;

import java.util.HashMap;
import java.util.Map;

/**
 * Backs the {@code stroom:put} and {@code stroom:get} XSLT functions. One map per pipeline scope, so a
 * {@code get} sees every {@code put} made earlier in the same task.
 * <p>
 * {@link #snapshot()}, {@link #restore} and {@link #clear()} exist for pipeline stepping only and are never
 * called on the normal processing path. Stepping scopes the map to the current <b>record</b> - it clears the
 * map after capturing each record, and a reprocess restores the captured map before replaying a record so
 * that a {@code get} below an edit still sees what the elements above it put, even though those elements are
 * deliberately not re-run. See {@code stepping-design.md} §11.
 */
@PipelineScoped
public class TaskScopeMap {
    private final Map<String, String> map = new HashMap<>();

    void put(final String key, final String value) {
        map.put(key, value);
    }

    String get(final String key) {
        return map.get(key);
    }

    /**
     * @return a detached copy of the current contents, safe to hold while processing continues.
     */
    public Map<String, String> snapshot() {
        return new HashMap<>(map);
    }

    /**
     * Replace the contents with {@code values} (a null or empty map just clears).
     */
    public void restore(final Map<String, String> values) {
        map.clear();
        if (values != null) {
            map.putAll(values);
        }
    }

    public void clear() {
        map.clear();
    }
}
