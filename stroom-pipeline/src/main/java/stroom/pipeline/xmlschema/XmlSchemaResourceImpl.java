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

package stroom.pipeline.xmlschema;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.util.shared.EntityServiceException;
import stroom.xmlschema.shared.XmlSchemaDoc;
import stroom.xmlschema.shared.XmlSchemaResource;
import stroom.xmlschema.shared.XmlSchemaValidationResponse;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

@AutoLogged
class XmlSchemaResourceImpl implements XmlSchemaResource {

    private final Provider<XmlSchemaStore> xmlSchemaStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    XmlSchemaResourceImpl(final Provider<XmlSchemaStore> xmlSchemaStoreProvider,
                          final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.xmlSchemaStoreProvider = xmlSchemaStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public XmlSchemaDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(xmlSchemaStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public XmlSchemaDoc update(final String uuid, final XmlSchemaDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(xmlSchemaStoreProvider.get(), doc);
    }

    @Override
    public XmlSchemaValidationResponse validate(final String schemaData) {
        try {
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.newSchema(new StreamSource(new StringReader(schemaData)));
            return new XmlSchemaValidationResponse(true, null);
        } catch (final Exception e) {
            return new XmlSchemaValidationResponse(false, e.getMessage());
        }
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(XmlSchemaDoc.TYPE)
                .build();
    }
}
