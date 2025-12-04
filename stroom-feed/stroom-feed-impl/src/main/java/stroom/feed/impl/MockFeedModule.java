/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.feed.impl;

import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.mock.MockFsVolumeGroupService;
import stroom.feed.api.FeedProperties;
import stroom.feed.api.FeedStore;

import com.google.inject.AbstractModule;

public class MockFeedModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(FeedStore.class).to(FeedStoreImpl.class);
        bind(FeedProperties.class).to(FeedPropertiesImpl.class);

        // Only needed for feed import so not an issue for Cli
        bind(FsVolumeGroupService.class).to(MockFsVolumeGroupService.class);
    }
}
