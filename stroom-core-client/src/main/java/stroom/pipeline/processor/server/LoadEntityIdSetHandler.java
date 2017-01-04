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

package stroom.pipeline.processor.server;

import stroom.entity.server.GenericEntityService;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.EntityIdSet;
import stroom.pipeline.processor.shared.LoadEntityIdSetAction;
import stroom.pipeline.processor.shared.SetId;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedList;
import stroom.util.shared.SharedMap;

import javax.annotation.Resource;
import java.util.Map.Entry;

@TaskHandlerBean(task = LoadEntityIdSetAction.class)
public class LoadEntityIdSetHandler
        extends AbstractTaskHandler<LoadEntityIdSetAction, SharedMap<SetId, SharedList<DocRef>>> {
    @Resource
    private GenericEntityService genericEntityService;

    @Override
    public SharedMap<SetId, SharedList<DocRef>> exec(final LoadEntityIdSetAction action) {
        final SharedMap<SetId, SharedList<DocRef>> result = new SharedMap<SetId, SharedList<DocRef>>();

        final SharedMap<SetId, EntityIdSet<?>> map = action.getEntitySetMap();
        if (map != null) {
            for (final Entry<SetId, EntityIdSet<?>> entry : map.entrySet()) {
                final SetId setId = entry.getKey();
                final EntityIdSet<?> entityIdSet = entry.getValue();

                final SharedList<DocRef> list = createList(setId.getEntityType(), entityIdSet);
                if (list != null) {
                    result.put(setId, list);
                }
            }
        }

        return result;
    }

    private SharedList<DocRef> createList(final String entityType, final EntityIdSet<?> entityIdSet) {
        SharedList<DocRef> entityList = null;
        if (entityType != null && entityIdSet != null && entityIdSet.getSet() != null && entityIdSet.size() > 0) {
            if (entityList == null) {
                entityList = new SharedList<DocRef>(entityIdSet.size());
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
