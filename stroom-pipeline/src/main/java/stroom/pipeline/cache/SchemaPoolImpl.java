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

import stroom.cache.api.CacheManager;
import stroom.pipeline.filter.XmlSchemaConfig;
import stroom.pipeline.xmlschema.FindXMLSchemaCriteria;
import stroom.pipeline.xmlschema.XmlSchemaCache;
import stroom.security.api.SecurityContext;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.xmlschema.shared.XmlSchemaDoc;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
@EntityEventHandler(
        type = XmlSchemaDoc.TYPE,
        action = {EntityAction.DELETE, EntityAction.UPDATE, EntityAction.CLEAR_CACHE})
class SchemaPoolImpl extends AbstractPoolCache<SchemaKey, StoredSchema>
        implements SchemaPool, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SchemaPoolImpl.class);

    private final SchemaLoader schemaLoader;
    private final SecurityContext securityContext;

    @Inject
    SchemaPoolImpl(final CacheManager cacheManager,
                   final Provider<XmlSchemaConfig> xmlSchemaConfigProvider,
                   final SchemaLoader schemaLoader,
                   final XmlSchemaCache xmlSchemaCache,
                   final SecurityContext securityContext) {
        super(cacheManager, "Schema Pool", () -> xmlSchemaConfigProvider.get().getCacheConfig());
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
    protected StoredSchema internalCreateValue(final SchemaKey key) {
        LOGGER.trace(() -> "internalCreateValue " + key);
        return securityContext.asProcessingUserResult(() ->
                schemaLoader.load(key.getSchemaLanguage(), key.getData(), key.getFindXMLSchemaCriteria()));
    }

    /**
     * We will clear the schema pool if there are any changes to any schemas.
     */
    @Override
    public void onChange(final EntityEvent event) {
        clear();
    }

    @Override
    Object mapKeyForSystemInfo(final SchemaKey key) {
        if (key == null) {
            return null;
        } else {
            final FindXMLSchemaCriteria schemaCriteria = key.getFindXMLSchemaCriteria();
            final Builder<Object, Object> builder = ImmutableMap.builder();

            NullSafe.consume(schemaCriteria,
                    FindXMLSchemaCriteria::getNamespaceURI,
                    str -> builder.put("namespaceURI", str));
            NullSafe.consume(schemaCriteria,
                    FindXMLSchemaCriteria::getSystemId,
                    str -> builder.put("systemId", str));
            NullSafe.consume(schemaCriteria,
                    FindXMLSchemaCriteria::getSchemaGroup,
                    str -> builder.put("schemaGroup", str));
            NullSafe.consume(schemaCriteria,
                    FindXMLSchemaCriteria::getUserRef,
                    str -> builder.put("userRef", str));

            return builder.build();
        }
    }
}
