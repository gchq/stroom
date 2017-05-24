/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.server;

import org.springframework.context.annotation.Scope;
import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.shared.FindService;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.StringCriteria;
import stroom.entity.shared.StringCriteria.MatchStyle;
import stroom.feed.shared.FeedService;
import stroom.node.shared.NodeService;
import stroom.pipeline.shared.PipelineEntityService;
import stroom.query.shared.FetchSuggestionsAction;
import stroom.streamstore.shared.StreamTypeService;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedList;
import stroom.util.shared.SharedString;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;

@TaskHandlerBean(task = FetchSuggestionsAction.class)
@Scope(StroomScope.TASK)
class FetchSuggestionsHandler extends AbstractTaskHandler<FetchSuggestionsAction, SharedList<SharedString>> {
    private final FeedService feedService;
    private final NodeService nodeService;
    private final PipelineEntityService pipelineEntityService;
    private final StreamTypeService streamTypeService;

    @Inject
    public FetchSuggestionsHandler(final FeedService feedService, final NodeService nodeService, final PipelineEntityService pipelineEntityService, final StreamTypeService streamTypeService) {
        this.feedService = feedService;
        this.nodeService = nodeService;
        this.pipelineEntityService = pipelineEntityService;
        this.streamTypeService = streamTypeService;
    }

    @Override
    public SharedList<SharedString> exec(final FetchSuggestionsAction task) {
        if (task.getDataSource() != null) {
            if ("STREAM_STORE".equals(task.getDataSource().getUuid())) {
                if (task.getField().getFieldName().equals("Feed")) {
                    return createList(feedService, task.getText());
                }

                if (task.getField().getFieldName().equals("Node")) {
                    return createList(nodeService, task.getText());
                }

                if (task.getField().getFieldName().equals("Pipeline")) {
                    return createList(pipelineEntityService, task.getText());
                }

                if (task.getField().getFieldName().equals("StreamType")) {
                    return createList(streamTypeService, task.getText());
                }
            }
        }

        return new SharedList<>();
    }

    @SuppressWarnings("unchecked")
    private SharedList<SharedString> createList(final FindService service, final String text) {
        final SharedList<SharedString> result = new SharedList<>();
        final FindNamedEntityCriteria criteria = (FindNamedEntityCriteria) service.createCriteria();
        criteria.setName(new StringCriteria(text, MatchStyle.WildEnd));
        final List<Object> list = service.find(criteria);
        list
                .stream()
                .sorted(Comparator.comparing(e -> ((NamedEntity) e).getName()))
                .forEachOrdered(e -> result.add(SharedString.wrap(((NamedEntity) e).getName())));
        return result;
    }
}
