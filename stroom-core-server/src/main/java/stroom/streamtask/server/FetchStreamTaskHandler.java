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
import stroom.entity.shared.Sort.Direction;
import stroom.feed.shared.Feed;
import stroom.node.shared.Node;
import stroom.pipeline.shared.PipelineEntity;
import stroom.security.Secured;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.shared.FetchStreamTaskAction;
import stroom.streamtask.shared.FindStreamTaskCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamTask;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;

@TaskHandlerBean(task = FetchStreamTaskAction.class)
@Scope(StroomScope.TASK)
@Secured(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)
public class FetchStreamTaskHandler extends AbstractTaskHandler<FetchStreamTaskAction, ResultList<StreamTask>> {
    @Resource
    private StreamTaskService streamTaskService;
    @Resource
    private ExpressionToFindStreamTaskCriteria expressionToFindStreamTaskCriteria;

    @Override
    public ResultList<StreamTask> exec(final FetchStreamTaskAction action) {
        final FindStreamTaskCriteria criteria = expressionToFindStreamTaskCriteria.convert(action.getCriteria());
        criteria.setSort(FindStreamTaskCriteria.FIELD_CREATE_TIME, Direction.DESCENDING, false);
        criteria.getFetchSet().add(Stream.ENTITY_TYPE);
        criteria.getFetchSet().add(StreamType.ENTITY_TYPE);
        criteria.getFetchSet().add(Feed.ENTITY_TYPE);
        criteria.getFetchSet().add(StreamProcessor.ENTITY_TYPE);
        criteria.getFetchSet().add(PipelineEntity.ENTITY_TYPE);
        criteria.getFetchSet().add(Node.ENTITY_TYPE);
        criteria.obtainStreamTaskStatusSet().setMatchAll(Boolean.FALSE);
        // Only show unlocked stuff
        criteria.obtainStatusSet().add(StreamStatus.UNLOCKED);
        return streamTaskService.find(criteria);
    }
}
