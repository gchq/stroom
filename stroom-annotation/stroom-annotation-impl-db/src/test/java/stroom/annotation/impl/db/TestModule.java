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

package stroom.annotation.impl.db;

import stroom.cache.impl.CacheModule;
import stroom.collection.mock.MockCollectionModule;
import stroom.dictionary.mock.MockWordListProviderModule;
import stroom.docrefinfo.mock.MockDocRefInfoModule;
import stroom.meta.api.StreamFeedProvider;
import stroom.security.mock.MockSecurityContextModule;
import stroom.security.user.api.UserRefLookup;
import stroom.task.mock.MockTaskModule;
import stroom.test.common.MockMetrics;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.metrics.Metrics;
import stroom.util.shared.UserRef;

import com.google.inject.AbstractModule;

import java.util.Optional;

public class TestModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();
        install(new AnnotationDaoModule());
        install(new AnnotationDbModule());
        install(new MockCollectionModule());
        install(new MockDocRefInfoModule());
        install(new MockSecurityContextModule());
        install(new MockWordListProviderModule());
        install(new DbTestModule());
        install(new MockTaskModule());
        install(new CacheModule());

        bind(UserRefLookup.class).toInstance((userUuid, context) -> Optional.of(UserRef.forUserUuid(userUuid)));
        bind(StreamFeedProvider.class).toInstance(id -> "TEST_FEED_NAME");
        bind(Metrics.class).toInstance(new MockMetrics());
    }
//
//
//    @Provides
//    CollectionService collectionService() {
//        return new CollectionService() {
//            @Override
//            public Set<DocRef> getChildren(final DocRef folder, final String type) {
//                return null;
//            }
//
//            @Override
//            public Set<DocRef> getDescendants(final DocRef folder, final String type) {
//                return null;
//            }
//        };
//    }
//
//    @Provides
//    WordListProvider wordListProvider() {
//        return new WordListProvider() {
//
//            @Override
//            public List<DocRef> findByName(final String name) {
//                return List.of();
//            }
//
//            @Override
//            public Optional<DocRef> findByUuid(final String uuid) {
//                return Optional.empty();
//            }
//
//            @Override
//            public String getCombinedData(final DocRef dictionaryRef) {
//                return null;
//            }
//
//            @Override
//            public String[] getWords(final DocRef dictionaryRef) {
//                return null;
//            }
//
//            @Override
//            public WordList getCombinedWordList(final DocRef dictionaryRef,
//                                                final DocRefDecorator docRefDecorator) {
//                return null;
//            }
//        };
//    }
}
