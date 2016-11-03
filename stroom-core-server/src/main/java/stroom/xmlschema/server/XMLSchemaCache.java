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

package stroom.xmlschema.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import stroom.util.logging.StroomLogger;
import org.springframework.stereotype.Component;

import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventHandler;
import stroom.xmlschema.shared.FindXMLSchemaCriteria;
import stroom.xmlschema.shared.XMLSchema;
import stroom.xmlschema.shared.XMLSchemaService;

@Component
@EntityEventHandler(type = XMLSchema.ENTITY_TYPE)
public class XMLSchemaCache implements EntityEvent.Handler {
    public interface ClearHandler {
        void onClear();
    }

    public static class SchemaSet {
        private final Map<String, List<XMLSchema>> schemaNameMap;
        private final Map<String, List<XMLSchema>> schemaNamespaceURIMap;
        private final Map<String, List<XMLSchema>> schemaSystemIdMap;
        private final String locations;

        public SchemaSet(final Map<String, List<XMLSchema>> schemaNameMap,
                final Map<String, List<XMLSchema>> schemaNamespaceURIMap,
                final Map<String, List<XMLSchema>> schemaSystemIdMap, final String locations) {
            this.schemaNameMap = schemaNameMap;
            this.schemaNamespaceURIMap = schemaNamespaceURIMap;
            this.schemaSystemIdMap = schemaSystemIdMap;
            this.locations = locations;
        }

        public String getLocations() {
            return locations;
        }

        public List<XMLSchema> getSchemaByName(final String name) {
            return schemaNameMap.get(name);
        }

        public List<XMLSchema> getSchemaBySystemId(final String systemId) {
            return schemaSystemIdMap.get(systemId);
        }

        public List<XMLSchema> getSchemaByNamespaceURI(final String namespaceURI) {
            return schemaNamespaceURIMap.get(namespaceURI);
        }

        public XMLSchema getBestMatch(final String systemId, final String namespaceURI) {
            // Try and find a matching schema by system id.
            List<XMLSchema> matches = schemaSystemIdMap.get(systemId);
            if (matches != null && matches.size() > 0) {
                return matches.get(0);
            }

            // If not found try and match with namespace URI.
            matches = schemaNamespaceURIMap.get(namespaceURI);
            if (matches != null && matches.size() > 0) {
                return matches.get(0);
            }

            return null;
        }
    }

    private static final StroomLogger LOGGER = StroomLogger.getLogger(XMLSchemaCache.class);
    private static final long TEN_MINUTES = 1000 * 60 * 10;

    private static final FindXMLSchemaCriteria ALL = new FindXMLSchemaCriteria();

    private final XMLSchemaService xmlSchemaService;

    private final List<ClearHandler> clearHandlers = new ArrayList<ClearHandler>();
    private volatile long lastClearTime;

    private final Map<FindXMLSchemaCriteria, SchemaSet> schemaSets = new ConcurrentHashMap<FindXMLSchemaCriteria, SchemaSet>();

    @Inject
    public XMLSchemaCache(final XMLSchemaService xmlSchemaService) {
        this.xmlSchemaService = xmlSchemaService;
        lastClearTime = System.currentTimeMillis();
    }

    /**
     * We will clear the schema cache if there are any changes to the schema.
     */
    @Override
    public void onChange(final EntityEvent event) {
        clear();
    }

    private void clear() {
        LOGGER.debug("Clearing XML schema cache");
        schemaSets.clear();

        for (final ClearHandler clearHandler : clearHandlers) {
            try {
                clearHandler.onClear();
            } catch (final Exception e) {
                LOGGER.error(e, e);
            }
        }
    }

    public SchemaSet getAllSchemas() {
        return getSchemaSet(ALL);
    }

    public SchemaSet getSchemaSet(final FindXMLSchemaCriteria criteria) {
        SchemaSet schemaSet = schemaSets.get(criteria);
        if (schemaSet == null) {
            try {
                // Get a list of matching schemas.
                final List<XMLSchema> xmlSchemas = xmlSchemaService.find(criteria);

                final Map<String, List<XMLSchema>> schemaNameMap = new HashMap<String, List<XMLSchema>>();
                final Map<String, List<XMLSchema>> schemaNamespaceURIMap = new HashMap<String, List<XMLSchema>>();
                final Map<String, List<XMLSchema>> schemaSystemIdMap = new HashMap<String, List<XMLSchema>>();
                final List<String> systemIdList = new ArrayList<String>(xmlSchemas.size());

                for (final XMLSchema xmlSchema : xmlSchemas) {
                    addToMap(schemaNameMap, xmlSchema.getName(), xmlSchema);
                    addToMap(schemaNamespaceURIMap, xmlSchema.getNamespaceURI(), xmlSchema);
                    addToMap(schemaSystemIdMap, xmlSchema.getSystemId(), xmlSchema);

                    if (xmlSchema.getSystemId() != null) {
                        final String systemId = xmlSchema.getSystemId().trim();
                        if (systemId.length() > 0) {
                            systemIdList.add(systemId);
                        }
                    }
                }

                // Create location string.
                Collections.sort(systemIdList);
                final StringBuilder sb = new StringBuilder();
                for (final String systemId : systemIdList) {
                    sb.append(systemId);
                    sb.append("\n");
                }
                if (sb.length() > 0) {
                    sb.setLength(sb.length() - 1);
                }
                final String locations = sb.toString();

                schemaSet = new SchemaSet(schemaNameMap, schemaNamespaceURIMap, schemaSystemIdMap, locations);

                // Cache this info for future use.

                // There was a memory leak in XMLSchemaCache due
                // to schema criteria map keys not implementing equals properly.
                // This has been fixed but has highlighted the issue that a
                // proper ehcache cache should be used instead of a map to allow
                // this to scale better.
                schemaSets.put(criteria, schemaSet);

                if (schemaSets.size() > 100) {
                    LOGGER.error("Too many schema sets.");

                    while (schemaSets.size() > 50) {
                        schemaSets.remove(schemaSets.keySet().iterator().next());
                    }
                }

            } catch (final Exception e) {
                LOGGER.error(e, e);
            }
        }

        clearIfOld();

        return schemaSet;
    }

    private void addToMap(final Map<String, List<XMLSchema>> map, final String name, final XMLSchema xmlSchema) {
        List<XMLSchema> list = map.get(name);
        if (list == null) {
            list = new ArrayList<>();
            map.put(name, list);
        }
        list.add(xmlSchema);
    }

    private void clearIfOld() {
        // If the cache is more than 10 minutes old then clear it for the next
        // request to rebuild it.
        if (lastClearTime < System.currentTimeMillis() - TEN_MINUTES) {
            clear();
            lastClearTime = System.currentTimeMillis();
        }
    }

    public void addClearHandler(final ClearHandler clearHandler) {
        clearHandlers.add(clearHandler);
    }
}
