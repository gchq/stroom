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

package stroom.streamstore.meta.db;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.FindService;
import stroom.entity.shared.Clearable;

public class StreamMetaDBModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(StreamAttributeKeyService.class).to(StreamAttributeKeyServiceImpl.class);
        bind(StreamAttributeMapService.class).to(StreamAttributeMapServiceImpl.class);
        bind(StreamAttributeValueFlush.class).to(StreamAttributeValueFlushImpl.class);
        bind(StreamAttributeValueService.class).to(StreamAttributeValueServiceImpl.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(StreamAttributeKeyServiceImpl.class);
        clearableBinder.addBinding().to(StreamAttributeValueFlushImpl.class);

        final Multibinder<FindService> findServiceBinder = Multibinder.newSetBinder(binder(), FindService.class);
        findServiceBinder.addBinding().to(StreamAttributeMapServiceImpl.class);
    }
}