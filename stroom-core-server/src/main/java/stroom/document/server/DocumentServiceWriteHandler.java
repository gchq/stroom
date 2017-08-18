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
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedObject;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = DocumentServiceWriteAction.class)
@Scope(value = StroomScope.TASK)
class DocumentServiceWriteHandler extends AbstractTaskHandler<DocumentServiceWriteAction<SharedObject>, SharedObject> {
    private final DocumentService documentService;

    @Inject
    DocumentServiceWriteHandler(final DocumentService documentService) {
        this.documentService = documentService;
    }

    @SuppressWarnings("unchecked")
    @Override
    public SharedObject exec(final DocumentServiceWriteAction action) {
//        final Object bean = beanRegistry.getEntityService(action.getEntity().getClass());
//        if (bean == null) {
//            throw new EntityServiceException("No entity service can be found");
//        }
//        if (!(bean instanceof EntityService<?>)) {
//            throw new EntityServiceException("Bean is not an entity service");
//        }
//
//        final EntityService<BaseEntity> entityService = (EntityService<BaseEntity>) bean;
//        final BaseEntity entity = action.getEntity();
//        final boolean persistent = entity.isPersistent();
//
//        BaseEntity result = null;
//
//        try {
//            if (persistent) {
//                // Get the before version.
//                final BaseEntity before = entityService.load(entity);
//
//                // Validate the entity name.
//                if (entityService instanceof ProvidesNamePattern) {
//                    final ProvidesNamePattern providesNamePattern = (ProvidesNamePattern) entityService;
//                    NameValidationUtil.validate(providesNamePattern, before, entity);
//                }
//
//                result = entityService.save(entity);
//                documentEventLog.update(before, result);
//
//            } else {
//                // Validate the entity name.
//                if (entityService instanceof ProvidesNamePattern) {
//                    final ProvidesNamePattern providesNamePattern = (ProvidesNamePattern) entityService;
//                    NameValidationUtil.validate(providesNamePattern, entity);
//                }
//
//                result = entityService.save(entity);
//                documentEventLog.create(result);
//            }
//        } catch (final RuntimeException e) {
//            if (persistent) {
//                // Get the before version.
//                documentEventLog.update(null, entity, e);
//            } else {
//                documentEventLog.create(entity, e);
//            }
//            throw e;
//        }
//
//        return result;

        return (SharedObject) documentService.writeDocument(action.getDocRef(), action.getDocument());
    }
}
