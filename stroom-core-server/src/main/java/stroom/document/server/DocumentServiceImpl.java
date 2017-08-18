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
import stroom.dashboard.server.logging.DocumentEventLog;
import stroom.entity.server.EntityServiceBeanRegistry;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.PermissionInheritance;
import stroom.query.api.v1.DocRef;

import javax.inject.Inject;

@Component
class DocumentServiceImpl implements DocumentService {
    private final DocumentEventLog documentEventLog;
    private final EntityServiceBeanRegistry beanRegistry;

    @Inject
    public DocumentServiceImpl(final DocumentEventLog documentEventLog, final EntityServiceBeanRegistry beanRegistry) {
        this.documentEventLog = documentEventLog;
        this.beanRegistry = beanRegistry;
    }

    @Override
    public Object readDocument(DocRef docRef) {
        Object result = null;
        try {
            final DocumentActionHandler documentActionHandler = getDocumentActionHandler(docRef.getType());
            result = documentActionHandler.readDocument(docRef);
            if (result != null) {
                documentEventLog.view(result);
            }
        } catch (final RuntimeException e) {
            documentEventLog.view(result, e);
            throw e;
        }

        return result;
    }

    @Override
    public Object writeDocument(DocRef docRef, final Object document) {
        Object result = null;
        try {
            final DocumentActionHandler documentActionHandler = getDocumentActionHandler(docRef.getType());
            result = documentActionHandler.writeDocument(document);
            if (result != null) {
                documentEventLog.update(document, result, null);
            }
        } catch (final RuntimeException e) {
            documentEventLog.update(document, result, e);
            throw e;
        }

        return result;
    }

    @Override
    public Object forkDocument(final DocRef docRef, final Object document, final String docName, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        Object result = null;
        try {
            final DocumentActionHandler documentActionHandler = getDocumentActionHandler(docRef.getType());
            result = documentActionHandler.forkDocument(document, docName, destinationFolderRef);
            if (result != null) {
                documentEventLog.create(result, null);
            }
        } catch (final RuntimeException e) {
            documentEventLog.create(result, e);
            throw e;
        }

        // TODO : Tell the explorer service that this document has been forked so that it can create an entry and setup permissions.

        return result;
    }

    @Override
    public void deleteDocument(final DocRef docRef) {
//        try {
//            final DocumentActionHandler documentActionHandler = getDocumentActionHandler(docRef.getType());
//            documentActionHandler.deleteDocument(docRef);
//            documentEventLog.delete(docRef, null);
//        } catch (final RuntimeException e) {
//            documentEventLog.delete(docRef, e);
//            throw e;
//        }

        throw new EntityServiceException("Not implemented");
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
