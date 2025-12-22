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

package stroom.pipeline.cache;

public class MockSchemaPool implements SchemaPool {

    private final SchemaLoaderImpl schemaLoader;

    public MockSchemaPool(final SchemaLoaderImpl schemaLoader) {
        this.schemaLoader = schemaLoader;
    }

    @Override
    public PoolItem<StoredSchema> borrowObject(final SchemaKey key, final boolean usePool) {
        return new PoolItem<>(
                new PoolKey<>(key),
                schemaLoader.load(key.getSchemaLanguage(), key.getData(), key.getFindXMLSchemaCriteria()));
    }

    @Override
    public void returnObject(final PoolItem<StoredSchema> poolItem, final boolean usePool) {
    }
}
