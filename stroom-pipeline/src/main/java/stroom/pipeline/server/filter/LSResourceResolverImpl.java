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

package stroom.pipeline.server.filter;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import stroom.xmlschema.server.XMLSchemaCache;
import stroom.xmlschema.server.XMLSchemaCache.SchemaSet;
import stroom.xmlschema.shared.XMLSchema;

public class LSResourceResolverImpl implements LSResourceResolver {
    private final XMLSchemaCache xmlSchemaCache;

    public LSResourceResolverImpl(final XMLSchemaCache xmlSchemaCache) {
        this.xmlSchemaCache = xmlSchemaCache;
    }

    @Override
    public LSInput resolveResource(final String type, final String namespaceURI, final String publicId,
            final String systemId, final String baseURI) {
        final SchemaSet allSchemas = xmlSchemaCache.getAllSchemas();
        final XMLSchema xmlSchema = allSchemas.getBestMatch(systemId, namespaceURI);

        if (xmlSchema != null && xmlSchema.getData() != null) {
            return new LSInputImpl(xmlSchema.getData(), systemId, publicId, baseURI);
        }

        return null;
    }
}
