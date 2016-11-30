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

package stroom.pool.server;

import org.springframework.context.annotation.Scope;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.pool.shared.FetchPoolNodeRowAction;
import stroom.pool.shared.PoolNodeRow;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@TaskHandlerBean(task = FetchPoolNodeRowAction.class)
@Scope(StroomScope.TASK)
public class FetchPoolNodeRowHandler extends AbstractTaskHandler<FetchPoolNodeRowAction, ResultList<PoolNodeRow>> {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(FetchPoolNodeRowHandler.class);

    @Resource
    private ClusterDispatchAsyncHelper dispatchHelper;

    @Override
    public ResultList<PoolNodeRow> exec(final FetchPoolNodeRowAction action) {
        final List<PoolNodeRow> values = new ArrayList<PoolNodeRow>();

        // final FindPoolInfoCriteria criteria = new FindPoolInfoCriteria();
        // criteria.setName(new StringCriteria(action.getPoolName(), null));
        // final FindServiceClusterTask<FindPoolInfoCriteria, PoolInfo> task =
        // new FindServiceClusterTask<FindPoolInfoCriteria, PoolInfo>(
        // action.getSessionId(), action.getUserId(), "Find pool info",
        // PoolManager.class, criteria);
        // final DefaultClusterResultCollector<ResultList<PoolInfo>> collector =
        // dispatchHelper.execAsync(task,
        // TargetType.ACTIVE);
        //
        // /** Sort the list of node names. */
        // final List<Node> nodes = new
        // ArrayList<Node>(collector.getTargetNodes());
        // Collections.sort(nodes, new Comparator<Node>() {
        // @Override
        // public int compare(final Node o1, final Node o2) {
        // if (o1.getName() == null || o2.getName() == null) {
        // return 0;
        // }
        // return o1.getName().compareToIgnoreCase(o2.getName());
        // }
        // });
        //
        // for (final Node node : nodes) {
        // final ResultList<PoolInfo> result = collector.getResult(node);
        // if (result != null) {
        // for (final PoolInfo value : result) {
        // values.add(new PoolNodeRow(node, value));
        // }
        // }
        // }

        return BaseResultList.createUnboundedList(values);
    }
}
