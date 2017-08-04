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
import stroom.entity.shared.DocumentServiceForkAction;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedObject;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = DocumentServiceForkAction.class)
@Scope(value = StroomScope.TASK)
class DocumentServiceForkHandler extends AbstractTaskHandler<DocumentServiceForkAction<SharedObject>, SharedObject> {
    private final DocumentService documentService;

    @Inject
    DocumentServiceForkHandler(final DocumentService documentService) {
        this.documentService = documentService;
    }

    @SuppressWarnings("unchecked")
    @Override
    public SharedObject exec(final DocumentServiceForkAction action) {
//        final Object bean = beanRegistry.getEntityService(action.getEntity().getClass());
//        if (bean == null) {
//            throw new EntityServiceException("No entity service can be found");
//        }
//        if (!(bean instanceof DocumentEntityService<?>)) {
//            throw new EntityServiceException("Bean is not a document entity service");
//        }
//
//        final DocumentEntityService<DocumentEntity> entityService = (DocumentEntityService<DocumentEntity>) bean;
//        final BaseEntity entity = action.getEntity();
//
//        DocumentEntity result = null;
//
//        try {
//                // Validate the entity name.
////                if (entityService instanceof ProvidesNamePattern) {
////                    final ProvidesNamePattern providesNamePattern = (ProvidesNamePattern) entityService;
//                    NameValidationUtil.validate(entityService, entity);
////                }
//
//                result = entityService.saveAs((DocumentEntity) entity, action.getFolder(), action.getName(), action.getPermissionInheritance());
//                entityEventLog.create(result);
//        } catch (final RuntimeException e) {
//                entityEventLog.create(entity, e);
//            throw e;
//        }
//
//        return result;

        return (SharedObject) documentService.fork(action.getDocRef(), action.getDocument(), action.getDocName(), action.getDestinationFolderRef(), action.getPermissionInheritance());
    }
}
