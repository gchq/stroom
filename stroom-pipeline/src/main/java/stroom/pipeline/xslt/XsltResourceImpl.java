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

package stroom.pipeline.xslt;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.XsltResource;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class XsltResourceImpl implements XsltResource {

    private final Provider<XsltStore> xsltStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;

    @Inject
    XsltResourceImpl(final Provider<XsltStore> xsltStoreProvider,
                     final Provider<DocumentResourceHelper> documentResourceHelperProvider) {
        this.xsltStoreProvider = xsltStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
    }

    @Override
    public XsltDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(xsltStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public XsltDoc update(final String uuid, final XsltDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(xsltStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(XsltDoc.TYPE)
                .build();
    }
}
