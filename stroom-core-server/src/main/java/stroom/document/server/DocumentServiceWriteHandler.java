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

import org.springframework.context.annotation.Scope;
import stroom.entity.shared.DocumentServiceWriteAction;
import stroom.logging.DocumentEventLog;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedObject;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = DocumentServiceWriteAction.class)
@Scope(value = StroomScope.TASK)
class DocumentServiceWriteHandler extends AbstractTaskHandler<DocumentServiceWriteAction<SharedObject>, SharedObject> {
    private final DocumentService documentService;
    private final DocumentEventLog documentEventLog;

    @Inject
    DocumentServiceWriteHandler(final DocumentService documentService, final DocumentEventLog documentEventLog) {
        this.documentService = documentService;
        this.documentEventLog = documentEventLog;
    }

    @Override
    public SharedObject exec(final DocumentServiceWriteAction action) {
        try {
            final SharedObject before = (SharedObject) documentService.readDocument(action.getDocRef());
            final SharedObject after = (SharedObject) documentService.writeDocument(action.getDocRef(), action.getDocument());
            documentEventLog.update(before, after, null);
            return after;
        } catch (final RuntimeException e) {
            documentEventLog.update(action.getDocRef(), null, e);
            throw e;
        }
    }
}
