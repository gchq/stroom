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

package stroom.query.server;

import org.springframework.context.annotation.Scope;
import stroom.entity.server.FindService;
import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.StringCriteria;
import stroom.entity.shared.StringCriteria.MatchStyle;
import stroom.feed.server.FeedService;
import stroom.node.server.NodeService;
import stroom.pipeline.server.PipelineService;
import stroom.query.shared.FetchSuggestionsAction;
import stroom.streamstore.server.StreamTypeService;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamStatus;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedList;
import stroom.util.shared.SharedString;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@TaskHandlerBean(task = FetchSuggestionsAction.class)
@Scope(StroomScope.TASK)
class FetchSuggestionsHandler extends AbstractTaskHandler<FetchSuggestionsAction, SharedList<SharedString>> {
    private final FeedService feedService;
    private final PipelineService pipelineService;
    private final StreamTypeService streamTypeService;
    private final NodeService nodeService;

    @Inject
    FetchSuggestionsHandler(@Named("cachedFeedService") final FeedService feedService,
                            @Named("cachedPipelineService") final PipelineService pipelineService,
                            @Named("cachedStreamTypeService") final StreamTypeService streamTypeService,
                            @Named("cachedNodeService") final NodeService nodeService) {
        this.feedService = feedService;
        this.pipelineService = pipelineService;
        this.streamTypeService = streamTypeService;
        this.nodeService = nodeService;
    }

    @Override
    public SharedList<SharedString> exec(final FetchSuggestionsAction task) {
        if (task.getDataSource() != null) {
            if (StreamDataSource.STREAM_STORE_DOC_REF.equals(task.getDataSource())) {
                if (task.getField().getName().equals(StreamDataSource.FEED_NAME)) {
                    return createList(feedService, task.getText());
                }

                if (task.getField().getName().equals(StreamDataSource.PIPELINE_UUID)) {
                    return createList(pipelineService, task.getText());
                }

                if (task.getField().getName().equals(StreamDataSource.STREAM_TYPE_NAME)) {
                    return createList(streamTypeService, task.getText());
                }

                if (task.getField().getName().equals(StreamDataSource.STATUS)) {
                    return new SharedList<>(Arrays.stream(StreamStatus.values())
                            .map(StreamStatus::getDisplayValue)
                            .map(SharedString::wrap)
                            .sorted()
                            .collect(Collectors.toList()));
                }

                if (task.getField().getName().equals(StreamDataSource.NODE)) {
                    return createList(nodeService, task.getText());
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
