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
 */

package stroom.pipeline.xmlschema;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.util.shared.AutoLogged;
import stroom.xmlschema.shared.XmlSchemaDoc;
import stroom.xmlschema.shared.XmlSchemaResource;

import javax.inject.Inject;
import javax.inject.Provider;

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
        return documentResourceHelperProvider.get().read(
                xmlSchemaStoreProvider.get(),
                DocRef.builder()
                        .uuid(uuid)
                        .type(XmlSchemaDoc.DOCUMENT_TYPE)
                        .build());
    }

    @Override
    public XmlSchemaDoc update(final XmlSchemaDoc doc) {
        return documentResourceHelperProvider.get().update(xmlSchemaStoreProvider.get(), doc);
    }
}