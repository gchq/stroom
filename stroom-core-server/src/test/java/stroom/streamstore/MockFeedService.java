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

package stroom.streamstore;

import stroom.entity.MockNamedEntityService;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.streamstore.shared.Feed;

import javax.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * Very simple mock that keeps everything in memory.
 * </p>
 * <p>
 * <p>
 * You can call clear at any point to clear everything down.
 * </p>
 */
@Singleton
class MockFeedService extends MockNamedEntityService<Feed, FindFeedCriteria>
        implements FeedService {
    public MockFeedService() {
    }

    /**
     * @return the stream type by it's ID or null
     */
    @SuppressWarnings("unchecked")
    @Override
    public Feed get(final String name) {
        final List<Feed> list = map.values()
                .stream()
                .filter(st -> st.getName().equals(name))
                .collect(Collectors.toList());

        if (list.size() == 0) {
            return null;
        }
        return list.get(0);
    }

    @Override
    public Feed getOrCreate(final String name) {
        Feed feed = get(name);
        if (feed == null) {
            feed = create(name);
        }
        return feed;
    }

    @Override
    public long getId(final String name) {
        return getOrCreate(name).getId();
    }

    @Override
    public EntityIdSet<Feed> convertNameSet(final CriteriaSet<String> feeds) {
        if (feeds == null) {
            return null;
        }

        final EntityIdSet<Feed> entityIdSet = new EntityIdSet<>();
        entityIdSet.setMatchAll(feeds.getMatchAll());
        entityIdSet.setMatchNull(feeds.getMatchNull());
        feeds.forEach(feedName -> entityIdSet.add(getId(feedName)));

        return entityIdSet;
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
}
