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

package stroom.pipeline.cache;

import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasSystemInfoBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;

public class PipelineCacheModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(SchemaPool.class).to(SchemaPoolImpl.class);
        bind(SchemaLoader.class).to(SchemaLoaderImpl.class);
        bind(ParserFactoryPool.class).to(ParserFactoryPoolImpl.class);
        bind(XsltPool.class).to(XsltPoolImpl.class);
        bind(DocumentPermissionCache.class).to(DocumentPermissionCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(SchemaPoolImpl.class)
                .addBinding(ParserFactoryPoolImpl.class)
                .addBinding(XsltPoolImpl.class)
                .addBinding(DocumentPermissionCacheImpl.class);

        HasSystemInfoBinder.create(binder())
                .bind(SchemaPoolImpl.class)
                .bind(ParserFactoryPoolImpl.class)
                .bind(XsltPoolImpl.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(ParserFactoryPoolImpl.class)
                .addBinding(SchemaPoolImpl.class)
                .addBinding(XsltPoolImpl.class);
    }
}
