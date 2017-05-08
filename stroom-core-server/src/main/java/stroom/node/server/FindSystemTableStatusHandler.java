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

package stroom.node.server;

import org.springframework.context.annotation.Scope;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.node.shared.DBTableService;
import stroom.node.shared.DBTableStatus;
import stroom.node.shared.FindSystemTableStatusAction;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = FindSystemTableStatusAction.class)
@Scope(value = StroomScope.TASK)
public class FindSystemTableStatusHandler
        extends AbstractTaskHandler<FindSystemTableStatusAction, ResultList<DBTableStatus>> {
    private final DBTableService dbTableService;

    @Inject
    FindSystemTableStatusHandler(final DBTableService dbTableService) {
        this.dbTableService = dbTableService;
    }

    @Override
    public BaseResultList<DBTableStatus> exec(final FindSystemTableStatusAction action) {
        return BaseResultList.createUnboundedList(dbTableService.findSystemTableStatus(action.getOrderBy(), action.getOrderByDirection()));
    }
}
