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

package stroom.servlet;

import stroom.entity.cluster.FindServiceClusterTask;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.node.shared.Node;
import stroom.security.Insecure;
import stroom.security.UserTokenUtil;
import stroom.task.cluster.ClusterCallEntry;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.DefaultClusterResultCollector;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Map.Entry;

@TaskHandlerBean(task = SessionListAction.class)
@Insecure
class SessionListHandler extends AbstractTaskHandler<SessionListAction, ResultList<SessionDetails>> {
    private final ClusterDispatchAsyncHelper dispatchHelper;

    @Inject
    SessionListHandler(final ClusterDispatchAsyncHelper dispatchHelper) {
        this.dispatchHelper = dispatchHelper;
    }

    @Override
    public ResultList<SessionDetails> exec(final SessionListAction action) {
        final DefaultClusterResultCollector<ResultList<SessionDetails>> collector = dispatchHelper
                .execAsync(
                        new FindServiceClusterTask<BaseCriteria, SessionDetails>(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN, "Get session list", SessionListService.class, null),
                        TargetType.ACTIVE);

        final ArrayList<SessionDetails> rtnList = new ArrayList<>();

        for (final Entry<Node, ClusterCallEntry<ResultList<SessionDetails>>> call : collector.getResponseMap()
                .entrySet()) {
            if (call.getValue().getResult() != null) {
                for (final SessionDetails sessionDetails : call.getValue().getResult()) {
                    sessionDetails.setNodeName(call.getKey().getName());
                    rtnList.add(sessionDetails);
                }
            }
        }
        return BaseResultList.createUnboundedList(rtnList);
    }
}
