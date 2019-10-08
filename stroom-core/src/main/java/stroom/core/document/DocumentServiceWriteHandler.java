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

package stroom.core.document;

import stroom.docref.SharedObject;
import stroom.entity.shared.DocumentServiceWriteAction;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;

class DocumentServiceWriteHandler extends AbstractTaskHandler<DocumentServiceWriteAction<SharedObject>, SharedObject> {
    private final DocumentService documentService;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;

    @Inject
    DocumentServiceWriteHandler(final DocumentService documentService,
                                final DocumentEventLog documentEventLog,
                                final SecurityContext securityContext) {
        this.documentService = documentService;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
    }

    @SuppressWarnings("unchecked")
    @Override
    public SharedObject exec(final DocumentServiceWriteAction action) {
        return securityContext.secureResult(() -> {
            try {
                final SharedObject before = (SharedObject) documentService.readDocument(action.getDocRef());
                final SharedObject after = (SharedObject) documentService.writeDocument(action.getDocRef(), action.getDocument());
                documentEventLog.update(before, after, null);
                return after;
            } catch (final RuntimeException e) {
                documentEventLog.update(null, action.getDocRef(), e);
                throw e;
            }
        });
    }
}
