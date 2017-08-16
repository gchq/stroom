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
 */

package stroom.process.server;

import org.springframework.context.annotation.Scope;
import stroom.entity.server.GenericEntityService;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.EntityIdSet;
import stroom.process.shared.LoadEntityIdSetAction;
import stroom.process.shared.SetId;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedMap;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.Map.Entry;

@TaskHandlerBean(task = LoadEntityIdSetAction.class)
@Scope(value = StroomScope.TASK)
public class LoadEntityIdSetHandler
        extends AbstractTaskHandler<LoadEntityIdSetAction, SharedMap<SetId, DocRefs>> {
    private final GenericEntityService genericEntityService;

    @Inject
    LoadEntityIdSetHandler(final GenericEntityService genericEntityService) {
        this.genericEntityService = genericEntityService;
    }

    @Override
    public SharedMap<SetId, DocRefs> exec(final LoadEntityIdSetAction action) {
        final SharedMap<SetId, DocRefs> result = new SharedMap<>();

        final SharedMap<SetId, EntityIdSet<?>> map = action.getEntitySetMap();
        if (map != null) {
            for (final Entry<SetId, EntityIdSet<?>> entry : map.entrySet()) {
                final SetId setId = entry.getKey();
                final EntityIdSet<?> entityIdSet = entry.getValue();

                final DocRefs list = createList(setId.getEntityType(), entityIdSet);
                if (list != null) {
                    result.put(setId, list);
                }
            }
        }

        return result;
    }

    private DocRefs createList(final String entityType, final EntityIdSet<?> entityIdSet) {
        DocRefs entityList = null;
        if (entityType != null && entityIdSet != null && entityIdSet.getSet() != null && entityIdSet.size() > 0) {
            if (entityList == null) {
                entityList = new DocRefs();
            }

            for (final Long id : entityIdSet.getSet()) {
                if (id != null) {
                    final BaseEntity entity = genericEntityService.loadById(entityType.toString(), id);
                    if (entity != null) {
                        entityList.add(DocRefUtil.create(entity));
                    }
                }
            }
        }

        return entityList;
    }
}
