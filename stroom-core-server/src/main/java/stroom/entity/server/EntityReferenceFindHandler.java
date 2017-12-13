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

package stroom.entity.server;

import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;
import org.springframework.context.annotation.Scope;
import stroom.logging.DocumentEventLog;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.EntityReferenceFindAction;
import stroom.entity.shared.ResultList;
import stroom.entity.shared.SharedDocRef;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@TaskHandlerBean(task = EntityReferenceFindAction.class)
@Scope(value = StroomScope.TASK)
class EntityReferenceFindHandler
        extends AbstractTaskHandler<EntityReferenceFindAction<BaseCriteria>, ResultList<SharedDocRef>> {
    private final EntityServiceBeanRegistry beanRegistry;
    private final DocumentEventLog documentEventLog;

    @Inject
    EntityReferenceFindHandler(final EntityServiceBeanRegistry beanRegistry, final DocumentEventLog documentEventLog) {
        this.beanRegistry = beanRegistry;
        this.documentEventLog = documentEventLog;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ResultList<SharedDocRef> exec(final EntityReferenceFindAction<BaseCriteria> action) {
        BaseResultList<BaseEntity> resultList = null;

        final And and = new And();
        final Advanced advanced = new Advanced();
        advanced.getAdvancedQueryItems().add(and);
        final Query query = new Query();
        query.setAdvanced(advanced);

        try {
            final Object entityService = beanRegistry.getEntityService(action.getCriteria().getClass());
            if (entityService != null && entityService instanceof SupportsCriteriaLogging) {
                final SupportsCriteriaLogging<BaseCriteria> logging = (SupportsCriteriaLogging<BaseCriteria>) entityService;
                logging.appendCriteria(and.getAdvancedQueryItems(), action.getCriteria());
            }
        } catch (final Exception e) {
            // Ignore.
        }

        try {
            resultList = (BaseResultList<BaseEntity>) beanRegistry.invoke("find", action.getCriteria());
            documentEventLog.search(action.getCriteria(), query, resultList);
        } catch (final RuntimeException e) {
            documentEventLog.search(action.getCriteria(), query, e);

            throw e;
        }

        ResultList<SharedDocRef> docRefs = null;
        if (resultList != null && resultList.size() > 0) {
            final List<SharedDocRef> list = new ArrayList<>(resultList.size());
            for (final BaseEntity baseEntity : resultList) {
                list.add(SharedDocRef.create(DocRefUtil.create(baseEntity)));
            }
            docRefs = new BaseResultList<>(list, Long.valueOf(resultList.getStart()),
                    Long.valueOf(resultList.getSize()), (resultList.getStart() + list.size() < resultList.getSize()));
        }

        return docRefs;
    }
}
