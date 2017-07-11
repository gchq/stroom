/*
 * Copyright 2016 Crown Copyright
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

package stroom.entity.server;

import org.springframework.context.annotation.Scope;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.DocumentEntityService;
import stroom.entity.shared.EntityServiceCopyAction;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderService;
import stroom.logging.EntityEventLog;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.Collections;

@TaskHandlerBean(task = EntityServiceCopyAction.class)
@Scope(value = StroomScope.TASK)
class EntityServiceCopyHandler
        extends AbstractTaskHandler<EntityServiceCopyAction<DocumentEntity>, DocumentEntity> {
    private final FolderService folderService;
    private final EntityServiceBeanRegistry beanRegistry;
    private final EntityEventLog entityEventLog;

    @Inject
    EntityServiceCopyHandler(final FolderService folderService, final EntityServiceBeanRegistry beanRegistry, final EntityEventLog entityEventLog) {
        this.folderService = folderService;
        this.beanRegistry = beanRegistry;
        this.entityEventLog = entityEventLog;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DocumentEntity exec(final EntityServiceCopyAction<DocumentEntity> action) {
        final Object bean = beanRegistry.getEntityService(action.getEntity().getClass());
        if (bean == null) {
            throw new EntityServiceException("No entity service can be found");
        }
        if (!(bean instanceof DocumentEntityService<?>)) {
            throw new EntityServiceException("Bean is not a document entity service");
        }

        final DocumentEntityService<DocumentEntity> entityService = (DocumentEntityService<DocumentEntity>) bean;

        DocumentEntity result;

        final DocumentEntity entity = action.getEntity();

        try {
            // Validate the entity name.
            NameValidationUtil.validate(entityService, action.getName());

            // We have to load the folder here as it currently comes from the parent folder of the entity when doing
            // 'Save As' in which case the folder UUID will not have been populated.
            // TODO : Remove this when the new explorer service is in place.
            Folder folder = null;
            if (action.getFolder() != null) {
                folder = folderService.loadByIdInsecure(action.getFolder().getId(), Collections.emptySet());
            }

            result = entityService.copy(entity, DocRefUtil.create(folder), action.getName(), action.getPermissionInheritance());
            entityEventLog.create(result);
        } catch (final RuntimeException e) {
            entityEventLog.create(entity, e);
            throw e;
        }

        return result;
    }
}
