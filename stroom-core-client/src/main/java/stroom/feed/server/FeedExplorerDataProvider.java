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

package stroom.feed.server;

import stroom.entity.shared.FolderService;
import stroom.explorer.server.AbstractExplorerDataProvider;
import stroom.explorer.server.ProvidesExplorerData;
import stroom.explorer.server.TreeModel;
import stroom.explorer.shared.ExplorerData;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.feed.shared.FindFeedCriteria;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;

@ProvidesExplorerData
@Component
public class FeedExplorerDataProvider extends AbstractExplorerDataProvider<Feed, FindFeedCriteria> {
    private final FeedService feedService;

    @Inject
    FeedExplorerDataProvider(@Named("cachedFolderService") final FolderService cachedFolderService, final FeedService feedService) {
        super(cachedFolderService);
        this.feedService = feedService;
    }

    @Override
    public void addItems(final TreeModel treeModel) {
        addItems(feedService, treeModel);
    }

    @Override
    public String getType() {
        return Feed.ENTITY_TYPE;
    }

    @Override
    public String getDisplayType() {
        return Feed.ENTITY_TYPE;
    }

    @Override
    public int getPriority() {
        return 3;
    }
}
