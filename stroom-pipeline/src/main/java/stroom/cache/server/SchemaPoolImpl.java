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

import javax.annotation.Resource;
import javax.inject.Inject;

import org.springframework.stereotype.Component;

import stroom.security.Insecure;
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventHandler;
import stroom.pool.AbstractPoolCacheBean;
import stroom.xmlschema.server.XMLSchemaCache;
import stroom.xmlschema.shared.XMLSchema;
import net.sf.ehcache.CacheManager;

@Insecure
@Component
@EntityEventHandler(type = XMLSchema.ENTITY_TYPE)
public class SchemaPoolImpl extends AbstractPoolCacheBean<SchemaKey, StoredSchema>
        implements SchemaPool, EntityEvent.Handler {
    private final SchemaLoader schemaLoader;

    @Inject
    public SchemaPoolImpl(final CacheManager cacheManager, final SchemaLoader schemaLoader) {
        super(cacheManager, "Schema Pool");
        this.schemaLoader = schemaLoader;
    }

    @Override
    protected StoredSchema createValue(final SchemaKey key) {
        return schemaLoader.load(key.getSchemaLanguage(), key.getData());
    }

    /**
     * We will clear the schema pool if there are any changes to any schemas.
     */
    @Override
    public void onChange(final EntityEvent event) {
        clear();
    }

    @Resource
    public void setXMLSchemaCache(final XMLSchemaCache xmlSchemaCache) {
        xmlSchemaCache.addClearHandler(() -> clear());
    }
}
