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

package stroom.document.server;

import org.springframework.stereotype.Component;
import stroom.docstore.server.DocumentActionHandler;
import stroom.entity.server.EntityServiceBeanRegistry;
import stroom.entity.shared.EntityServiceException;
import stroom.query.api.v2.DocRef;

import javax.inject.Inject;

@Component
class DocumentServiceImpl implements DocumentService {
    private final EntityServiceBeanRegistry beanRegistry;

    @Inject
    public DocumentServiceImpl(final EntityServiceBeanRegistry beanRegistry) {
        this.beanRegistry = beanRegistry;
    }

    @Override
    public Object readDocument(DocRef docRef) {
        return getDocumentActionHandler(docRef.getType()).readDocument(docRef);
    }

    @Override
    public Object writeDocument(DocRef docRef, final Object document) {
        return getDocumentActionHandler(docRef.getType()).writeDocument(document);
    }

    private DocumentActionHandler getDocumentActionHandler(final String type) {
        final Object bean = beanRegistry.getEntityService(type);
        if (bean == null) {
            throw new EntityServiceException("No document action handler can be found for type '" + type + "'");
        }
        if (!(bean instanceof DocumentActionHandler)) {
            throw new EntityServiceException("Bean is not a document action handler");
        }
        return (DocumentActionHandler) bean;
    }
}
