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

package stroom.streamtask.server;

import org.springframework.context.annotation.Scope;
import stroom.entity.shared.ResultList;
import stroom.pipeline.shared.PipelineEntity;
import stroom.security.Secured;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamtask.shared.FetchStreamTaskSummaryAction;
import stroom.streamtask.shared.FindStreamTaskCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamTaskSummary;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;

@TaskHandlerBean(task = FetchStreamTaskSummaryAction.class)
@Scope(StroomScope.TASK)
@Secured(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)
public class FetchStreamTaskSummaryHandler extends AbstractTaskHandler<FetchStreamTaskSummaryAction, ResultList<StreamTaskSummary>> {
    @Resource
    private StreamTaskService streamTaskService;
    @Resource
    private ExpressionToFindStreamTaskCriteria expressionToFindStreamTaskCriteria;

    @Override
    public ResultList<StreamTaskSummary> exec(final FetchStreamTaskSummaryAction action) {
        final FindStreamTaskCriteria criteria = expressionToFindStreamTaskCriteria.convert(action.getExpression());
        criteria.obtainStatusSet().setSingleItem(StreamStatus.UNLOCKED);
        criteria.getFetchSet().add(StreamProcessor.ENTITY_TYPE);
        criteria.getFetchSet().add(PipelineEntity.ENTITY_TYPE);

        return streamTaskService.findSummary(criteria);
    }
}
