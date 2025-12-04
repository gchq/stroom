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

package stroom.pipeline.filter;

import stroom.pipeline.xmlschema.FindXMLSchemaCriteria;
import stroom.pipeline.xmlschema.XmlSchemaCache;
import stroom.pipeline.xmlschema.XmlSchemaCache.SchemaSet;
import stroom.xmlschema.shared.XmlSchemaDoc;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import java.util.List;

public class LSResourceResolverImpl implements LSResourceResolver {

    private final XmlSchemaCache xmlSchemaCache;
    private final FindXMLSchemaCriteria schemaConstraint;

    public LSResourceResolverImpl(final XmlSchemaCache xmlSchemaCache,
                                  final FindXMLSchemaCriteria schemaConstraint) {
        this.xmlSchemaCache = xmlSchemaCache;
        this.schemaConstraint = schemaConstraint;
    }

    @Override
    public LSInput resolveResource(final String type,
                                   final String namespaceURI,
                                   final String publicId,
                                   final String systemId,
                                   final String baseURI) {
        // Try and get a schema using the constraints of the calling schema filter.
        XmlSchemaDoc xmlSchema = getConstrainedMatch(systemId, namespaceURI);

        // If we couldn't find a schema then look at all available schemas.
        if (xmlSchema == null) {
            final FindXMLSchemaCriteria findXMLSchemaCriteria = new FindXMLSchemaCriteria();
            findXMLSchemaCriteria.setUserRef(schemaConstraint.getUserRef());
            final SchemaSet allSchemas = xmlSchemaCache.getSchemaSet(findXMLSchemaCriteria);
            xmlSchema = allSchemas.getBestMatch(systemId, namespaceURI);
        }

        if (xmlSchema != null && xmlSchema.getData() != null) {
            return new LSInputImpl(xmlSchema.getData(), systemId, publicId, baseURI);
        }

        return null;
    }

    private XmlSchemaDoc getConstrainedMatch(final String systemId,
                                             final String namespaceURI) {
        final SchemaSet schemaSet = xmlSchemaCache.getSchemaSet(schemaConstraint);
        schemaSet.getBestMatch(systemId, namespaceURI);

        // Try and find a matching schema by system id. The constraints applied mean there should be only one match.
        List<XmlSchemaDoc> matches = schemaSet.getSchemaBySystemId(systemId);
        if (matches != null) {
            if (matches.size() > 1) {
                throw new RuntimeException("More than one schema found for system id '" + systemId + "'");
            } else if (matches.size() == 1) {
                return matches.get(0);
            }
        }

        // If not found try and match with namespace URI.
        matches = schemaSet.getSchemaByNamespaceURI(namespaceURI);
        if (matches != null && matches.size() > 0) {
            return matches.get(0);
        }

        return null;
    }
}
