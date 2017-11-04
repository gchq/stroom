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

import org.springframework.stereotype.Component;
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventHandler;
import stroom.pool.AbstractPoolCache;
import stroom.pool.PoolItem;
import stroom.security.Insecure;
import stroom.security.SecurityContext;
import stroom.util.cache.CentralCacheManager;
import stroom.util.task.ServerTask;
import stroom.xmlschema.server.XMLSchemaCache;
import stroom.xmlschema.shared.XMLSchema;

import javax.inject.Inject;

@Insecure
@Component
@EntityEventHandler(type = XMLSchema.ENTITY_TYPE)
class SchemaPoolImpl extends AbstractPoolCache<SchemaKey, StoredSchema>
        implements SchemaPool, EntityEvent.Handler {
    private final SchemaLoader schemaLoader;
    private final SecurityContext securityContext;

    @Inject
    SchemaPoolImpl(final CentralCacheManager cacheManager, final SchemaLoader schemaLoader, final XMLSchemaCache xmlSchemaCache, final SecurityContext securityContext) {
        super(cacheManager, "Schema Pool");
        this.schemaLoader = schemaLoader;
        this.securityContext = securityContext;
        xmlSchemaCache.addClearHandler(this::clear);
    }

    @Override
    public PoolItem<StoredSchema> borrowObject(final SchemaKey key, final boolean usePool) {
        return internalBorrowObject(key, usePool);
    }

    @Override
    public void returnObject(final PoolItem<StoredSchema> poolItem, final boolean usePool) {
        internalReturnObject(poolItem, usePool);
    }

    @Override
    protected StoredSchema internalCreateValue(final Object key) {
        securityContext.pushUser(ServerTask.INTERNAL_PROCESSING_USER_TOKEN);
        try {
            final SchemaKey schemaKey = (SchemaKey) key;
            return schemaLoader.load(schemaKey.getSchemaLanguage(), schemaKey.getData(), schemaKey.getFindXMLSchemaCriteria());
        } finally {
            securityContext.popUser();
        }
    }

    /**
     * We will clear the schema pool if there are any changes to any schemas.
     */
    @Override
    public void onChange(final EntityEvent event) {
        clear();
    }
}
