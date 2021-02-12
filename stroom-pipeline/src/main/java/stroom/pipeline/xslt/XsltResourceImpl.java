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

package stroom.pipeline.xslt;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.pipeline.shared.XsltDTO;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.XsltResource;
import stroom.util.rest.RestUtil;
import stroom.event.logging.rs.api.AutoLogged;

import javax.inject.Inject;
import javax.inject.Provider;

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
    public XsltDoc update(final XsltDoc doc) {
        return documentResourceHelperProvider.get()
                .update(xsltStoreProvider.get(), doc);
    }

    @Override
    public XsltDoc fetch(final String xsltId) {
        return documentResourceHelperProvider.get()
                .read(xsltStoreProvider.get(), getDocRef(xsltId));
    }

    // Used by react UI?
    @Override
    public void save(final String xsltId,
                     final XsltDTO xsltDto) {
        RestUtil.requireMatchingUuids(xsltId, xsltDto);

        final XsltDoc xsltDoc = fetch(xsltId);

        if (xsltDoc != null) {
            xsltDoc.setDescription(xsltDto.getDescription());
            xsltDoc.setData(xsltDto.getData());
            update(xsltDoc);
        }
    }

    private DocRef getDocRef(final String xsltId) {
        return DocRef.builder()
                .uuid(xsltId)
                .type(XsltDoc.DOCUMENT_TYPE)
                .build();
    }
}