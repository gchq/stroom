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

package stroom.feed.server;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.entity.server.MockDocumentEntityService;
import stroom.entity.shared.BaseResultList;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FindFeedCriteria;
import stroom.importexport.server.ImportExportHelper;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;

/**
 * <p>
 * Very simple mock that keeps everything in memory.
 * </p>
 * <p>
 * <p>
 * You can call clear at any point to clear everything down.
 * </p>
 */
@Profile(StroomSpringProfiles.TEST)
@Component("feedService")
public class MockFeedService extends MockDocumentEntityService<Feed, FindFeedCriteria> implements FeedService {
    public MockFeedService() {
    }

    @Inject
    public MockFeedService(final ImportExportHelper importExportHelper) {
        super(importExportHelper);
    }

    @Override
    public Class<Feed> getEntityClass() {
        return Feed.class;
    }

    @Override
    public Feed loadByName(final String name) {
        BaseResultList<Feed> list = find(createCriteria());
        for (final Feed feed : list) {
            if (feed.getName().equals(name)) {
                return feed;
            }
        }

        return null;
    }

    @Override
    public String getDisplayClassification(final Feed feed) {
        return null;
    }
}
