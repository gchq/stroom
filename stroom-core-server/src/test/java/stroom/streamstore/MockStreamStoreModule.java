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

package stroom.streamstore;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.shared.Clearable;
import stroom.streamstore.api.StreamStore;
import stroom.streamstore.fs.StreamVolumeService;
import stroom.streamstore.fs.StreamVolumeServiceImpl;
import stroom.streamstore.meta.StreamMetaService;
import stroom.streamstore.meta.db.StreamEntityService;
import stroom.streamstore.meta.db.StreamEntityServiceImpl;

public class MockStreamStoreModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StreamStore.class).to(MockStreamStore.class);
        bind(StreamAttributeKeyService.class).to(MockStreamAttributeKeyService.class);
        bind(FeedEntityService.class).to(MockFeedService.class);
        bind(StreamTypeEntityService.class).to(MockStreamTypeService.class);
        bind(StreamMetaService.class).to(MockStreamMetaService.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(MockStreamStore.class);
        clearableBinder.addBinding().to(MockStreamTypeService.class);
        clearableBinder.addBinding().to(MockFeedService.class);
        clearableBinder.addBinding().to(MockStreamMetaService.class);
        clearableBinder.addBinding().to(MockStreamAttributeKeyService.class);
    }
    //    @Bean
//    public ExpressionToFindCriteria expressionToFindCriteria(@Named("cachedFeedService") final FeedService feedService,
//                                                             @Named("cachedPipelineStore") final PipelineStore pipelineStore,
//                                                             final DictionaryStore dictionaryStore,
//                                                             final StreamAttributeKeyService streamAttributeKeyService) {
//        return new ExpressionToFindCriteria(feedService, pipelineStore, dictionaryStore, streamAttributeKeyService);
//    }
//
//    @Bean
//    public StreamAttributeKeyService streamAttributeKeyService() {
//        return new MockStreamAttributeKeyService();
//    }
//
//    @Bean
//    public StreamStore streamStore() {
//        return new MockStreamStore();
//    }
//
//    @Bean
//    public StreamTypeService streamTypeService() {
//        return new MockStreamTypeService();
//    }
//
//    @Bean("cachedStreamTypeService")
//    public StreamTypeService cachedStreamTypeService(final StreamTypeService streamTypeService) {
//        return streamTypeService;
//    }
}