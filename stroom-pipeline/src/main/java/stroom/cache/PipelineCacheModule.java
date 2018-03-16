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

package stroom.cache;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import stroom.entity.event.EntityEvent;
import stroom.entity.event.EntityEvent.Handler;
import stroom.entity.shared.Clearable;

public class PipelineCacheModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(SchemaPool.class).to(SchemaPoolImpl.class);
        bind(SchemaLoader.class).to(SchemaLoaderImpl.class);
        bind(ParserFactoryPool.class).to(ParserFactoryPoolImpl.class);
        bind(XSLTPool.class).to(XSLTPoolImpl.class);

        final Multibinder<Clearable> clearableBinder = Multibinder.newSetBinder(binder(), Clearable.class);
        clearableBinder.addBinding().to(SchemaPoolImpl.class);
        clearableBinder.addBinding().to(ParserFactoryPoolImpl.class);
        clearableBinder.addBinding().to(XSLTPoolImpl.class);

        final Multibinder<Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(SchemaPoolImpl.class);
    }
}