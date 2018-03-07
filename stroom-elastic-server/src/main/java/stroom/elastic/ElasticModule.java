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

package stroom.elastic;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.connectors.elastic.StroomElasticProducerFactoryService;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.Element;
import stroom.pipeline.state.PipelineHolder;
import stroom.properties.StroomPropertyService;
import stroom.security.SecurityContext;
import stroom.util.cache.CacheManager;
import stroom.util.spring.StroomScope;

public class ElasticModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ElasticIndexCache.class).to(ElasticIndexCacheImpl.class);

        final Multibinder<Element> elementBinder = Multibinder.newSetBinder(binder(), Element.class);
        elementBinder.addBinding().to(stroom.elastic.ElasticIndexingFilter.class);
    }
}