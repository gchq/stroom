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

package stroom.cache.server;

import stroom.pool.PoolItem;

public class MockSchemaPool implements SchemaPool {
    private final SchemaLoaderImpl schemaLoader;

    public MockSchemaPool(final SchemaLoaderImpl schemaLoader) {
        this.schemaLoader = schemaLoader;
    }

    @Override
    public PoolItem<SchemaKey, StoredSchema> borrowObject(final SchemaKey key) {
        return new PoolItem<>(key, schemaLoader.load(key.getSchemaLanguage(), key.getData()));
    }

    @Override
    public void returnObject(final PoolItem<SchemaKey, StoredSchema> poolItem) {
    }
}
