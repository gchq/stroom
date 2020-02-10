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

package stroom.security.impl.session;

import stroom.cluster.task.api.ClusterCallEntry;
import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.cluster.task.api.DefaultClusterResultCollector;
import stroom.cluster.task.api.TargetType;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.ResultList;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Map.Entry;


class SessionListHandler extends AbstractTaskHandler<SessionListTask, ResultList<SessionDetails>> {
    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final SecurityContext securityContext;

    @Inject
    SessionListHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                       final SecurityContext securityContext) {
        this.dispatchHelper = dispatchHelper;
        this.securityContext = securityContext;
    }

    @Override
    public ResultList<SessionDetails> exec(final SessionListTask action) {
        return securityContext.asProcessingUserResult(() -> {
            final DefaultClusterResultCollector<ResultList<SessionDetails>> collector = dispatchHelper
                    .execAsync(
                            new SessionListClusterTask("Get session list"),
                            TargetType.ACTIVE);

            final ArrayList<SessionDetails> rtnList = new ArrayList<>();

            for (final Entry<String, ClusterCallEntry<ResultList<SessionDetails>>> call : collector.getResponseMap()
                    .entrySet()) {
                if (call.getValue().getResult() != null) {
                    for (final SessionDetails sessionDetails : call.getValue().getResult()) {
                        sessionDetails.setNodeName(call.getKey());
                        rtnList.add(sessionDetails);
                    }
                }
            }
            return ResultList.createUnboundedList(rtnList);
        });
    }
}
