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
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.node.shared.Node;
import stroom.security.Security;
import stroom.security.util.UserTokenUtil;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;
import stroom.task.cluster.ClusterCallEntry;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.DefaultClusterResultCollector;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Map.Entry;

@TaskHandlerBean(task = SessionListAction.class)
class SessionListHandler extends AbstractTaskHandler<SessionListAction, ResultList<SessionDetails>> {
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final Security security;

    @Inject
    SessionListHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                       final Security security) {
        this.dispatchHelper = dispatchHelper;
        this.security = security;
    }

    @Override
    public ResultList<SessionDetails> exec(final SessionListAction action) {
        return security.insecureResult(() -> {
            final DefaultClusterResultCollector<ResultList<SessionDetails>> collector = dispatchHelper
                    .execAsync(
                            new FindServiceClusterTask<>(UserTokenUtil.INTERNAL_PROCESSING_USER_TOKEN, "Get session list", SessionListService.class, null),
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
        });
    }
}
