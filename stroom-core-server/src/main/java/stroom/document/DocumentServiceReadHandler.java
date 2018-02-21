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

package stroom.document;

import stroom.entity.shared.DocumentServiceReadAction;
import stroom.logging.DocumentEventLog;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.SharedObject;

import javax.inject.Inject;

@TaskHandlerBean(task = DocumentServiceReadAction.class)
class DocumentServiceReadHandler
        extends AbstractTaskHandler<DocumentServiceReadAction<SharedObject>, SharedObject> {
    private final DocumentService documentService;
    private final DocumentEventLog documentEventLog;

    @Inject
    DocumentServiceReadHandler(final DocumentService documentService,
                               final DocumentEventLog documentEventLog) {
        this.documentService = documentService;
        this.documentEventLog = documentEventLog;
    }

    @Override
    public SharedObject exec(final DocumentServiceReadAction action) {

//        BaseEntity result = null;
//
//        try {
//            final DocRef docRef = action.getDocRef();
//            if (docRef != null && docRef.getType() != null && docRef.getType().length() > 0) {
//                if (docRef.getUuid() != null && docRef.getUuid().length() > 0) {
//                    result = entityService.loadByUuid(docRef.getType(), docRef.getUuid(), action.getFetchSet());
//                }
//
//                if (result == null && docRef.getId() != null) {
//                    result = entityService.loadById(docRef.getType(), docRef.getId(), action.getFetchSet());
//                }
//            }
//
//            if (result != null) {
//                documentEventLog.view(result);
//            }
//        } catch (final RuntimeException e) {
//            throw e;
//        }
//
//        return result;

        try {
            final SharedObject doc = (SharedObject) documentService.readDocument(action.getDocRef());
            documentEventLog.view(action.getDocRef(), null);
            return doc;
        } catch (final RuntimeException e) {
            documentEventLog.view(action.getDocRef(), e);
            throw e;
        }
    }
}
