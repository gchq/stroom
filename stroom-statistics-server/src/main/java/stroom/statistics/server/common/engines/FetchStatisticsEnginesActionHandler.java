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

package stroom.statistics.server.common.engines;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import stroom.util.spring.StroomScope;
import org.springframework.context.annotation.Scope;

import stroom.statistics.common.StatisticsFactory;
import stroom.statistics.shared.common.engines.FetchStatisticsEnginesAction;
import stroom.statistics.shared.common.engines.FetchStatisticsEnginesResults;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;

@TaskHandlerBean(task = FetchStatisticsEnginesAction.class)
@Scope(value = StroomScope.TASK)
public class FetchStatisticsEnginesActionHandler
        extends AbstractTaskHandler<FetchStatisticsEnginesAction, FetchStatisticsEnginesResults> {
    @Resource
    private StatisticsFactory statisticEventStoreFactory;

    @Override
    public FetchStatisticsEnginesResults exec(final FetchStatisticsEnginesAction action) {
        final Set<String> engines = statisticEventStoreFactory.getAllEngineNames();
        final List<String> sortedEngines = new ArrayList<String>(engines);
        Collections.sort(sortedEngines);

        return new FetchStatisticsEnginesResults(sortedEngines);
    }
}
