/*
 * Copyright 2018 Crown Copyright
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

package stroom.feed;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import stroom.entity.FindService;

public class MockFeedModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(FeedService.class).to(MockFeedService.class);
        bind(FeedService.class).annotatedWith(Names.named("cachedFeedService")).to(MockFeedService.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(MockFeedService.class);
    }
//
//    @Provides
//    @Singleton
//    public FeedService feedService(final ImportExportHelper importExportHelper) {
//        return new MockFeedService(importExportHelper);
//    }
//
//    @Provides
//    @Singleton
//    @Named("cachedFeedService")
//    public FeedService cachedFeedService(final FeedService feedService) {
//        return feedService;
//    }
}