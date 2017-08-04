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
    private final DocumentEventLog entityEventLog;
    private final EntityServiceBeanRegistry beanRegistry;

    @Inject
    public DocumentServiceImpl(final DocumentEventLog entityEventLog, final EntityServiceBeanRegistry beanRegistry) {
        this.entityEventLog = entityEventLog;
        this.beanRegistry = beanRegistry;
    }

    @Override
    public Object read(DocRef docRef) {
        Object result = null;
        try {
            final DocumentActionHandler documentActionHandler = getDocumentActionHandler(docRef.getType());
            result = documentActionHandler.read(docRef);
            if (result != null) {
                entityEventLog.view(result);
            }
        } catch (final RuntimeException e) {
            entityEventLog.view(result, e);
            throw e;
        }

        return result;
    }

    @Override
    public Object write(DocRef docRef, final Object document) {
        Object result = null;
        try {
            final DocumentActionHandler documentActionHandler = getDocumentActionHandler(docRef.getType());
            result = documentActionHandler.write(document);
            if (result != null) {
                entityEventLog.update(document, result);
            }
        } catch (final RuntimeException e) {
            entityEventLog.update(document, result, e);
            throw e;
        }

        return result;
    }

    @Override
    public Object fork(final DocRef docRef, final Object document, final String docName, final DocRef destinationFolderRef, final PermissionInheritance permissionInheritance) {
        Object result = null;
        try {
            final DocumentActionHandler documentActionHandler = getDocumentActionHandler(docRef.getType());
            result = documentActionHandler.fork(document, docName, destinationFolderRef, permissionInheritance);
            if (result != null) {
                entityEventLog.create(result);
            }
        } catch (final RuntimeException e) {
            entityEventLog.create(result, e);
            throw e;
        }

        return result;
    }

    @Override
    public void delete(final DocRef docRef) {
        try {
            final DocumentActionHandler documentActionHandler = getDocumentActionHandler(docRef.getType());
            documentActionHandler.delete(docRef);
            entityEventLog.delete(docRef);
        } catch (final RuntimeException e) {
            entityEventLog.delete(docRef, e);
            throw e;
        }
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
