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

import java.util.ArrayList;
import java.util.List;

import stroom.util.spring.StroomScope;
import org.springframework.context.annotation.Scope;

import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.ResultList;
import stroom.pool.shared.FetchPoolRowAction;
import stroom.pool.shared.PoolRow;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;

@TaskHandlerBean(task = FetchPoolRowAction.class)
@Scope(StroomScope.TASK)
public class FetchPoolRowHandler extends AbstractTaskHandler<FetchPoolRowAction, ResultList<PoolRow>> {
    // @Resource
    // private PoolManager poolMamager;

    @Override
    public ResultList<PoolRow> exec(final FetchPoolRowAction action) {
        final List<PoolRow> values = new ArrayList<PoolRow>();

        // final BaseResultList<PoolInfo> list = poolMamager.find(new
        // FindPoolInfoCriteria());
        // for (final PoolInfo value : list) {
        // values.add(new PoolRow(value.getName()));
        // }
        //
        // // Sort the pool names.
        // Collections.sort(values, new Comparator<PoolRow>() {
        // @Override
        // public int compare(final PoolRow o1, final PoolRow o2) {
        // return o1.getPoolName().compareTo(o2.getPoolName());
        // }
        // });

        return BaseResultList.createUnboundedList(values);
    }
}
