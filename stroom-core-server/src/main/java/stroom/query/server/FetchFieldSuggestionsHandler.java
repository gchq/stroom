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
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.StringCriteria;
import stroom.entity.shared.StringCriteria.MatchStyle;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.feed.shared.FindFeedCriteria;
import stroom.query.shared.FetchFieldSuggestionsAction;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedList;
import stroom.util.shared.SharedString;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;

@TaskHandlerBean(task = FetchFieldSuggestionsAction.class)
@Scope(StroomScope.TASK)
class FetchFieldSuggestionsHandler extends AbstractTaskHandler<FetchFieldSuggestionsAction, SharedList<SharedString>> {
    private final FeedService feedService;

    @Inject
    public FetchFieldSuggestionsHandler(final FeedService feedService) {
        this.feedService = feedService;
    }

    @Override
    public SharedList<SharedString> exec(final FetchFieldSuggestionsAction task) {
        final SharedList<SharedString> result = new SharedList<>();

        if (task.getDataSource() != null) {
            if ("STREAM_STORE".equals(task.getDataSource().getUuid())) {
                if (task.getField().getFieldName().equals("Feed")) {
                    final FindFeedCriteria criteria = new FindFeedCriteria();
                    criteria.setName(new StringCriteria(task.getText(), MatchStyle.WildEnd));
                    final List<Feed> feeds = feedService.find(criteria);
                    feeds.stream().sorted(Comparator.comparing(NamedEntity::getName)).forEachOrdered(f -> result.add(SharedString.wrap(f.getName())));
                }
            }
        }

        return result;
    }
}
