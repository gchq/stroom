/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.pipeline.xmlschema;

import stroom.security.api.SecurityContext;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.xmlschema.shared.XmlSchemaDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@EntityEventHandler(type = XmlSchemaDoc.TYPE)
public class XmlSchemaCache implements EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(XmlSchemaCache.class);

    private final XmlSchemaStore xmlSchemaStore;
    private final List<ClearHandler> clearHandlers = new ArrayList<>();
    private final Map<FindXMLSchemaCriteria, SchemaSet> schemaSets = new ConcurrentHashMap<>();
    private final SecurityContext securityContext;

    @Inject
    public XmlSchemaCache(final XmlSchemaStore xmlSchemaStore,
                          final SecurityContext securityContext) {
        this.xmlSchemaStore = xmlSchemaStore;
        this.securityContext = securityContext;
    }

    /**
     * We will clear the schema cache if there are any changes to the schema.
     */
    @Override
    public void onChange(final EntityEvent event) {
        clear();
    }

    void clear() {
        LOGGER.debug("Clearing XML schema cache");
        schemaSets.clear();

        for (final ClearHandler clearHandler : clearHandlers) {
            try {
                clearHandler.onClear();
            } catch (final RuntimeException e) {
                LOGGER.error("Unable to clear cache!", e);
            }
        }
    }

    public SchemaSet getSchemaSet(final FindXMLSchemaCriteria criteria) {
        return securityContext.asProcessingUserResult(() -> {
            SchemaSet schemaSet = schemaSets.get(criteria);
            if (schemaSet == null) {
                try {
                    final Map<String, List<XmlSchemaDoc>> schemaNameMap = new HashMap<>();
                    final Map<String, List<XmlSchemaDoc>> schemaNamespaceURIMap = new HashMap<>();
                    final Map<String, List<XmlSchemaDoc>> schemaSystemIdMap = new HashMap<>();
                    final List<String> systemIdList = new ArrayList<>();

                    // Get a list of matching schemas.
                    final List<XmlSchemaDoc> schemas = xmlSchemaStore.find(criteria).getValues();
                    schemas.forEach(schema -> {
                        addToMap(schemaNameMap, schema.getName(), schema);
                        addToMap(schemaNamespaceURIMap, schema.getNamespaceURI(), schema);
                        addToMap(schemaSystemIdMap, schema.getSystemId(), schema);

                        if (schema.getSystemId() != null) {
                            final String systemId = schema.getSystemId().trim();
                            if (!systemId.isEmpty()) {
                                systemIdList.add(systemId);
                            }
                        }
                    });

                    // Create location string.
                    Collections.sort(systemIdList);
                    final StringBuilder sb = new StringBuilder();
                    for (final String systemId : systemIdList) {
                        sb.append(systemId);
                        sb.append("\n");
                    }
                    if (!sb.isEmpty()) {
                        sb.setLength(sb.length() - 1);
                    }
                    final String locations = sb.toString();

                    schemaSet = new SchemaSet(schemaNameMap, schemaNamespaceURIMap, schemaSystemIdMap, locations);

                    // Cache this info for future use.

                    // There was a memory leak in XMLSchemaCache due
                    // to schema criteria map keys not implementing equals properly.
                    // This has been fixed but has highlighted the issue that a
                    // proper cache should be used instead of a map to allow
                    // this to scale better.
                    schemaSets.put(criteria, schemaSet);

                    if (schemaSets.size() > 100) {
                        LOGGER.error("Too many schema sets.");

                        while (schemaSets.size() > 50) {
                            schemaSets.remove(schemaSets.keySet().iterator().next());
                        }
                    }

                } catch (final RuntimeException e) {
                    LOGGER.error("Unable to get schema set!", e);
                }
            }

            return schemaSet;
        });
    }

    private void addToMap(final Map<String, List<XmlSchemaDoc>> map, final String name, final XmlSchemaDoc xmlSchema) {
        map.computeIfAbsent(name, k -> new ArrayList<>()).add(xmlSchema);
    }

    public void addClearHandler(final ClearHandler clearHandler) {
        clearHandlers.add(clearHandler);
    }

    public interface ClearHandler {

        void onClear();
    }

    public static class SchemaSet {

        private final Map<String, List<XmlSchemaDoc>> schemaNameMap;
        private final Map<String, List<XmlSchemaDoc>> schemaNamespaceURIMap;
        private final Map<String, List<XmlSchemaDoc>> schemaSystemIdMap;
        private final String locations;

        SchemaSet(final Map<String, List<XmlSchemaDoc>> schemaNameMap,
                  final Map<String, List<XmlSchemaDoc>> schemaNamespaceURIMap,
                  final Map<String, List<XmlSchemaDoc>> schemaSystemIdMap, final String locations) {
            this.schemaNameMap = schemaNameMap;
            this.schemaNamespaceURIMap = schemaNamespaceURIMap;
            this.schemaSystemIdMap = schemaSystemIdMap;
            this.locations = locations;
        }

        public String getLocations() {
            return locations;
        }

        public List<XmlSchemaDoc> getSchemaByName(final String name) {
            return schemaNameMap.get(name);
        }

        public List<XmlSchemaDoc> getSchemaBySystemId(final String systemId) {
            return schemaSystemIdMap.get(systemId);
        }

        public List<XmlSchemaDoc> getSchemaByNamespaceURI(final String namespaceURI) {
            return schemaNamespaceURIMap.get(namespaceURI);
        }

        public XmlSchemaDoc getBestMatch(final String systemId, final String namespaceURI) {
            // Try and find a matching schema by system id.
            List<XmlSchemaDoc> matches = schemaSystemIdMap.get(systemId);
            if (matches != null && !matches.isEmpty()) {
                return matches.getFirst();
            }

            // If not found try and match with namespace URI.
            matches = schemaNamespaceURIMap.get(namespaceURI);
            if (matches != null && !matches.isEmpty()) {
                return matches.getFirst();
            }

            return null;
        }
    }
}
