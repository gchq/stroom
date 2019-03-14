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
import stroom.entity.shared.DocumentServiceReadAction;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;


class DocumentServiceReadHandler
        extends AbstractTaskHandler<DocumentServiceReadAction<SharedObject>, SharedObject> {
    private final DocumentService documentService;
    private final DocumentEventLog documentEventLog;
    private final Security security;

    @Inject
    DocumentServiceReadHandler(final DocumentService documentService,
                               final DocumentEventLog documentEventLog,
                               final Security security) {
        this.documentService = documentService;
        this.documentEventLog = documentEventLog;
        this.security = security;
    }

    @Override
    public SharedObject exec(final DocumentServiceReadAction action) {
        return security.secureResult(() -> {
            try {
                final SharedObject doc = (SharedObject) documentService.readDocument(action.getDocRef());
                documentEventLog.view(action.getDocRef(), null);
                return doc;
            } catch (final RuntimeException e) {
                documentEventLog.view(action.getDocRef(), e);
                throw e;
            }
        });
    }
}
